package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Описание исходного чека для операций возврата (parentTicket в ticket.proto).
 */
@Serializable
data class ParentTicket(
    val parentTicketNumber: Long,
    val parentTicketDateTimeMillis: Long,
    val kgdKkmId: String,
    val parentTicketTotal: Money,
    val parentTicketIsOffline: Boolean
)

