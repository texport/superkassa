package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.model.CoreSettings

/**
 * Хранилище конфигурации ядра.
 */
interface CoreSettingsRepository {
    fun load(): CoreSettings?
    fun save(settings: CoreSettings): Boolean
    fun loadOrCreate(defaults: CoreSettings): CoreSettings
}
