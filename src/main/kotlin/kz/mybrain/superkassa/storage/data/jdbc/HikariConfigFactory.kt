package kz.mybrain.superkassa.storage.data.jdbc

import com.zaxxer.hikari.HikariConfig
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kz.mybrain.superkassa.storage.domain.config.StorageEngine

/**
 * Утилита для создания HikariConfig из StorageConfig.
 */
object HikariConfigFactory {
    fun fromStorageConfig(
        config: StorageConfig,
        maxPoolSize: Int = 10,
        minIdle: Int = 2,
        connectionTimeoutMs: Long = 30000,
        idleTimeoutMs: Long = 600000,
        maxLifetimeMs: Long = 1800000
    ): HikariConfig {
        val hikari = HikariConfig()
        hikari.jdbcUrl = config.jdbcUrl
        hikari.username = config.user
        hikari.password = config.password
        hikari.driverClassName = driverClassName(config.resolvedEngine())
        hikari.maximumPoolSize = maxPoolSize
        hikari.minimumIdle = minIdle
        hikari.connectionTimeout = connectionTimeoutMs
        hikari.idleTimeout = idleTimeoutMs
        hikari.maxLifetime = maxLifetimeMs
        for ((key, value) in config.properties) {
            hikari.addDataSourceProperty(key, value)
        }
        return hikari
    }

    private fun driverClassName(engine: StorageEngine): String {
        return when (engine) {
            StorageEngine.SQLITE -> "org.sqlite.JDBC"
            StorageEngine.POSTGRES -> "org.postgresql.Driver"
            StorageEngine.MYSQL -> "com.mysql.cj.jdbc.Driver"
        }
    }
}
