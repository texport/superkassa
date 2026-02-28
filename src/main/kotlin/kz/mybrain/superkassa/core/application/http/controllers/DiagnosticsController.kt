package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OK
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.storage.application.health.StorageHealthChecker
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер для диагностики системы.
 * Предоставляет эндпоинты для проверки работоспособности компонентов системы.
 */
@RestController
@RequestMapping("/")
@Tag(name = "Диагностика", description = "Проверка работоспособности и состояния Superkassa.")
class DiagnosticsController(
    private val storageHealthChecker: StorageHealthChecker,
    private val storageConfig: StorageConfig,
    private val kkmService: KkmService? = null
) {

    /**
     * Проверка работоспособности сервиса (Health Check).
     * 
     * Проверяет:
     * - Доступность базы данных (обязательно)
     * - Доступность ОФД (опционально, если указан параметр checkOfd=true и есть зарегистрированные ККМ)
     * 
     * Возвращает:
     * - HTTP 200 OK, если все проверки прошли успешно
     * - HTTP 503 Service Unavailable, если хотя бы одна проверка не прошла
     */
    @GetMapping("/health")
    @Operation(
        summary = "Проверка здоровья сервиса",
        description = """
            Проверяет работоспособность компонентов системы Superkassa Core.
            
            Обязательные проверки:
            - Доступность базы данных (storage) - проверяется всегда
            
            Опциональные проверки (если указан параметр checkOfd=true):
            - Доступность ОФД - проверяется для всех уникальных комбинаций ОФД провайдер + окружение
            
            Механизм проверки ОФД:
            При включенной проверке (checkOfd=true) система выполняет следующие шаги:
            1. Получает список всех зарегистрированных ККМ
            2. Группирует ККМ по уникальным комбинациям (ОФД провайдер : окружение)
               Например: "KAZAKHTELECOM:TEST", "KAZAKHTELECOM:PROD", "OTHER_OFD:PROD"
            3. Для каждой уникальной комбинации выбирает одну активную ККМ (если есть)
            4. Отправляет команду COMMAND_SYSTEM в соответствующий ОФД через метод checkOfdConnection()
            5. Команда отправляется через HTTP-запрос к эндпоинту ОФД с использованием настроек
               кассы (ofdId, ofdToken, ofdSystemId) и протокола версии ofdProtocolVersion
            6. Результаты проверки группируются по комбинациям ОФД+окружение:
               - "OK" - если ОФД успешно ответил (статус OfdCommandStatus.OK)
               - "DEGRADED: <описание ошибки>" - если ОФД недоступен или вернул ошибку
               - "SKIPPED: no active KKM" - если нет активных ККМ для данной комбинации
               - "ERROR: <сообщение>" - если произошла исключительная ситуация
            
            Параметры:
            - checkOfd (опционально, по умолчанию false) - включить проверку связи с ОФД
            - ofdEnvironment (опционально) - фильтр по окружению ОФД (TEST/PROD/DEV).
              Если указан, проверяются только ККМ с данным окружением.
              Если не указан, проверяются все окружения.
            - ofdProvider (опционально) - фильтр по провайдеру ОФД (например, "KAZAKHTELECOM").
              Если указан, проверяются только ККМ данного провайдера.
              Если не указан, проверяются все провайдеры.
            
            Примеры использования:
            - GET /health?checkOfd=true - проверить все ОФД и окружения
            - GET /health?checkOfd=true&ofdEnvironment=PROD - проверить только production окружения
            - GET /health?checkOfd=true&ofdProvider=KAZAKHTELECOM - проверить только указанный провайдер
            - GET /health?checkOfd=true&ofdProvider=KAZAKHTELECOM&ofdEnvironment=PROD - проверить конкретную комбинацию
            
            Возвращаемые статусы:
            - "OK" - все проверки прошли успешно (HTTP 200)
            - "DEGRADED" - хотя бы одна проверка не прошла (HTTP 503)
            
            Структура ответа при checkOfd=true:
            {
              "status": "OK" | "DEGRADED",
              "storage": "OK" | "ERROR: ...",
              "ofd": {
                "KAZAKHTELECOM:TEST": "OK" | "DEGRADED: ..." | "SKIPPED: ...",
                "KAZAKHTELECOM:PROD": "OK" | "DEGRADED: ..." | "SKIPPED: ...",
                ...
              }
            }
            
            Используется системами мониторинга и оркестрации (Kubernetes, Docker Healthcheck и т.д.)
            для определения состояния сервиса.
        """
    )
    @KkmApiResponses(ok = MSG_200_OK)
    fun health(
        @RequestParam(required = false) checkOfd: Boolean = false,
        @RequestParam(required = false) ofdEnvironment: String? = null,
        @RequestParam(required = false) ofdProvider: String? = null
    ): ResponseEntity<Map<String, Any>> {
        val status = mutableMapOf<String, Any>()
        val storageStatus = storageHealthChecker.check(storageConfig, timeoutSeconds = 3)
        status["storage"] = storageStatus.message
        var isHealthy = storageStatus.ok
        
        // Опциональная проверка ОФД (только если запрошена и есть KkmService)
        if (checkOfd && kkmService != null) {
            try {
                val kkms = kkmService.listKkms(
                    kz.mybrain.superkassa.core.application.model.KkmListParams(
                        limit = 1000, // Получаем достаточно ККМ для группировки
                        offset = 0
                    )
                )
                
                if (kkms.items.isEmpty()) {
                    status["ofd"] = "SKIPPED: no KKM registered"
                } else {
                    // Группируем ККМ по уникальным комбинациям ОФД провайдер:окружение
                    val ofdGroups = kkms.items
                        .filter { it.ofdProvider != null }
                        .groupBy { it.ofdProvider!! }
                        .mapNotNull { (providerTag, kkmList) ->
                            // Разбиваем провайдер на ofdId:ofdEnvironment
                            val (ofdId, ofdEnv) = splitOfdTag(providerTag)
                            
                            // Применяем фильтры если указаны
                            val matchesProvider = ofdProvider == null || 
                                (ofdId != null && ofdId.equals(ofdProvider, ignoreCase = true))
                            val matchesEnvironment = ofdEnvironment == null || 
                                (ofdEnv != null && ofdEnv.equals(ofdEnvironment, ignoreCase = true))
                            
                            if (matchesProvider && matchesEnvironment) {
                                // Выбираем первую активную ККМ из группы
                                val activeKkm = kkmList.firstOrNull { 
                                    it.state == "ACTIVE" 
                                } ?: kkmList.first()
                                
                                Pair(providerTag, activeKkm)
                            } else {
                                null
                            }
                        }
                    
                    val ofdResults = mutableMapOf<String, String>()
                    
                    for ((providerTag, kkm) in ofdGroups) {
                        try {
                            val ofdResult = kkmService.checkOfdConnection(kkm.id)
                            val resultStatus = if (ofdResult.status == OfdCommandStatus.OK) {
                                "OK"
                            } else {
                                "DEGRADED: ${ofdResult.errorMessage ?: "Unknown error"}"
                            }
                            ofdResults[providerTag] = resultStatus
                            if (ofdResult.status != OfdCommandStatus.OK) {
                                isHealthy = false
                            }
                        } catch (ex: Exception) {
                            ofdResults[providerTag] = "ERROR: ${ex.message ?: ex.toString()}"
                            isHealthy = false
                        }
                    }
                    
                    if (ofdGroups.isEmpty()) {
                        status["ofd"] = "SKIPPED: no matching KKM found for filters"
                    } else {
                        status["ofd"] = ofdResults
                    }
                }
            } catch (ex: Exception) {
                status["ofd"] = "ERROR: ${ex.message}"
                isHealthy = false
            }
        }
        
        return if (isHealthy) {
            status["status"] = "OK"
            ResponseEntity.ok(status)
        } else {
            status["status"] = "DEGRADED"
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status)
        }
    }
    
    private fun splitOfdTag(tag: String?): Pair<String?, String?> {
        if (tag == null) return null to null
        val parts = tag.split(":")
        return if (parts.size == 2) parts[0] to parts[1] else tag to null
    }
}
