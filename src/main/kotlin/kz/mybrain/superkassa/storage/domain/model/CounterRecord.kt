package kz.mybrain.superkassa.storage.domain.model

/**
 * Счетчик кассы: либо глобальный, либо внутри конкретной смены.
 * key соответствует счетчику из протокола/домена.
 */
data class CounterRecord(
    // Идентификатор кассы.
    val cashboxId: String,
    // Область счетчика: GLOBAL или SHIFT.
    val scope: String,
    // Смена, если счетчик относится к смене.
    val shiftId: String? = null,
    // Ключ счетчика (например operation.OPERATION_SELL.sum).
    val key: String,
    // Значение счетчика.
    val value: Long,
    // Время обновления (epoch millis).
    val updatedAt: Long
)
