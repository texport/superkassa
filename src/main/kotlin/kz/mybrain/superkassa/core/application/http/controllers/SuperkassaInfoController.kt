package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_LIST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_VERSION
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.toResponse
import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.application.model.KkmListParams
import kz.mybrain.superkassa.core.application.model.KkmResponse
import kz.mybrain.superkassa.core.application.model.PaginatedResponse
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер для получения информации о Superkassa.
 * Предоставляет информацию о версии, режиме работы и статистике системы.
 */
@RestController
@RequestMapping("/")
@Tag(name = "О Superkassa", description = "Информация о версии, режиме работы и статистике.")
class SuperkassaInfoController(
    private val coreSettings: CoreSettings,
    private val storage: StoragePort,
    private val kkmService: KkmService,
    @Value("\${app.version:1.0}") private val appVersion: String
) {

    /**
     * Получить список ККМ с фильтрацией и пагинацией.
     */
    @GetMapping("kkm")
    @Operation(
        summary = "Получить список ККМ (с фильтрацией)",
        description = """
            Возвращает список ККМ с поддержкой фильтрации, поиска, сортировки и пагинации.
            Параметры: limit, offset, state (ACTIVE, IDLE, INACTIVE), search, sortBy (createdAt, updatedAt, factoryNumber), order (ASC, DESC).
            Ответ: items (массив KkmResponse), total, limit, offset, hasMore.
        """
    )
    @KkmApiResponses(ok = MSG_200_KKM_LIST)
    fun listKkms(
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false, defaultValue = "createdAt") sortBy: String,
        @RequestParam(required = false, defaultValue = "DESC") order: String
    ): PaginatedResponse<KkmResponse> {
        val params = KkmListParams(
            limit = limit,
            offset = offset,
            state = state,
            search = search,
            sortBy = sortBy,
            sortOrder = order
        )
        val result = kkmService.listKkms(params)
        return PaginatedResponse(
            items = result.items.map { it.toResponse() },
            total = result.total,
            limit = params.limit,
            offset = params.offset,
            hasMore = (params.offset + params.limit) < result.total
        )
    }

    /**
     * Получить информацию о Superkassa.
     * 
     * Возвращает:
     * - Версию приложения
     * - Режим работы (DESKTOP, SERVER)
     * - Статистику системы (количество зарегистрированных ККМ)
     * - Информацию о хранилище данных
     */
    @GetMapping("/info")
    @Operation(
        summary = "Информация о Superkassa",
        description = """
            Возвращает подробную информацию о системе Superkassa Core.
            
            Структура ответа:
            
            **name** (string): Название системы - всегда "Superkassa Core"
            
            **version** (string): Версия приложения, берется из конфигурации app.version
            
            **mode** (string): Режим работы системы:
            - DESKTOP - десктопное приложение для локального использования
            - SERVER - серверный режим для кластера
            
            **nodeId** (string): Уникальный идентификатор узла в кластере.
            Используется в SERVER режиме для идентификации узла при распределенной обработке.
            
            **ofdProtocolVersion** (string): Версия протокола ОФД, используемая для обмена с ОФД.
            Текущая версия: "203"
            
            **storage** (object): Информация о хранилище данных:
            - engine (string): Тип СУБД (SQLITE, POSTGRESQL, MYSQL)
            - jdbcUrl (string): JDBC URL для подключения к БД (пароль скрыт)
            
            **statistics** (object): Статистика системы:
            - registeredKkms (number): Количество зарегистрированных ККМ в системе
            
            **features** (object): Дополнительные возможности системы:
            - allowSettingsChanges (boolean): Разрешены ли изменения настроек через API
            - deliveryChannels (array<string>): Список каналов доставки документов (например, ["PRINT"])
            - ofdTimeoutSeconds (number): Общее время на обработку транзакции ОФД (сек., не менее 5)
            - ofdReconnectIntervalSeconds (number): Интервал между попытками восстановления связи (сек., не менее 60)
            
            Используется для мониторинга и диагностики системы.
        """
    )
    @KkmApiResponses(ok = MSG_200_VERSION)
    fun info(): Map<String, Any> {
        val kkmCount = try {
            storage.countKkms(state = null, search = null)
        } catch (e: Exception) {
            0
        }
        
        return mapOf(
            "name" to "Superkassa Core",
            "version" to appVersion,
            "mode" to coreSettings.mode.name,
            "nodeId" to coreSettings.nodeId,
            "ofdProtocolVersion" to coreSettings.ofdProtocolVersion,
            "storage" to mapOf(
                "engine" to coreSettings.storage.engine,
                "jdbcUrl" to coreSettings.storage.jdbcUrl.replace(Regex(":.*@"), ":***@") // Скрываем пароль
            ),
            "statistics" to mapOf(
                "registeredKkms" to kkmCount
            ),
            "features" to mapOf(
                "allowSettingsChanges" to coreSettings.allowChanges,
                "deliveryChannels" to coreSettings.deliveryChannels,
                "ofdTimeoutSeconds" to coreSettings.ofdTimeoutSeconds,
                "ofdReconnectIntervalSeconds" to coreSettings.ofdReconnectIntervalSeconds
            )
        )
    }
}
