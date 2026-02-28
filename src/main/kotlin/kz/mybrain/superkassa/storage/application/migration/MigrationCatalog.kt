package kz.mybrain.superkassa.storage.application.migration

import kz.mybrain.superkassa.storage.domain.config.StorageEngine

/**
 * Описание SQL-миграции.
 */
data class MigrationScript(
    val version: String,
    val resourcePath: String,
    val checksum: String
)

/**
 * Каталог миграций по движку БД.
 */
interface MigrationCatalog {
    fun scriptsFor(engine: StorageEngine): List<MigrationScript>
}
