package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/** Запрос отчета (X/Z). */
@Serializable
data class ReportRequest(
        val kkmId: String,
        val pin: String,
        val type: ReportType
)
