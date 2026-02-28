package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Деньги в формате bills/coins как в протоколе ОФД.
 */
@Serializable
data class Money(
    val bills: Long,
    val coins: Int
) {
    companion object {
        /**
         * Преобразует сумму в виде Double (тенге) в формат Money (bills/coins).
         * bills - целая часть (тенге), coins - дробная часть (тиыны, умноженные на 100).
         * 
         * @param amount Сумма в тенге (например, 1234.56)
         * @return Money с bills=1234 и coins=56
         */
        fun fromTenge(amount: Double): Money {
            val bills = amount.toLong()
            val coins = ((amount - bills) * 100).toInt()
            return Money(bills = bills, coins = coins)
        }
    }
}
