package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable

/**
 * Запрос на создание X-отчета.
 * Пустой DTO, так как все параметры берутся из пути и заголовка.
 */
@Serializable
data class XReportRequest(
    @kotlinx.serialization.Transient
    private val _unused: String? = null // Пустой DTO: kkmId и pin берутся из пути и заголовка
)
