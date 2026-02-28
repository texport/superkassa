package kz.mybrain.superkassa.storage.domain.config

/**
 * Поддерживаемые движки хранения.
 */
enum class StorageEngine {
    SQLITE,
    POSTGRES,
    MYSQL;

    companion object {
        /**
         * Пытается определить движок по JDBC URL.
         * Возвращает null, если формат не распознан.
         */
        fun fromJdbcUrl(jdbcUrl: String): StorageEngine? {
            val normalized = jdbcUrl.lowercase()
            return when {
                normalized.startsWith("jdbc:sqlite:") -> SQLITE
                normalized.startsWith("jdbc:postgresql:") -> POSTGRES
                normalized.startsWith("jdbc:mysql:") -> MYSQL
                else -> null
            }
        }
    }
}
