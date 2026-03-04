package kz.mybrain.superkassa.core.config

import kz.mybrain.network.OfdTcpNetworkClient
import kz.mybrain.superkassa.core.application.model.CoreMode
import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.application.model.StorageSettings
import kz.mybrain.superkassa.core.application.policy.DefaultCounterUpdater
import kz.mybrain.superkassa.core.application.policy.SystemClock
import kz.mybrain.superkassa.core.application.policy.UuidGenerator
import kz.mybrain.superkassa.core.application.service.AuthorizationService
import kz.mybrain.superkassa.core.application.service.AutonomousModeService
import kz.mybrain.superkassa.core.application.service.FileCoreSettingsRepository
import kz.mybrain.superkassa.core.application.service.FiscalOperationExecutor
import kz.mybrain.superkassa.core.application.service.KkmRegistrationService
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.application.service.KkmUserService
import kz.mybrain.superkassa.core.application.service.OfdCommandRequestBuilder
import kz.mybrain.superkassa.core.application.service.OfdSyncService
import kz.mybrain.superkassa.core.application.service.QueueManagementService
import kz.mybrain.superkassa.core.application.service.ReqNumService
import kz.mybrain.superkassa.core.application.service.ShiftCountersRecalculator
import kz.mybrain.superkassa.core.application.service.ShiftService
import kz.mybrain.superkassa.core.data.adapter.DeliveryPortAdapter
import kz.mybrain.superkassa.core.data.adapter.CloseShiftRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.MoneyPlacementRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.OfdConfigPortAdapter
import kz.mybrain.superkassa.core.data.adapter.ReportRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.OfdManagerAdapter
import kz.mybrain.superkassa.core.data.adapter.ServiceRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.TicketRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.ResilienceOfdManagerPortAdapter
import kz.mybrain.superkassa.core.data.adapter.OfflineQueuePortAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageBackedQueueStoragePort
import kz.mybrain.superkassa.core.data.adapter.Sha256PinHasherPort
import kz.mybrain.superkassa.core.data.adapter.StoragePortAdapter
import kz.mybrain.superkassa.core.data.ofd.OfdCodecService
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.domain.port.*
import kz.mybrain.superkassa.delivery.application.service.DeliveryService
import kz.mybrain.superkassa.delivery.domain.model.DeliveryChannel
import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import kz.mybrain.superkassa.delivery.domain.model.DeliveryResult
import kz.mybrain.superkassa.delivery.domain.port.DeliveryAdapter
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort
import kz.mybrain.superkassa.storage.data.jdbc.HikariConfigFactory
import com.zaxxer.hikari.HikariDataSource
import kz.mybrain.superkassa.storage.application.health.StorageHealthChecker
import kz.mybrain.superkassa.storage.data.bootstrap.DefaultStorageBootstrap
import kz.mybrain.superkassa.storage.data.jdbc.DefaultStorageConnectorRegistry
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import java.nio.file.Paths

@Configuration
class AppConfig {

    private val logger = LoggerFactory.getLogger(AppConfig::class.java)

    @Bean
    fun settingsRepository(): FileCoreSettingsRepository {
        return FileCoreSettingsRepository(Paths.get("config/core-settings.json"))
    }

