package kz.mybrain.superkassa.core.application.model.receipt

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

/**
 * Данные исходного чека для операций возврата.
 */
@Serializable
@Schema(description = "Информация об исходном чеке для возврата")
data class ParentTicketDto(
    @Schema(description = "Номер исходного чека", example = "123")
    val parentTicketNumber: Long,
    @Schema(
        description = "Дата и время исходного чека в формате ISO-8601 (UTC)",
        example = "2025-02-20T10:15:30Z"
    )
    val parentTicketDateTime: String,
    @Schema(
        description = "Регистрационный номер ККМ (КГД), на которой был пробит исходный чек",
        example = "123456789012"
    )
    val kgdKkmId: String,
    @Schema(description = "Сумма исходного чека (в тенге)", example = "1500.75")
    val parentTicketTotal: Double,
    @Schema(
        description = "Был ли исходный чек пробит в офлайн-режиме",
        example = "false"
    )
    val parentTicketIsOffline: Boolean
)

