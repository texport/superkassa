package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable

/** Результат попытки отправки по одному каналу (ручная повторная отправка чека). */
@Serializable
data class DeliveryRetryItemResponse(
    val channel: String,
    val success: Boolean
)

@Serializable
data class DeliveryRetryResponse(
    val results: List<DeliveryRetryItemResponse>
)
