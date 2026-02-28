package kz.mybrain.superkassa.core.application.model.receipt

import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.domain.model.Money
import kz.mybrain.superkassa.core.domain.model.ParentTicket
import kz.mybrain.superkassa.core.domain.model.PaymentType
import kz.mybrain.superkassa.core.domain.model.ReceiptItem
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.model.ReceiptPayment
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.UnitOfMeasurement
import kz.mybrain.superkassa.core.domain.model.VatGroup

/**
 * Маппер для преобразования HTTP DTO в domain модели для чеков.
 * Код секции (sectionCode) заполняется на стороне сервера по умолчанию "001".
 */
object ReceiptMapper {

    private const val DEFAULT_SECTION_CODE = "001"

    /**
     * Сумма позиции вычисляется: price × quantity − скидка + наценка (по позиции).
     */
    fun toReceiptItem(dto: ReceiptItemDto): ReceiptItem {
        val baseSumTenge = dto.price * dto.quantity
        val itemDiscountTenge = when {
            dto.discountPercent != null -> baseSumTenge * dto.discountPercent / 100.0
            dto.discountSum != null -> dto.discountSum
            else -> 0.0
        }
        val itemMarkupTenge = when {
            dto.markupPercent != null -> baseSumTenge * dto.markupPercent / 100.0
            dto.markupSum != null -> dto.markupSum
            else -> 0.0
        }
        val itemSumTenge = (baseSumTenge - itemDiscountTenge + itemMarkupTenge).coerceAtLeast(0.0)
        val itemDiscount = if (itemDiscountTenge > 0) Money.fromTenge(itemDiscountTenge) else null
        val itemMarkup = if (itemMarkupTenge > 0) Money.fromTenge(itemMarkupTenge) else null
        val measureUnitCode = dto.measureUnitCode?.takeIf { it.isNotBlank() }?.let { raw ->
            try {
                UnitOfMeasurement.fromCode(raw).code
            } catch (e: IllegalArgumentException) {
                throw ValidationException(ErrorMessages.measureUnitCodeInvalid(raw), "MEASURE_UNIT_CODE_INVALID")
            }
        }
        return ReceiptItem(
            name = dto.name,
            sectionCode = DEFAULT_SECTION_CODE,
            quantity = dto.quantity,
            price = Money.fromTenge(dto.price),
            sum = Money.fromTenge(itemSumTenge),
            barcode = dto.barcode?.takeIf { it.isNotBlank() },
            vatGroup = dto.vatGroup?.let { value ->
                try {
                    VatGroup.valueOf(value)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Invalid vatGroup: $value. Valid: " +
                            VatGroup.values().joinToString { it.name }
                    )
                }
            },
            discount = itemDiscount,
            markup = itemMarkup,
            measureUnitCode = measureUnitCode,
            listExciseStamp = dto.listExciseStamp?.takeIf { it.isNotEmpty() },
            ntin = dto.ntin?.takeIf { it.isNotBlank() }
        )
    }

    fun toReceiptPayment(dto: ReceiptPaymentDto): ReceiptPayment {
        val paymentType = try {
            PaymentType.valueOf(dto.type)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid payment type: ${dto.type}. Valid: ${PaymentType.values().joinToString { it.name }}")
        }
        return ReceiptPayment(type = paymentType, sum = Money.fromTenge(dto.sum))
    }

    private fun toParentTicket(dto: ParentTicketDto?): ParentTicket? {
        if (dto == null) return null
        val instant = java.time.Instant.parse(dto.parentTicketDateTime)
        val millis = instant.toEpochMilli()
        return ParentTicket(
            parentTicketNumber = dto.parentTicketNumber,
            parentTicketDateTimeMillis = millis,
            kgdKkmId = dto.kgdKkmId,
            parentTicketTotal = Money.fromTenge(dto.parentTicketTotal),
            parentTicketIsOffline = dto.parentTicketIsOffline
        )
    }

    /**
     * Общая сумма чека вычисляется на сервере: сумма по всем позициям (уже с учётом скидок/наценок по позиции)
     * минус скидка на чек плюс наценка на чек.
     */
    fun toReceiptRequest(
        kkmId: String,
        pin: String,
        operation: ReceiptOperationType,
        idempotencyKey: String,
        items: List<ReceiptItemDto>,
        discountPercent: Double?,
        discountSum: Double?,
        markupPercent: Double?,
        markupSum: Double?,
        payments: List<ReceiptPaymentDto>,
        taken: Double?,
        @Suppress("UNUSED_PARAMETER") change: Double?, // не передаём в ОФД; сдача считается по taken и total
        parentTicket: ParentTicketDto? = null,
        defaultVatGroup: String? = null
    ): ReceiptRequest {
        val receiptItems = items.map { toReceiptItem(it) }
        val itemsTotalTenge = receiptItems.sumOf { it.sum.bills + it.sum.coins / 100.0 }
        val receiptDiscountTenge = when {
            discountPercent != null -> itemsTotalTenge * discountPercent / 100.0
            discountSum != null -> discountSum
            else -> 0.0
        }
        val receiptMarkupTenge = when {
            markupPercent != null -> itemsTotalTenge * markupPercent / 100.0
            markupSum != null -> markupSum
            else -> 0.0
        }
        val totalTenge = (itemsTotalTenge - receiptDiscountTenge + receiptMarkupTenge).coerceAtLeast(0.0)
        val totalMoney = Money.fromTenge(totalTenge)
        val receiptPayments = payments.map { toReceiptPayment(it) }
        val cashSumTenge = receiptPayments
            .filter { it.type == PaymentType.CASH }
            .sumOf { it.sum.bills + it.sum.coins / 100.0 }
        val (takenMoney, changeMoney) = when {
            taken == null -> {
                Pair(Money.fromTenge(cashSumTenge), null)
            }
            else -> {
                require(taken >= cashSumTenge) {
                    "taken must be >= sum of CASH payments (cashSum=$cashSumTenge, taken=$taken)"
                }
                val changeTenge = (taken - totalTenge).coerceAtLeast(0.0)
                Pair(Money.fromTenge(taken), Money.fromTenge(changeTenge))
            }
        }
        val receiptDiscount = if (receiptDiscountTenge > 0) Money.fromTenge(receiptDiscountTenge) else null
        val receiptMarkup = if (receiptMarkupTenge > 0) Money.fromTenge(receiptMarkupTenge) else null
        val vatGroup = defaultVatGroup?.takeIf { it.isNotBlank() }?.let { value ->
            try {
                VatGroup.valueOf(value)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid defaultVatGroup: $value. Valid: " + VatGroup.values().joinToString { it.name }
                )
            }
        }
        return ReceiptRequest(
            kkmId = kkmId,
            pin = pin,
            operation = operation,
            items = receiptItems,
            payments = receiptPayments,
            total = totalMoney,
            taken = takenMoney,
            change = changeMoney,
            idempotencyKey = idempotencyKey,
            parentTicket = toParentTicket(parentTicket),
            defaultVatGroup = vatGroup,
            discount = receiptDiscount,
            markup = receiptMarkup
        )
    }
}
