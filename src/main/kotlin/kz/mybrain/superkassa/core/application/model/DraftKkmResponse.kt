package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable

/**
 * Ответ на создание черновика ККМ.
 */
@Serializable
data class DraftKkmResponse(
    val kkmId: String,
    val factoryNumber: String,
    val manufactureYear: Int
)
