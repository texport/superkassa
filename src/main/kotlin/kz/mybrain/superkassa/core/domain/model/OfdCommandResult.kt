package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Результат отправки в ОФД.
 */
@Serializable
data class OfdCommandResult(
    val status: OfdCommandStatus,
    val responseBin: ByteArray? = null,
    val responseJson: JsonObject? = null,
    val responseToken: Long? = null,
    val responseReqNum: Int? = null,
    val resultCode: Int? = null,
    val resultText: String? = null,
    val fiscalSign: String? = null,
    val autonomousSign: String? = null,
    val errorMessage: String? = null
)

