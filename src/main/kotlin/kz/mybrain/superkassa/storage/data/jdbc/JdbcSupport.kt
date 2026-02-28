package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * Низкоуровневая работа с JDBC, без бизнес-логики.
 */
internal object JdbcSupport {
    /**
     * Открывает JDBC-соединение, регистрируя драйвер.
     */
    fun openConnection(config: StorageConfig, driverClassName: String): Connection {
        Class.forName(driverClassName)
        val props = Properties()
        config.user?.let { props["user"] = it }
        config.password?.let { props["password"] = it }
        for ((key, value) in config.properties) {
            props[key] = value
        }
        return if (props.isEmpty) {
            DriverManager.getConnection(config.jdbcUrl)
        } else {
            DriverManager.getConnection(config.jdbcUrl, props)
        }
    }
}
