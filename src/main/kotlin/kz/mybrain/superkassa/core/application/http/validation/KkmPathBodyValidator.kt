package kz.mybrain.superkassa.core.application.http.validation

import kz.mybrain.superkassa.core.application.error.ValidationException

/**
 * Проверяет соответствие kkmId из path и body запроса.
 * Выбрасывает ValidationException, если значения не совпадают.
 */
fun requireKkmIdMatch(pathId: String, bodyId: String) {
    if (pathId != bodyId) {
        throw ValidationException("Path kkmId must match body kkmId")
    }
}
