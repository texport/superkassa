package kz.mybrain.superkassa.core.application.model

import kz.mybrain.superkassa.core.domain.model.UnitOfMeasurement
import kotlinx.serialization.Serializable

/**
 * DTO для API справочника единиц измерения (ОКЕИ).
 */
@Serializable
data class UnitOfMeasurementResponse(
    val code: String,
    val nameShort: String,
    val nameFull: String
) {
    companion object {
        fun from(uom: UnitOfMeasurement): UnitOfMeasurementResponse =
            UnitOfMeasurementResponse(
                code = uom.code,
                nameShort = uom.shortRus,
                nameFull = uom.nameRus
            )
    }
}