    @Bean
    fun coreSettings(repository: FileCoreSettingsRepository): CoreSettings {
        val defaults = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(
                engine = "SQLITE",
                jdbcUrl = "jdbc:sqlite:build/core.db"
            ),
            allowChanges = true
        )
        // Load or create defaults without interactive prompt
        return repository.loadOrCreate(defaults)
    }

    @Bean
    fun storageConfig(settings: CoreSettings): StorageConfig {
        return StorageConfig(
            settings.storage.jdbcUrl,
            null,
            settings.storage.user,
            settings.storage.password
        )
    }

    @Bean
    fun storageHealthChecker(): StorageHealthChecker {
        return StorageHealthChecker(DefaultStorageConnectorRegistry())
    }

    @Bean
    fun storagePort(config: StorageConfig): StoragePort {
        val storageBootstrap = DefaultStorageBootstrap()
        logger.info("Connecting to storage: ${config.jdbcUrl}")
        storageBootstrap.migrate(config)
        return StoragePortAdapter(storageBootstrap, config)
    }

    @Bean
    fun queueStoragePort(storagePort: StoragePort): QueueStoragePort {
        return StorageBackedQueueStoragePort(storagePort)
    }

    @Bean
    fun queueLeaseLockPort(storagePort: StoragePort): LeaseLockPort {
        return kz.mybrain.superkassa.core.data.adapter.StorageBackedLeaseLockPort(storagePort)
    }

    @Bean
    fun queueCommandHandler(
        ofdSyncService: OfdSyncService,
        storagePort: StoragePort
    ): QueueCommandHandler =
        kz.mybrain.superkassa.core.application.service.OfdQueueCommandHandler(
            ofdSyncService = ofdSyncService,
            storage = storagePort,
            clock = SystemClock
        )

    @Bean
    fun queuePort(
        settings: CoreSettings,
        queueStoragePort: QueueStoragePort,
        queueLeaseLockPort: LeaseLockPort,
        queueCommandHandler: QueueCommandHandler
    ): OfflineQueuePort {
        val ownerId = settings.nodeId
        return OfflineQueuePortAdapter(queueStoragePort, queueLeaseLockPort, queueCommandHandler, ownerId = ownerId)
    }

    @Bean
    fun deliveryPort(settings: CoreSettings): DeliveryPort {
        val adapters = mutableListOf<DeliveryAdapter>()
        val delivery = settings.delivery
        val channelsToUse = if (delivery != null && delivery.channels.isNotEmpty()) {
            delivery.channels.filter { it.enabled }.map { it.channel.uppercase() }
        } else {
            settings.deliveryChannels.map { it.uppercase() }
        }
        channelsToUse.distinct().forEach { channelName ->
            try {
                val channel = DeliveryChannel.valueOf(channelName)
                val adapter = when (channel) {
                    DeliveryChannel.PRINT -> delivery?.print?.connection?.let { conn ->
                        conn.host?.let { h -> conn.port?.let { p ->
                            kz.mybrain.superkassa.delivery.data.adapter.PrintDeliveryAdapter(h, p)
                        }}
                    } ?: stubAdapter(DeliveryChannel.PRINT)
                    DeliveryChannel.EMAIL -> delivery?.email?.let { e ->
                        kz.mybrain.superkassa.delivery.data.adapter.EmailDeliveryAdapter(
                            e.host, e.port, e.user, e.password, e.from
                        )
                    } ?: stubAdapter(DeliveryChannel.EMAIL)
                    DeliveryChannel.SMS -> delivery?.sms?.takeIf { it.providerUrl != null }?.let { s ->
                        kz.mybrain.superkassa.delivery.data.adapter.SmsDeliveryAdapter(s.providerUrl, s.apiKey)
                    } ?: stubAdapter(DeliveryChannel.SMS)
                    DeliveryChannel.TELEGRAM -> delivery?.telegram?.takeIf { it.botToken != null }?.let { t ->
                        kz.mybrain.superkassa.delivery.data.adapter.TelegramDeliveryAdapter(t.botToken!!)
                    } ?: stubAdapter(DeliveryChannel.TELEGRAM)
                    DeliveryChannel.WHATSAPP -> delivery?.whatsapp?.takeIf {
                        it.accessToken != null && it.phoneNumberId != null
                    }?.let { w ->
                        kz.mybrain.superkassa.delivery.data.adapter.WhatsAppDeliveryAdapter(
                            w.accessToken!!, w.phoneNumberId!!
                        )
                    } ?: stubAdapter(DeliveryChannel.WHATSAPP)
                }
                adapters.add(adapter)
            } catch (e: IllegalArgumentException) {
                logger.warn("Unknown delivery channel: $channelName")
            }
        }
        if (adapters.isEmpty()) {
            adapters.add(stubAdapter(DeliveryChannel.PRINT))
        }
        return DeliveryPortAdapter(DeliveryService(adapters))
    }

    private fun stubAdapter(channel: DeliveryChannel): DeliveryAdapter = object : DeliveryAdapter {
        override val channel: DeliveryChannel = channel
        override fun send(request: DeliveryRequest): DeliveryResult {
            logger.debug("Delivery stub for channel: {}", channel)
            return DeliveryResult(true)
        }
    }

    @Bean
    fun receiptRenderPort(): ReceiptRenderPort = kz.mybrain.superkassa.core.data.receipt.ReceiptHtmlRenderer()

    @Bean
    fun documentConvertPort(): DocumentConvertPort = kz.mybrain.superkassa.core.data.receipt.DocumentConvertAdapter()

    @Bean
    fun ofdConfigPort(): OfdConfigPort = OfdConfigPortAdapter()

    @Bean
    fun pinHasherPort(): PinHasherPort = Sha256PinHasherPort()

    @Bean
    fun authorizationService(storage: StoragePort, pinHasher: PinHasherPort): AuthorizationService =
        AuthorizationService(storage, pinHasher)

    @Bean
    fun kkmUserService(
        storage: StoragePort,
        pinHasher: PinHasherPort,
        authorization: AuthorizationService
    ): KkmUserService =
        KkmUserService(storage, UuidGenerator, SystemClock, pinHasher, authorization)

    @Bean
    fun ofdManagerPort(settings: CoreSettings, storage: StoragePort): OfdManagerPort {
        val shiftCountersRecalculator = ShiftCountersRecalculator(storage)
        val requestBuilders = listOf(
            ServiceRequestBuilderStrategy(),
            MoneyPlacementRequestBuilderStrategy(storage),
            ReportRequestBuilderStrategy(storage, shiftCountersRecalculator),
            CloseShiftRequestBuilderStrategy(storage, shiftCountersRecalculator),
            TicketRequestBuilderStrategy()
        )
        val delegate = OfdManagerAdapter(
            OfdConfig(protocolVersion = settings.ofdProtocolVersion),
            OfdCodecService(),
            OfdTcpNetworkClient(),
            requestBuilders = requestBuilders,
            timeoutSeconds = settings.ofdTimeoutSeconds.coerceAtLeast(5L),
            reconnectIntervalSeconds = settings.ofdReconnectIntervalSeconds.coerceAtLeast(60L)
        )
        return ResilienceOfdManagerPortAdapter(delegate)
    }

    @Bean
    fun ofdCommandRequestBuilder(ofdConfig: OfdConfigPort): OfdCommandRequestBuilder =
        OfdCommandRequestBuilder(ofdConfig)

    @Bean
    fun tokenCodecPort(): kz.mybrain.superkassa.core.domain.port.TokenCodecPort =
        kz.mybrain.superkassa.core.data.adapter.Base64TokenCodecPort()

    @Bean
    fun autonomousModeService(
        storage: StoragePort,
        @Lazy queue: OfflineQueuePort
    ): AutonomousModeService =
        AutonomousModeService(
            storage = storage,
            queue = queue,
            clock = SystemClock
        )

    @Bean
    fun fiscalOperationExecutor(
        storage: StoragePort,
        authorization: AuthorizationService
    ): FiscalOperationExecutor =
        FiscalOperationExecutor(
            storage = storage,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization
        )

    @Bean
    fun reqNumService(storage: StoragePort): ReqNumService =
        ReqNumService(storage = storage)

    @Bean
    fun ofdSyncService(
        storage: StoragePort,
        @Lazy queue: OfflineQueuePort,
        ofd: OfdManagerPort,
        authorization: AuthorizationService,
        ofdCommandRequestBuilder: OfdCommandRequestBuilder,
        tokenCodec: kz.mybrain.superkassa.core.domain.port.TokenCodecPort,
        autonomousModeService: AutonomousModeService,
        reqNumService: ReqNumService
    ): OfdSyncService =
        OfdSyncService(
            storage = storage,
            queue = queue,
            ofd = ofd,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization,
            ofdCommandRequestBuilder = ofdCommandRequestBuilder,
            tokenCodec = tokenCodec,
            autonomousModeService = autonomousModeService,
            reqNumService = reqNumService
        )

    @Bean
    fun shiftService(
        storage: StoragePort,
        queue: OfflineQueuePort,
        ofdSyncService: OfdSyncService,
        authorization: AuthorizationService
    ): ShiftService =
        ShiftService(
            storage = storage,
            queue = queue,
            ofdSyncService = ofdSyncService,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization
        )

    @Bean
    fun kkmRegistrationService(
        storage: StoragePort,
        ofd: OfdManagerPort,
        ofdConfig: OfdConfigPort,
        tokenCodec: kz.mybrain.superkassa.core.domain.port.TokenCodecPort,
        authorization: AuthorizationService,
        ofdCommandRequestBuilder: OfdCommandRequestBuilder,
        reqNumService: ReqNumService
    ): KkmRegistrationService =
        KkmRegistrationService(
            storage = storage,
            ofd = ofd,
            ofdConfig = ofdConfig,
            tokenCodec = tokenCodec,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            pinHasher = Sha256PinHasherPort(),
            authorization = authorization,
            ofdCommandRequestBuilder = ofdCommandRequestBuilder,
            reqNumService = reqNumService,
            counters = DefaultCounterUpdater(storage)
        )

    @Bean
    fun kkmService(
        storage: StoragePort,
        queue: OfflineQueuePort,
        ofd: OfdManagerPort,
        ofdConfig: OfdConfigPort,
        delivery: DeliveryPort,
        kkmUserService: KkmUserService,
        shiftService: ShiftService,
        ofdSyncService: OfdSyncService,
        kkmRegistrationService: KkmRegistrationService,
        tokenCodec: kz.mybrain.superkassa.core.domain.port.TokenCodecPort,
        autonomousModeService: AutonomousModeService,
        fiscalOperationExecutor: FiscalOperationExecutor,
        reqNumService: ReqNumService,
        pinHasher: PinHasherPort,
        authorization: AuthorizationService,
        ofdCommandRequestBuilder: OfdCommandRequestBuilder,
        coreSettings: CoreSettings,
        receiptRenderPort: ReceiptRenderPort,
        documentConvertPort: DocumentConvertPort
    ): KkmService {
        return KkmService(
            storage = storage,
            queue = queue,
            ofd = ofd,
            ofdConfig = ofdConfig,
            delivery = delivery,
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
    }

    @Bean
    fun queueManagementService(
        storage: StoragePort,
        queuePort: OfflineQueuePort,
        queueStoragePort: QueueStoragePort,
        authorization: AuthorizationService
    ): QueueManagementService =
        QueueManagementService(
            storage = storage,
            queuePort = queuePort,
            queueStorage = queueStoragePort,
            authorization = authorization
        )
}
