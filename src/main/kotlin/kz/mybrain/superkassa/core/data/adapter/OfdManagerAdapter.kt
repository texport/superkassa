package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.data.ofd.OfdCodecService
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.data.ofd.OfdRegistry
import kz.mybrain.superkassa.core.domain.port.OfdManagerPort
import kz.mybrain.network.OfdEndpoint as NetworkEndpoint
import kz.mybrain.network.OfdNetworkClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Адаптер ОФД-менеджера с сериализацией через ofd-proto-codec.
 * Использует стратегии для построения запросов (OCP).
 * По протоколу ОФД п. 5: при отсутствии связи повторная попытка — не чаще интервала
 * восстановления связи (настраиваемый, не менее 60 с).
 */
class OfdManagerAdapter(
    private val config: OfdConfig,
    private val codec: OfdCodecService,
    private val networkClient: OfdNetworkClient,
    private val requestBuilders: List<OfdRequestBuilderStrategy> = defaultRequestBuilders(),
    /** Общее время на обработку транзакции (протокол п. 5), не менее 5 с. */
    private val timeoutSeconds: Long = 30L,
    /** Интервал задержки между попытками восстановления связи (протокол п. 5), не менее 60 с. */
    private val reconnectIntervalSeconds: Long = 60L
) : OfdManagerPort {
    private val logger = LoggerFactory.getLogger(OfdManagerAdapter::class.java)

    private val reconnectIntervalMs: Long = reconnectIntervalSeconds.coerceAtLeast(60L) * 1000L

    /** Время последней неудачи по связи (нет ответа ОФД) по kkmId. */
    private val lastNoConnectionMillis = ConcurrentHashMap<String, Long>()

    companion object {
        /**
         * Дефолтный набор стратегий построения запросов.
         * Можно расширить, добавив новые стратегии без изменения существующего кода.
         */
        private fun defaultRequestBuilders(): List<OfdRequestBuilderStrategy> {
            return listOf(
                ServiceRequestBuilderStrategy(),
                TicketRequestBuilderStrategy() // Должна быть последней как fallback
            )
        }
    }

    override fun send(command: OfdCommandRequest): OfdCommandResult {
        val now = System.currentTimeMillis()
        val lastFail = lastNoConnectionMillis[command.kkmId]
        if (lastFail != null && (now - lastFail) < reconnectIntervalMs) {
            logger.debug("OFD throttle: kkmId={}, retry after {}s", command.kkmId, reconnectIntervalSeconds)
            return OfdCommandResult(
                status = OfdCommandStatus.TIMEOUT,
                errorMessage = ErrorMessages.ofdRequestFailed("No connection; retry not earlier than ${reconnectIntervalSeconds}s"),
                resultCode = null
            )
        }
        return try {
            val endpoint = resolveEndpoint(command)
                ?: return OfdCommandResult(
                    status = OfdCommandStatus.FAILED,
                    errorMessage = ErrorMessages.ofdRequestFailed("Invalid OFD configuration: provider or endpoint not found")
                )
            val json = buildRequest(command)
                ?: return OfdCommandResult(
                    status = OfdCommandStatus.FAILED,
                    errorMessage = ErrorMessages.ofdRequestFailed("Missing required request parameters")
                )
            val bytes = codec.encode(json)
            val response = runBlocking {
                try {
                    withTimeout(timeoutSeconds * 1000L) {
                        networkClient.sendAndReceive(endpoint, bytes)
                    }
                } catch (e: TimeoutCancellationException) {
                    logger.warn("OFD request timeout after {} seconds", timeoutSeconds)
                    return@runBlocking Result.failure<ByteArray>(
                        java.util.concurrent.TimeoutException("OFD request timeout after ${timeoutSeconds}s")
                    )
                }
            }
            if (response.isFailure) {
                val error = response.exceptionOrNull()?.message ?: "unknown"
                val isTimeout = error.contains("timeout", ignoreCase = true)
                lastNoConnectionMillis[command.kkmId] = now
                return OfdCommandResult(
                    status = if (isTimeout) OfdCommandStatus.TIMEOUT else OfdCommandStatus.FAILED,
                    errorMessage = ErrorMessages.ofdRequestFailed(error)
                )
            }

            val responseBytes = response.getOrNull()
                ?: run {
                    lastNoConnectionMillis[command.kkmId] = now
                    return OfdCommandResult(
                        status = OfdCommandStatus.FAILED,
                        errorMessage = ErrorMessages.ofdRequestFailed("empty response")
                    )
                }
            val responseJson = codec.decode(responseBytes)
            val resultCode = extractResultCode(responseJson)
            val resultText = extractResultText(responseJson)
            if (resultCode != null) lastNoConnectionMillis.remove(command.kkmId)
            val status = if (resultCode == 0) OfdCommandStatus.OK else OfdCommandStatus.FAILED
            OfdCommandResult(
                status = status,
                responseBin = responseBytes,
                responseJson = responseJson,
                responseToken = extractHeaderToken(responseJson),
                responseReqNum = extractHeaderReqNum(responseJson),
                resultCode = resultCode,
                resultText = resultText,
                errorMessage = if (status == OfdCommandStatus.OK) null else resultText
            )
        } catch (ex: Exception) {
            logger.error("OFD request failed", ex)
            lastNoConnectionMillis[command.kkmId] = now
            OfdCommandResult(
                status = OfdCommandStatus.FAILED,
                errorMessage = ErrorMessages.ofdRequestFailed(ex.message)
            )
        }
    }

    private fun resolveEndpoint(command: OfdCommandRequest): NetworkEndpoint? {
        val provider = OfdRegistry.findProvider(command.ofdProviderId) ?: return null
        val environment = OfdRegistry.findEnvironment(command.ofdEnvironmentId) ?: return null
        val endpoint = OfdRegistry.findEndpoint(provider, environment) ?: return null
        return NetworkEndpoint(endpoint.host, endpoint.port)
    }

    private fun buildRequest(command: OfdCommandRequest): JsonObject? {
        // Находим первую стратегию, которая может обработать данный тип команды
        val builder = requestBuilders.firstOrNull { it.canHandle(command.commandType) }
            ?: return null
        return builder.build(command, config)
    }

    private fun extractHeaderToken(response: JsonObject): Long? {
        val header = response["header"] as? JsonObject ?: return null
        return header["token"]?.jsonPrimitive?.longOrNull
    }

    private fun extractHeaderReqNum(response: JsonObject): Int? {
        val header = response["header"] as? JsonObject ?: return null
        return header["reqNum"]?.jsonPrimitive?.intOrNull
    }

    private fun extractResultCode(response: JsonObject): Int? {
        val payload = response["payload"]?.jsonObject ?: return null
        val result = payload["result"]?.jsonObject ?: return null
        return result["resultCode"]?.jsonPrimitive?.intOrNull
    }

    private fun extractResultText(response: JsonObject): String? {
        val payload = response["payload"]?.jsonObject ?: return null
        val result = payload["result"]?.jsonObject ?: return null
        return result["resultText"]?.jsonPrimitive?.contentOrNull
    }
}
