package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable

/**
 * Конфигурация ядра, фиксируется при инициализации.
 */
@Serializable
data class CoreSettings(
    val mode: CoreMode,
    val storage: StorageSettings,
    val allowChanges: Boolean = false,
    val nodeId: String = "node-1",
    val ofdProtocolVersion: String = "203",
    val deliveryChannels: List<String> = listOf("PRINT"),
    /** Общее время на обработку транзакции (протокол ОФД п. 5), не менее 5 сек. */
    val ofdTimeoutSeconds: Long = 30L,
    /** Интервал задержки между попытками восстановления связи (протокол ОФД п. 5), не менее 60 сек. */
    val ofdReconnectIntervalSeconds: Long = 60L,
    /** Настройки доставки чеков (печать, каналы, форматы). */
    val delivery: DeliverySettings? = null
)

@Serializable
data class StorageSettings(
    val engine: String,
    val jdbcUrl: String,
    val user: String? = null,
    val password: String? = null
)

/** Настройки доставки чеков. */
@Serializable
data class DeliverySettings(
    val print: PrintDeliverySettings? = null,
    val channels: List<DeliveryChannelSettings> = emptyList(),
    val email: EmailProviderSettings? = null,
    val sms: SmsProviderSettings? = null,
    val telegram: TelegramProviderSettings? = null,
    val whatsapp: WhatsAppProviderSettings? = null
)

@Serializable
data class EmailProviderSettings(
    val host: String = "localhost",
    val port: Int = 587,
    val user: String? = null,
    val password: String? = null,
    val from: String = "noreply@local"
)

@Serializable
data class SmsProviderSettings(
    val providerUrl: String? = null,
    val apiKey: String? = null
)

@Serializable
data class TelegramProviderSettings(
    val botToken: String? = null
)

@Serializable
data class WhatsAppProviderSettings(
    val accessToken: String? = null,
    val phoneNumberId: String? = null
)

/** Настройки печати (ESC/POS). */
@Serializable
data class PrintDeliverySettings(
    val enabled: Boolean = true,
    val paperWidthMm: Int = 58,
    val connection: PrintConnectionSettings? = null
)

/** Настройки подключения к принтеру. */
@Serializable
data class PrintConnectionSettings(
    val type: String = "NETWORK",
    val host: String? = null,
    val port: Int? = 9100
)

/** Настройки канала доставки (EMAIL, SMS, WHATSAPP, TELEGRAM). */
@Serializable
data class DeliveryChannelSettings(
    val channel: String,
    val enabled: Boolean = true,
    /** LINK = только ссылка ОФД; DOCUMENT = документ; BOTH = оба. */
    val payloadType: String = "DOCUMENT",
    /** HTML | PDF | IMAGE — формат документа при DOCUMENT/BOTH. */
    val documentFormat: String = "PDF",
    val destination: String? = null
)
