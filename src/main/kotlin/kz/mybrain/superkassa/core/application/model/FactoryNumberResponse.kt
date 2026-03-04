package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

/**
 * Ответ с сгенерированным заводским номером и годом выпуска.
 * Используется для регистрации ККМ в ОФД до вызова /kkm/init.
 */
@Serializable
@Schema(description = "Сгенерированный заводской номер и год выпуска ККМ")
data class FactoryNumberResponse(
    @Schema(description = "Заводской номер ККМ", example = "KZT2026000001")
    val factoryNumber: String,

    @Schema(description = "Год выпуска ККМ", example = "2026")
    val manufactureYear: Int
)

