package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_INFO
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OFD_SYNC
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OFD_TOKEN_UPDATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SETTINGS_UPDATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_NOT_IDLE
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_NOT_PROGRAMMING_MODE
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_SYNC_ISSUE
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_DRAFT_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_CONFLICT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SYNC_BLOCKED
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.toResponse
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.model.*
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.application.service.KkmService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/kkm")
@Tag(name = "Управление ККМ", description = "Управление настройками и параметрами ККМ, интеграция с ОФД")
class KkmManagementController(private val kkmService: KkmService) {

    /**
     * Получить подробную информацию о конкретной ККМ.
     * 
     * Возвращает все данные о ККМ включая настройки, состояние, информацию об ОФД и статистику.
     */
    @GetMapping("/{kkmId}")
    @Operation(
        summary = "Получить информацию о ККМ",
        description = """
            Возвращает полную информацию о конкретной ККМ по её идентификатору.
            
            Что возвращается:
            - Базовые данные: ID, даты создания и обновления
            - Состояние: режим работы (REGISTRATION), статус (ACTIVE, IDLE, INACTIVE)
            - Информация об ОФД: провайдер, окружение, системный ID, регистрационный номер
            - Данные ККМ: заводской номер, год выпуска
            - Сервисная информация ОФД: организация, адрес, ИНН, геолокация
            - Статистика: номера последних смен, чеков, Z-отчетов
            - Настройки: автономный режим; автозакрытие смены (autoCloseShift); налог — налоговый режим (taxRegime) и базовая группа НДС (defaultVatGroup)
            
            Как использовать:
            - Передайте идентификатор ККМ в пути запроса
            - Метод не требует авторизации (публичный доступ)
            
            Пример использования:
            GET /kkm/kkm-12345
            
            Возвращаемые коды:
            - 200 OK: ККМ найдена, возвращены данные
            - 404 Not Found: ККМ с указанным ID не найдена
        """
    )
    @KkmApiResponses(ok = MSG_200_KKM_INFO, notFound = MSG_404_KKM_NOT_FOUND)
    fun getKkm(@PathVariable kkmId: String): KkmResponse {
        return kkmService.getKkm(kkmId).toResponse()
    }

