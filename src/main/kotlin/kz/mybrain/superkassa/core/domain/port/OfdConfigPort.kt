package kz.mybrain.superkassa.core.domain.port

/**
 * Порт для работы с конфигурацией ОФД (провайдеры, окружения, endpoints).
 * Инкапсулирует доступ к реестру ОФД, изолируя application-слой от data-слоя.
 */
interface OfdConfigPort {
    /** Проверяет наличие провайдера, окружения и endpoint. Возвращает отформатированный тег. */
    fun validateAndFormatTag(providerId: String, environmentId: String): String

    /** Парсит тег вида "PROVIDER:ENVIRONMENT" в пару (providerId, environmentId). */
    fun parseTag(tag: String): Pair<String, String>

    /** Проверяет, что endpoint существует для данной пары providerId + environmentId. */
    fun hasEndpoint(providerId: String, environmentId: String): Boolean
}
