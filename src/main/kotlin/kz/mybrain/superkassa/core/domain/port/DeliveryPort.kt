package kz.mybrain.superkassa.core.domain.port

import kz.mybrain.superkassa.core.domain.model.DeliveryRequest

/**
 * Порт доставки чека/отчета.
 */
interface DeliveryPort {
    fun deliver(request: DeliveryRequest): Boolean
}
