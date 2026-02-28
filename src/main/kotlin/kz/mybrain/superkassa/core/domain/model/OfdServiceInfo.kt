package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Регистрационные и сервисные данные для запросов COMMAND_SYSTEM/COMMAND_INFO.
 */
@Serializable
data class OfdServiceInfo(
    val orgTitle: String,
    val orgAddress: String,
    val orgAddressKz: String,
    val orgInn: String,
    val orgOkved: String,
    val geoLatitude: Int,
    val geoLongitude: Int,
    val geoSource: String
)
