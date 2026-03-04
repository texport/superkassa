package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_DELETED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_INIT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_STATUS_OR_SHIFT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_DELETE_BLOCKED
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.toResponse
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.model.*
import kz.mybrain.superkassa.core.application.service.KkmService
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для ввода/вывода ККМ из/в эксплуатацию.
 * Отвечает за регистрацию, инициализацию и удаление ККМ.
 */
@RestController
@RequestMapping("/kkm")
@Tag(name = "Ввод/Вывод из/в эксплуатацию ККМ", description = "Регистрация, инициализация и удаление ККМ")
class KkmDecommissioningController(private val kkmService: KkmService) {

    /**
     * Упрощенная инициализация ККМ без черновика.
     * Используется для ККМ, которые уже были зарегистрированы в ОФД ранее.
     * Все необходимые данные получаются из ОФД автоматически.
     */
    @PostMapping("/init")
    @Operation(
        operationId = "03_initKkm",
        summary = "Инициализация ККМ без черновика",
        description = "ККМ должна быть уже создана в ОФД и иметь системный ID и токен доступа. " +
                "Все необходимые данные (регистрационный номер, заводской номер, сервисная информация) " +
                "получаются из ОФД автоматически."
    )
    @KkmApiResponses(
        ok = MSG_200_KKM_INIT,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN
    )
    fun initKkm(
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: KkmInitSimpleRequest
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.initKkmSimple(pin, request).toResponse()
    }

    /**
     * Сгенерировать заводской номер и год выпуска для регистрации ККМ в ОФД.
     * Ничего не создаёт в базе, просто возвращает значения.
     */
    @GetMapping("/factory-info")
    @Operation(
        operationId = "02_generateFactoryInfo",
        summary = "Сгенерировать заводской номер и год выпуска",
        description = "Возвращает заводской номер и год выпуска для регистрации ККМ в ОФД. " +
            "Не создаёт запись ККМ в базе."
    )
    @KkmApiResponses(
        ok = MSG_200_KKM_INIT,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN
    )
    fun generateFactoryInfo(
        @RequestHeader("Authorization") authHeader: String?
    ): FactoryNumberResponse {
        // PIN не требуется, метод не привязан к конкретной ККМ
        return kkmService.generateFactoryInfo()
    }

    /**
     * Удалить ККМ (требуется режим программирования, закрытая смена и права администратора).
     */
    @DeleteMapping("/{kkmId}")
    @Operation(
        operationId = "05_deleteKkm",
        summary = "Удалить ККМ",
        description = """
            Удаляет ККМ из системы полностью.
            
            Требования:
            - ККМ должна быть в режиме программирования (POST /kkm/{kkmId}/programming/enter)
            - Смена должна быть закрыта (нет открытой смены)
            - Очередь команд должна быть пуста
            - Требуются права администратора
            
            Операция необратима. После удаления все данные ККМ (настройки, пользователи, счетчики) 
            будут удалены из системы.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_KKM_DELETED,
        badRequest = MSG_400_BAD_STATUS_OR_SHIFT,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND,
        conflict = MSG_409_DELETE_BLOCKED
    )
    fun deleteKkm(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): Map<String, Boolean> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return mapOf("ok" to kkmService.deleteKkm(kkmId, pin))
    }
}
