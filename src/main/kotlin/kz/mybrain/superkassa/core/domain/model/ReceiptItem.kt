package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/** Позиция чека. sectionCode заполняется на стороне сервера (по умолчанию "001"). */
@Serializable
data class ReceiptItem(
        val name: String,
        val sectionCode: String,
        val quantity: Long,
        val price: Money,
        val sum: Money,
        val barcode: String? = null,
        /** Группа НДС для позиции. Если null, используется defaultVatGroup кассы. */
        val vatGroup: VatGroup? = null,
        /** Скидка на позицию. */
        val discount: Money? = null,
        /** Наценка на позицию. */
        val markup: Money? = null,
        /** Код единицы измерения (протокол ОФД measure_unit_code). Если null — штука (796). */
        val measureUnitCode: String? = null,
        /** Список акцизных марок (протокол ОФД list_excise_stamp). */
        val listExciseStamp: List<String>? = null,
        /** НТИН (протокол ОФД ntin). */
        val ntin: String? = null
)
