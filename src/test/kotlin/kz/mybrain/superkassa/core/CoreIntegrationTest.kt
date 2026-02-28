package kz.mybrain.superkassa.core

import kz.mybrain.superkassa.core.application.policy.DefaultCounterUpdater
import kz.mybrain.superkassa.core.application.policy.SystemClock
import kz.mybrain.superkassa.core.application.policy.UuidGenerator
import kz.mybrain.superkassa.core.application.model.KkmInitDirectRequest
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.data.adapter.DeliveryPortAdapter
import kz.mybrain.superkassa.core.data.adapter.QueuePortAdapter
import kz.mybrain.superkassa.core.data.adapter.StoragePortAdapter
import kz.mybrain.superkassa.core.domain.model.Money
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo
import kz.mybrain.superkassa.core.domain.model.PaymentType
import kz.mybrain.superkassa.core.domain.model.ReceiptItem
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.model.ReceiptPayment
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.port.OfdManagerPort
import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.delivery.application.service.DeliveryService
import kz.mybrain.superkassa.delivery.domain.model.DeliveryChannel
import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import kz.mybrain.superkassa.delivery.domain.model.DeliveryResult
import kz.mybrain.superkassa.delivery.domain.port.DeliveryAdapter
import kz.mybrain.superkassa.queue.application.model.DispatchResult
import kz.mybrain.superkassa.queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.queue.data.inmemory.InMemoryLeaseLockPort
import kz.mybrain.superkassa.queue.data.inmemory.InMemoryQueueStoragePort
import kz.mybrain.superkassa.queue.domain.model.QueueStatus
import kz.mybrain.superkassa.storage.data.bootstrap.DefaultStorageBootstrap
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull

