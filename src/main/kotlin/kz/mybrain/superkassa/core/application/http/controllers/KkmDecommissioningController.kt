package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_DRAFT_CREATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_DRAFT_FISCALIZED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_DRAFT_UPDATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_DELETED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_INIT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_STATUS_OR_SHIFT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_NOT_IDLE
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_DRAFT_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_CONFLICT
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
     * Создать черновик ККМ (Draft KKM). Возвращает заводской номер и год выпуска для регистрации ККМ.
     */
    @PostMapping("/draft")
    @Operation(
        operationId = "02_createDraft",
        summary = "Создать черновик ККМ (Draft KKM)",
        description = "Возвращает заводской номер и год выпуска для регистрации ККМ в ОФД."
    )
    @KkmApiResponses(
        ok = MSG_200_DRAFT_CREATED,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_CONFLICT
    )
    fun createDraft(
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: DraftKkmRequest
    ): DraftKkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.createDraftKkm(pin, request)
    }

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
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_CONFLICT
    )
    fun initKkm(
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: KkmInitSimpleRequest
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.initKkmSimple(pin, request).toResponse()
    }

    /**
     * Завершение инициализации (фискализация) ранее созданного черновика ККМ.
     */
    @PostMapping("/init-draft")
    @Operation(
        operationId = "04_initDraftKkm",
        summary = "Инициализация черновика ККМ"
    )
    @KkmApiResponses(
        ok = MSG_200_DRAFT_FISCALIZED,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_DRAFT_NOT_FOUND,
        conflict = MSG_409_CONFLICT
    )
    fun initDraftKkm(
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: KkmInitDraftRequest
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.initDraftKkm(pin, request).toResponse()
    }

    /**
     * Обновить данные черновика ККМ.
     * 
     * Позволяет изменить данные черновика ККМ перед его фискализацией.
     * Доступно только для ККМ в статусе IDLE (черновик).
     */
    @PutMapping("/{kkmId}/draft")
    @Operation(
        summary = "Редактировать черновик ККМ",
        description = """
            Обновляет данные черновика ККМ перед фискализацией.
            
            Что такое черновик ККМ:
            Черновик - это предварительная запись о ККМ, созданная через POST /kkm/draft.
            Черновик содержит базовую информацию (заводской номер, год выпуска) и находится
            в статусе IDLE до момента фискализации через POST /kkm/init-draft.
            
            Когда использовать:
            - Если нужно исправить данные черновика перед фискализацией
            - При изменении заводского номера или года выпуска
            - Перед регистрацией ККМ в ОФД
            
            Требования:
            - ККМ должна находиться в статусе IDLE (черновик)
            - Если ККМ уже зарегистрирована (статус ACTIVE), метод вернет ошибку 400
            - Требуются права администратора
            
            Что передавать:
            - kkmId (в пути): Идентификатор черновика ККМ
            - factoryNumber (в теле запроса, опционально): Новый заводской номер
            - manufactureYear (в теле запроса, опционально): Новый год выпуска
            
            Что возвращается:
            - KkmResponse с обновленными данными черновика
            
            Важно:
            - После обновления черновик остается в статусе IDLE
            - Для фискализации используйте POST /kkm/init-draft
            - Изменение данных черновика не влияет на уже зарегистрированные ККМ
        """
    )
    @KkmApiResponses(
        ok = MSG_200_DRAFT_UPDATED,
        badRequest = MSG_400_NOT_IDLE,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_DRAFT_NOT_FOUND,
        conflict = MSG_409_CONFLICT
    )
    fun updateDraft(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: KkmDraftUpdateRequest
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.updateDraftKkm(kkmId, pin, request).toResponse()
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
