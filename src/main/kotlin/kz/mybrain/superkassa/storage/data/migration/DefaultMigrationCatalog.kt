package kz.mybrain.superkassa.storage.data.migration

import kz.mybrain.superkassa.storage.application.migration.MigrationCatalog
import kz.mybrain.superkassa.storage.application.migration.MigrationScript
import kz.mybrain.superkassa.storage.domain.config.StorageEngine

/**
 * Каталог миграций по умолчанию.
 */
class DefaultMigrationCatalog : MigrationCatalog {
    override fun scriptsFor(engine: StorageEngine): List<MigrationScript> {
        val prefix = when (engine) {
            StorageEngine.SQLITE -> "db/migration/sqlite"
            StorageEngine.POSTGRES -> "db/migration/postgres"
            StorageEngine.MYSQL -> "db/migration/mysql"
        }
        return listOf(
            MigrationScript(
                version = "1",
                resourcePath = "$prefix/V1__init.sql",
                checksum = "v1"
            ),
            MigrationScript(
                version = "2",
                resourcePath = "$prefix/V2__error_log.sql",
                checksum = "v2"
            ),
            MigrationScript(
                version = "3",
                resourcePath = "$prefix/V3__shift_and_counters.sql",
                checksum = "v3"
            ),
            MigrationScript(
                version = "4",
                resourcePath = "$prefix/V4__reference_data.sql",
                checksum = "v4"
            ),
            MigrationScript(
                version = "5",
                resourcePath = "$prefix/V5__outbox_and_indexes.sql",
                checksum = "v5"
            ),
            MigrationScript(
                version = "6",
                resourcePath = "$prefix/V6__cashbox_service_info.sql",
                checksum = "v6"
            ),
            MigrationScript(
                version = "7",
                resourcePath = "$prefix/V7__kkm_operator.sql",
                checksum = "v7"
            ),
            MigrationScript(
                version = "8",
                resourcePath = "$prefix/V8__kkm_user.sql",
                checksum = "v8"
            ),
            MigrationScript(
                version = "9",
                resourcePath = "$prefix/V9__cashbox_system_id_unique.sql",
                checksum = "v9"
            ),
            MigrationScript(
                version = "10",
                resourcePath = "$prefix/V10__cashbox_auto_close_shift.sql",
                checksum = "v10"
            ),
            MigrationScript(
                version = "11",
                resourcePath = "$prefix/V11__queue_tasks_and_locks.sql",
                checksum = "v11"
            ),
            MigrationScript(
                version = "12",
                resourcePath = "$prefix/V12__cashbox_tax_regime.sql",
                checksum = "v12"
            )
        )
    }
}
