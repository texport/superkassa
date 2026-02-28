package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable
import kz.mybrain.superkassa.core.domain.model.KkmInfo

/**
 * Универсальный ответ с пагинацией.
 */
@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean
)

/**
 * Параметры для списка ККМ.
 */
data class KkmListParams(
    val limit: Int = 50,
    val offset: Int = 0,
    val state: String? = null,
    val search: String? = null,
    val sortBy: String = "createdAt",
    val sortOrder: String = "DESC"
) {
    init {
        require(limit in 1..100) { "limit должен быть от 1 до 100" }
        require(offset >= 0) { "offset должен быть >= 0" }
        require(sortOrder in listOf("ASC", "DESC")) { "sortOrder должен быть ASC или DESC" }
        require(sortBy in listOf("createdAt", "updatedAt", "state", "registrationNumber")) {
            "sortBy должен быть одним из: createdAt, updatedAt, state, registrationNumber"
        }
    }
}

/**
 * Результат поиска ККМ с метаданными.
 */
data class KkmListResult(
    val items: List<KkmInfo>,
    val total: Int
)
