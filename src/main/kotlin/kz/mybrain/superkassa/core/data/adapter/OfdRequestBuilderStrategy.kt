package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kotlinx.serialization.json.JsonObject

/**
 * Стратегия построения JSON-запроса к ОФД для конкретного типа команды.
 * Позволяет расширять функциональность без изменения существующего кода (OCP).
 */
interface OfdRequestBuilderStrategy {
    /**
     * Проверяет, может ли стратегия обработать данный тип команды.
     */
    fun canHandle(commandType: kz.mybrain.superkassa.core.domain.model.OfdCommandType): Boolean

    /**
     * Строит JSON-запрос для команды.
     * @return JSON-объект запроса или null, если запрос не может быть построен.
     */
    fun build(command: OfdCommandRequest, config: kz.mybrain.superkassa.core.data.ofd.OfdConfig): JsonObject?
}
