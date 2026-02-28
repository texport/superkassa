package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

/** Ответ API по ККМ с согласованными именами полей. */
@Serializable
@Schema(description = "Информация о ККМ")
data class KkmResponse(
        @Schema(description = "ID ККМ", example = "kkm-123") val kkmId: String,
        @Schema(description = "Метка времени создания (epoch ms)", example = "1700000000000")
        val createdAt: Long,
        @Schema(
                description = "Метка времени последнего обновления (epoch ms)",
                example = "1700000000000"
        )
        val updatedAt: Long,
        @Schema(description = "Режим работы ККМ", example = "REGISTRATION") val mode: String,
        @Schema(description = "Состояние ККМ", example = "ACTIVE") val state: String,
        @Schema(description = "ID провайдера ОФД", example = "kazakhtelecom")
        val ofdId: String? = null,
        @Schema(description = "Среда ОФД", example = "test") val ofdEnvironment: String? = null,
        @Schema(description = "Регистрационный номер ККМ (КГД)", example = "123456789012")
        val kkmKgdId: String? = null,
        @Schema(description = "Заводской номер ККМ", example = "SWK-0001")
        val factoryNumber: String? = null,
        @Schema(description = "Год выпуска", example = "2024") val manufactureYear: Int? = null,
        @Schema(description = "Системный ID в ОФД", example = "sys-123")
        val ofdSystemId: String? = null,
        @Schema(description = "Сервисная информация ОФД")
        val ofdServiceInfo: kz.mybrain.superkassa.core.domain.model.OfdServiceInfo? = null,
        @Schema(
                description = "Зашифрованный токен ОФД (Base64)",
                hidden = true
        ) // Скрываем токен из документации если это чувствительные данные
        val tokenEncryptedBase64: String? = null,
        @Schema(description = "Время обновления токена", example = "1700000000000")
        val tokenUpdatedAt: Long? = null,
        @Schema(description = "Номер последней смены", example = "10") val lastShiftNo: Int? = null,
        @Schema(description = "Номер последнего чека", example = "150")
        val lastReceiptNo: Int? = null,
        @Schema(description = "Номер последнего Z-отчета", example = "10")
        val lastZReportNo: Int? = null,
        @Schema(
                description = "Время начала автономного режима (если активен)",
                example = "1700000000000"
        )
        val autonomousSince: Long? = null,
        @Schema(description = "Автоматическое закрытие смены", example = "false")
        val autoCloseShift: Boolean = false,
        @Schema(description = "Хэш последней фискальной операции", example = "base64hash==")
        val lastFiscalHashBase64: String? = null,
        @Schema(
                description = "Налоговый режим ККМ (NO_VAT, VAT_PAYER, MIXED)",
                example = "NO_VAT"
        )
        val taxRegime: String? = null,
        @Schema(
                description = "Базовая группа НДС по умолчанию (NO_VAT, VAT_0, VAT_16)",
                example = "NO_VAT"
        )
        val defaultVatGroup: String? = null
)
