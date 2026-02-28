package kz.mybrain.superkassa.storage.data.bootstrap

import kz.mybrain.superkassa.storage.application.migration.MigrationRunner
import kz.mybrain.superkassa.storage.application.bootstrap.StorageBootstrap
import kz.mybrain.superkassa.storage.application.connector.StorageManager
import kz.mybrain.superkassa.storage.data.jdbc.DefaultStorageConnectorRegistry
import kz.mybrain.superkassa.storage.data.jdbc.JdbcStorageSessionFactory
import kz.mybrain.superkassa.storage.data.migration.JdbcMigrationRunner

/**
 * Bootstrap по умолчанию: миграции + JDBC сессии.
 */
class DefaultStorageBootstrap(
    private val registry: DefaultStorageConnectorRegistry = DefaultStorageConnectorRegistry(),
    private val migrationRunner: MigrationRunner = JdbcMigrationRunner(registry),
    private val storageManager: StorageManager = StorageManager(
        registry,
        JdbcStorageSessionFactory()
    )
) : StorageBootstrap(storageManager, migrationRunner)
