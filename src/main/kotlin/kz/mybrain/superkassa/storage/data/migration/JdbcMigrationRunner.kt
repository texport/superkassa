package kz.mybrain.superkassa.storage.data.migration

import kz.mybrain.superkassa.storage.application.migration.MigrationCatalog
import kz.mybrain.superkassa.storage.application.migration.MigrationRunner
import kz.mybrain.superkassa.storage.application.connector.StorageConnectorRegistry
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.sql.Connection
import org.slf4j.LoggerFactory

/**
 * JDBC-исполнитель миграций.
 */
class JdbcMigrationRunner(
    private val registry: StorageConnectorRegistry,
    private val catalog: MigrationCatalog = DefaultMigrationCatalog()
) : MigrationRunner {
    private val logger = LoggerFactory.getLogger(JdbcMigrationRunner::class.java)

    override fun migrate(config: StorageConfig) {
        val engine = config.resolvedEngine()
        logger.info("Migration started. engine={}", engine)
        val connection = registry.connectorFor(engine).connect(config)
        connection.use {
            ensureSchemaMigrationsTable(connection)
            val applied = loadAppliedVersions(connection)
            val scripts = catalog.scriptsFor(engine)
            for (script in scripts) {
                if (applied.contains(script.version)) {
                    logger.info("Migration skipped. version={}", script.version)
                    continue
                }
                logger.info("Migration apply. version={}, resource={}", script.version, script.resourcePath)
                val sql = readResource(script.resourcePath)
                applySql(connection, sql)
                insertMigration(connection, script.version, script.checksum)
                logger.info("Migration applied. version={}", script.version)
            }
        }
        logger.info("Migration finished. engine={}", engine)
    }

    private fun ensureSchemaMigrationsTable(connection: Connection) {
        val sql = """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version TEXT PRIMARY KEY,
                checksum TEXT NOT NULL,
                applied_at BIGINT NOT NULL
            )
        """.trimIndent()
        connection.createStatement().use { stmt ->
            stmt.execute(sql)
        }
    }

    private fun loadAppliedVersions(connection: Connection): Set<String> {
        val sql = "SELECT version FROM schema_migrations"
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val versions = mutableSetOf<String>()
                while (rs.next()) {
                    versions.add(rs.getString("version"))
                }
                return versions
            }
        }
    }

    private fun applySql(connection: Connection, sql: String) {
        val statements = sql.splitToSequence(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        connection.createStatement().use { stmt ->
            for (statement in statements) {
                stmt.execute(statement)
            }
        }
    }

    private fun insertMigration(connection: Connection, version: String, checksum: String) {
        val sql = """
            INSERT INTO schema_migrations (version, checksum, applied_at)
            VALUES (?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, version)
            stmt.setString(2, checksum)
            stmt.setLong(3, System.currentTimeMillis())
            stmt.executeUpdate()
        }
    }

    private fun readResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: error("Migration resource not found: $path")
        BufferedReader(InputStreamReader(stream)).use { reader ->
            return reader.readText()
        }
    }
}
