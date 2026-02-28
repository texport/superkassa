package kz.mybrain.superkassa.delivery.domain.port

import kz.mybrain.superkassa.delivery.domain.model.DeliveryChannel
import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import kz.mybrain.superkassa.delivery.domain.model.DeliveryResult

/**
 * Адаптер доставки по конкретному каналу.
 */
interface DeliveryAdapter {
    val channel: DeliveryChannel
    fun send(request: DeliveryRequest): DeliveryResult
}
