package kz.mybrain.superkassa.storage.domain.config

/**
 * Конфигурация подключения к хранилищу.
 * Если engine не задан, определяется из jdbcUrl.
 */
data class StorageConfig(
    val jdbcUrl: String,
    val engine: StorageEngine? = null,
    val user: String? = null,
    val password: String? = null,
    val properties: Map<String, String> = emptyMap()
) {
    /**
     * Возвращает определенный движок или выбрасывает ошибку.
     */
    fun resolvedEngine(): StorageEngine {
        return engine ?: StorageEngine.fromJdbcUrl(jdbcUrl)
        ?: error("Unable to resolve storage engine from jdbcUrl: $jdbcUrl")
    }
}
