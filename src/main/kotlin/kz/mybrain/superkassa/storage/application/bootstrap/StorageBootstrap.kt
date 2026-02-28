package kz.mybrain.superkassa.storage.application.bootstrap

import kz.mybrain.superkassa.storage.application.connector.StorageManager
import kz.mybrain.superkassa.storage.application.migration.MigrationRunner
import kz.mybrain.superkassa.storage.application.session.StorageSession
import kz.mybrain.superkassa.storage.domain.config.StorageConfig

/**
 * Упрощенный фасад для миграций и открытия сессии.
 */
open class StorageBootstrap(
    private val manager: StorageManager,
    private val migrationRunner: MigrationRunner
) {
    /**
     * Применяет миграции схемы.
     */
    fun migrate(config: StorageConfig) {
        migrationRunner.migrate(config)
    }

    /**
     * Открывает сессию для работы с репозиториями.
     */
    fun openSession(config: StorageConfig): StorageSession {
        return manager.openSession(config)
    }
}
