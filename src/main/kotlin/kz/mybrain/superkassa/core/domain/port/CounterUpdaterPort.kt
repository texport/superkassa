package kz.mybrain.superkassa.core.domain.port

import kz.mybrain.superkassa.core.domain.model.ReceiptRequest

/**
 * Обновление счетчиков по операциям.
 */
interface CounterUpdaterPort {
    fun updateForReceipt(kkmId: String, shiftId: String, request: ReceiptRequest, isOffline: Boolean)
}
