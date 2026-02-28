package kz.mybrain.superkassa.core.application.policy

import kz.mybrain.superkassa.core.domain.model.PaymentType
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.port.CounterUpdaterPort
import kz.mybrain.superkassa.core.domain.port.StoragePort

/**
 * Базовое обновление счетчиков для X/Z отчетов.
 */
class DefaultCounterUpdater(
    private val storage: StoragePort
) : CounterUpdaterPort {
    override fun updateForReceipt(kkmId: String, shiftId: String, request: ReceiptRequest, isOffline: Boolean) {
        val operationKey = operationKey(request.operation)
        val sumValue = request.total.bills

        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.OPERATION_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.OPERATION_SUM.format(operationKey), sumValue)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_TOTAL_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_SUM.format(operationKey), sumValue)

        if (isOffline) {
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_OFFLINE_COUNT.format(operationKey), 1)
        }

        request.payments.forEach { payment ->
            val payKey = paymentKey(payment.type)
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.PAYMENT_SUM.format(operationKey, payKey), payment.sum.bills)
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.PAYMENT_COUNT.format(operationKey, payKey), 1)
        }

        // глобальные счетчики
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.OPERATION_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.OPERATION_SUM.format(operationKey), sumValue)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_TOTAL_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_SUM.format(operationKey), sumValue)
        if (isOffline) {
            increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_OFFLINE_COUNT.format(operationKey), 1)
        }
        request.payments.forEach { payment ->
            val payKey = paymentKey(payment.type)
            increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.PAYMENT_SUM.format(operationKey, payKey), payment.sum.bills)
            increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.PAYMENT_COUNT.format(operationKey, payKey), 1)
        }
    }

    private fun increment(kkmId: String, scope: String, shiftId: String?, key: String, delta: Long) {
        val current = storage.loadCounters(kkmId, scope, shiftId)[key] ?: 0L
        storage.upsertCounter(kkmId, scope, shiftId, key, current + delta)
    }

    private fun operationKey(operation: ReceiptOperationType): String {
        return when (operation) {
            ReceiptOperationType.SELL -> "OPERATION_SELL"
            ReceiptOperationType.SELL_RETURN -> "OPERATION_SELL_RETURN"
            ReceiptOperationType.BUY -> "OPERATION_BUY"
            ReceiptOperationType.BUY_RETURN -> "OPERATION_BUY_RETURN"
        }
    }

    private fun paymentKey(payment: PaymentType): String {
        return when (payment) {
            PaymentType.CASH -> "PAYMENT_CASH"
            PaymentType.CARD -> "PAYMENT_CARD"
            PaymentType.ELECTRONIC -> "PAYMENT_ELECTRONIC"
        }
    }
}
