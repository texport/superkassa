package kz.mybrain.superkassa.storage.application.connector

import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kz.mybrain.superkassa.storage.domain.config.StorageEngine
import java.sql.Connection

/**
 * Контракт подключения к конкретному движку БД.
 */
interface StorageConnector {
    val engine: StorageEngine
    fun connect(config: StorageConfig): Connection
}