    /**
     * Изменить настройку автозакрытия смены.
     * Точечная настройка: только автозакрытие смены. Требует режима программирования.
     */
    @PutMapping("/{kkmId}/settings/autocloseshift")
    @Operation(
        summary = "Изменить настройку автозакрытия смены",
        description = """
            Изменяет только настройку автоматического закрытия смены (autoCloseShift).
            
            Как использовать:
            1. Переведите ККМ в режим программирования через POST /kkm/{kkmId}/programming/enter
            2. Вызовите этот метод с новым значением autoCloseShift
            3. Выйдите из режима программирования через POST /kkm/{kkmId}/programming/exit
            
            Требования:
            - ККМ должна быть в режиме программирования (PROGRAMMING mode)
            - ККМ должна находиться в состоянии ACTIVE
            - Требуются права администратора (ADMIN role)
            - ПИН-код в заголовке Authorization (Bearer <pin> или <pin>)
            
            Тело запроса: { "autoCloseShift": true | false }
            
            Ответ: KkmResponse с обновлённой настройкой.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_SETTINGS_UPDATED,
        badRequest = MSG_400_NOT_PROGRAMMING_MODE,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun updateKkmSettings(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: AutoCloseShiftRequest
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.updateKkmSettings(kkmId, pin, request.autoCloseShift).toResponse()
    }

    /**
     * Обновить налоговые настройки ККМ (налоговый режим и базовую группу НДС).
     *
     * Требует режима программирования и прав администратора.
     */
    @PutMapping("/{kkmId}/settings/tax")
    @Operation(
        summary = "Обновить налоговые настройки ККМ",
        description = """
            Обновляет налоговый режим ККМ и базовую группу НДС по умолчанию.
            
            Базовые группы НДС:
            - NO_VAT — «Без НДС» (освобожденный оборот)
            - VAT_0 — «НДС 0%» (облагаемый оборот со ставкой 0%)
            - VAT_16 — «НДС 16%» (стандартная ставка)
            
            Налоговый режим:
            - NO_VAT — касса не является плательщиком НДС
            - VAT_PAYER — касса является плательщиком НДС
            - MIXED — смешанный режим (для будущих сценариев)
            
            Ограничения:
            - ККМ должна быть в режиме программирования (PROGRAMMING)
            - Не должно быть открытой смены
            - Очереди online/offline должны быть пусты (нет неотправленных данных)
            - Требуются права администратора (ADMIN)
            - PIN администратора передается в заголовке Authorization (Bearer <pin> или просто <pin>)
        """
    )
    @KkmApiResponses(
        ok = MSG_200_SETTINGS_UPDATED,
        badRequest = MSG_400_NOT_PROGRAMMING_MODE,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND,
        conflict = MSG_409_CONFLICT
    )
    fun updateKkmTaxSettings(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: KkmTaxSettingsUpdateRequest
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService
            .updateTaxSettings(
                kkmId = kkmId,
                pin = pin,
                taxRegime = request.taxRegime,
                defaultVatGroup = request.defaultVatGroup
            )
            .toResponse()
    }


    /**
     * Обновить токен доступа к ОФД вручную.
     * Для вызова этого метода касса должна быть в режиме программирования.
     */
    @PutMapping("/{kkmId}/ofd/token")
    @Operation(
        summary = "Обновить токен ОФД",
        description = """
            Обновляет токен доступа к ОФД для указанной ККМ.
            Требование: касса должна быть в режиме программирования (POST /kkm/{kkmId}/programming/enter).
            
            Когда использовать:
            - Если токен ОФД был изменен вручную в системе ОФД
            - После восстановления доступа к ОФД с новым токеном
            - При миграции ККМ между системами ОФД
            - В случае ошибок авторизации при работе с ОФД
            
            Требования:
            - ККМ должна быть зарегистрирована
            - Требуются права администратора
            - Новый токен должен быть валидным и активным в системе ОФД
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код администратора в формате "Bearer <pin>" или просто "<pin>"
            - token (в теле запроса): Новый токен доступа к ОФД
            
            Что возвращается:
            - Объект с полем "ok": true при успешном обновлении
            
            Важно:
            - Токен должен быть валидным для указанного провайдера и окружения ОФД
            - После обновления токена рекомендуется проверить связь через GET /kkm/{kkmId}/ofd/ping
            - Неправильный токен приведет к ошибкам при работе с ОФД
            - Система автоматически обновляет токен при необходимости, ручное обновление требуется редко
        """
    )
    @KkmApiResponses(
        ok = MSG_200_OFD_TOKEN_UPDATED,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun updateOfdToken(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: OfdTokenUpdateRequest
    ): Map<String, Boolean> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return mapOf("ok" to kkmService.updateOfdToken(kkmId, pin, request.token))
    }

    /**
     * Синхронизировать информацию ККМ с ОФД.
     * Для вызова этого метода касса должна быть в режиме программирования.
     */
    @PostMapping("/{kkmId}/ofd/sync")
    @Operation(
        summary = "Синхронизировать информацию ККМ с ОФД",
        description = """
            Синхронизирует информацию ККМ с данными из ОФД (регистрационный номер, заводской номер, сервисная информация и т.д.).
            Требование: касса должна быть в режиме программирования (POST /kkm/{kkmId}/programming/enter).
            
            Что делает метод:
            1. Отправляет запрос COMMAND_INFO в ОФД
            2. Получает актуальную информацию о ККМ из системы ОФД
            3. Обновляет локальную базу данных следующими данными:
               - Регистрационный номер ККМ (КГД)
               - Заводской номер
               - Серийный номер
               - Сервисная информация (организация, адрес, ИНН, геолокация)
               - Системный ID в ОФД
            4. Возвращает результат синхронизации
            
            Когда использовать:
            - После регистрации ККМ в ОФД для получения актуальных данных
            - При подозрении на расхождение данных между локальной БД и ОФД
            - После изменения данных ККМ в системе ОФД
            - После восстановления связи с ОФД после автономной работы
            - Перед важными операциями для обеспечения актуальности данных
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Должна быть установлена связь с ОФД (проверяется автоматически)
            - ПИН-код должен соответствовать пользователю с правами ADMIN или CASHIER
            - Не должно быть активных операций, которые могут конфликтовать с синхронизацией
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            
            Что возвращается:
            - OfdCommandResult с полями:
              * status: статус операции (OK - синхронизация успешна, FAILED - ошибка)
              * fiscalSign: фискальный признак (если применимо)
              * errorMessage: описание ошибки (если синхронизация не удалась)
              * responseBin: бинарные данные ответа от ОФД
            
            Ограничения:
            - Синхронизация может быть заблокирована, если есть активные операции с ККМ
            - При ошибке связи с ОФД операция будет отклонена
            - Слишком частые синхронизации могут привести к блокировке со стороны ОФД
            
            Важно:
            - Синхронизация обновляет данные в локальной БД, но не изменяет сам кассовый аппарат
            - После успешной синхронизации рекомендуется проверить данные через GET /kkm/{kkmId}
            - Данные из ОФД имеют приоритет над локальными данными при синхронизации
        """
    )
    @KkmApiResponses(
        ok = MSG_200_OFD_SYNC,
        forbidden = MSG_403_SYNC_ISSUE,
        notFound = MSG_404_KKM_NOT_FOUND,
        conflict = MSG_409_SYNC_BLOCKED
    )
    fun syncOfdServiceInfo(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): OfdCommandResult {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.syncOfdServiceInfo(kkmId, pin)
    }
}