class CoreIntegrationTest {
    @Test
    fun shouldRunFullFlowWithCodec() {
        val dbFile = Files.createTempFile("core-integration", ".db").toFile()
        val storageConfig = StorageConfig(jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}")
        val storageBootstrap = DefaultStorageBootstrap()
        storageBootstrap.migrate(storageConfig)

        val storage = StoragePortAdapter(storageBootstrap, storageConfig)
        val queueStorage = InMemoryQueueStoragePort()
        val queueLocks = InMemoryLeaseLockPort()
        val queueHandler = QueueCommandHandler { DispatchResult(QueueStatus.SENT) }
        val queuePort = QueuePortAdapter(queueStorage, queueLocks, queueHandler, ownerId = "node-1")

        val deliveryService = DeliveryService(
            listOf(
                object : DeliveryAdapter {
                    override val channel: DeliveryChannel = DeliveryChannel.PRINT
                    override fun send(request: DeliveryRequest): DeliveryResult = DeliveryResult(true)
                }
            )
        )
        val deliveryPort = DeliveryPortAdapter(deliveryService)

        val ofdManager = object : OfdManagerPort {
            override fun send(command: OfdCommandRequest): OfdCommandResult {
                val responseJson = buildJsonObject {
                    put(
                        "header",
                        buildJsonObject {
                            put("token", JsonPrimitive(12345))
                            put("reqNum", JsonPrimitive(command.reqNum))
                        }
                    )
                    put(
                        "payload",
                        buildJsonObject {
                            put(
                                "result",
                                buildJsonObject {
                                    put("resultCode", JsonPrimitive(0))
                                }
                            )
                            if (command.commandType == OfdCommandType.INFO) {
                                put(
                                    "report",
                                    buildJsonObject {
                                        put(
                                            "zxReport",
                                            buildJsonObject {
                                                put("shiftNumber", JsonPrimitive(1))
                                                put(
                                                    "nonNullableSums",
                                                    kotlinx.serialization.json.buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("operation", JsonPrimitive("OPERATION_SELL"))
                                                                put(
                                                                    "sum",
                                                                    buildJsonObject {
                                                                        put("bills", JsonPrimitive(1000))
                                                                        put("coins", JsonPrimitive(0))
                                                                    }
                                                                )
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
                return OfdCommandResult(
                    status = OfdCommandStatus.OK,
                    responseJson = responseJson,
                    responseToken = 12345,
                    responseReqNum = command.reqNum,
                    resultCode = 0
                )
            }
        }

        val ofdConfigPort = kz.mybrain.superkassa.core.data.adapter.OfdConfigPortAdapter()
        val pinHasher = kz.mybrain.superkassa.core.data.adapter.Sha256PinHasherPort()
        val authorization = kz.mybrain.superkassa.core.application.service.AuthorizationService(
            storage = storage,
            pinHasher = pinHasher
        )
        val kkmUserService = kz.mybrain.superkassa.core.application.service.KkmUserService(
            storage = storage,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            pinHasher = pinHasher,
            authorization = authorization
        )
        val tokenCodec = kz.mybrain.superkassa.core.data.adapter.Base64TokenCodecPort()
        val autonomousModeService = kz.mybrain.superkassa.core.application.service.AutonomousModeService(
            storage = storage,
            queue = queuePort,
            clock = SystemClock
        )
        val fiscalOperationExecutor = kz.mybrain.superkassa.core.application.service.FiscalOperationExecutor(
            storage = storage,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization
        )
        val reqNumService = kz.mybrain.superkassa.core.application.service.ReqNumService(
            storage = storage
        )
        val shiftService = kz.mybrain.superkassa.core.application.service.ShiftService(
            storage = storage,
            queue = queuePort,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization
        )
        val ofdCommandRequestBuilder = kz.mybrain.superkassa.core.application.service.OfdCommandRequestBuilder(ofdConfigPort)
        val ofdSyncService = kz.mybrain.superkassa.core.application.service.OfdSyncService(
            storage = storage,
            queue = queuePort,
            ofd = ofdManager,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization,
            ofdCommandRequestBuilder = ofdCommandRequestBuilder,
            tokenCodec = tokenCodec,
            autonomousModeService = autonomousModeService,
            reqNumService = reqNumService
        )
        val kkmRegistrationService = kz.mybrain.superkassa.core.application.service.KkmRegistrationService(
            storage = storage,
            ofd = ofdManager,
            ofdConfig = ofdConfigPort,
            tokenCodec = tokenCodec,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            pinHasher = pinHasher,
            authorization = authorization,
            ofdCommandRequestBuilder = ofdCommandRequestBuilder,
            reqNumService = reqNumService,
            counters = DefaultCounterUpdater(storage)
        )
        val coreSettings = kz.mybrain.superkassa.core.application.model.CoreSettings(
            mode = kz.mybrain.superkassa.core.application.model.CoreMode.DESKTOP,
            storage = kz.mybrain.superkassa.core.application.model.StorageSettings(
                engine = "SQLITE",
                jdbcUrl = "jdbc:sqlite:build/test-core.db"
            ),
            allowChanges = true
        )
        val receiptRenderPort = kz.mybrain.superkassa.core.data.receipt.ReceiptHtmlRenderer()
        val documentConvertPort = kz.mybrain.superkassa.core.data.receipt.DocumentConvertAdapter()
        val service = KkmService(
            storage = storage,
            queue = queuePort,
            ofd = ofdManager,
            ofdConfig = ofdConfigPort,
            delivery = deliveryPort,
            kkmUserService = kkmUserService,
            shiftService = shiftService,
            ofdSyncService = ofdSyncService,
            kkmRegistrationService = kkmRegistrationService,
            tokenCodec = tokenCodec,
            autonomousModeService = autonomousModeService,
            fiscalOperationExecutor = fiscalOperationExecutor,
            reqNumService = reqNumService,
            counters = DefaultCounterUpdater(storage),
            idGenerator = UuidGenerator,
            clock = SystemClock,
            pinHasher = pinHasher,
            authorization = authorization,
            ofdCommandRequestBuilder = ofdCommandRequestBuilder,
            coreSettings = coreSettings,
            receiptRenderPort = receiptRenderPort,
            documentConvertPort = documentConvertPort
        )

        val init = service.initKkm(
            "0000",
            KkmInitDirectRequest(
                ofdId = "KAZAKHTELECOM",
                ofdEnvironment = "TEST",
                ofdSystemId = "12345",
                ofdToken = "12345",
                kkmKgdId = "RN-1",
                factoryNumber = "FN-1",
                manufactureYear = 2024,
                serviceInfo = OfdServiceInfo(
                    orgTitle = "Test Org",
                    orgAddress = "Test Address",
                    orgAddressKz = "Test Address KZ",
                    orgInn = "123456789012",
                    orgOkved = "47301",
                    geoLatitude = 1,
                    geoLongitude = 1,
                    geoSource = "TEST"
                )
            )
        )
        val kkmId = init.id
        val shift = service.openShift(kkmId, "0000")
        assertNotNull(shift)

        val receipt = ReceiptRequest(
            kkmId = kkmId,
            pin = "1111",
            operation = ReceiptOperationType.SELL,
            items = listOf(ReceiptItem("Item", "1", 1, Money(1000, 0), Money(1000, 0))),
            payments = listOf(ReceiptPayment(PaymentType.CASH, Money(1000, 0))),
            total = Money(1000, 0),
            idempotencyKey = "idem-1"
        )
        val result = service.createReceipt(receipt)
        assertNotNull(result.documentId)

        val report = service.closeShift(kkmId, "1111")
        assertNotNull(report.documentId)
    }
}
