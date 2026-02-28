package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/** Запрос на операцию с наличными (внесение/изъятие). */
@Serializable
data class CashOperationRequest(
        val pin: String = "", // Будет заполнен из заголовка Authorization
        val amount: Double,
        val idempotencyKey: String
) {
    fun copy(pin: String): CashOperationRequest {
        return CashOperationRequest(
            pin = pin,
            amount = this.amount,
            idempotencyKey = this.idempotencyKey
        )
    }
}
