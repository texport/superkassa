package kz.mybrain.superkassa.storage.application.migration

import kz.mybrain.superkassa.storage.domain.config.StorageConfig

/**
 * Выполняет миграции схемы.
 */
interface MigrationRunner {
    fun migrate(config: StorageConfig)
}
