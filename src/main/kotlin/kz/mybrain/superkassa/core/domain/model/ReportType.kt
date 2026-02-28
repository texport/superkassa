package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Тип отчета.
 */
@Serializable
enum class ReportType {
    X_REPORT,
    Z_REPORT
}
