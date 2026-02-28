package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable

/**
 * Запрос на закрытие смены (Z-отчет).
 * Пустой DTO, так как все параметры берутся из пути и заголовка.
 */
@Serializable
data class CloseShiftRequest(
    @kotlinx.serialization.Transient
    private val _unused: String? = null // Пустой DTO: kkmId и pin берутся из пути и заголовка
)
