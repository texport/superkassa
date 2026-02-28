package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_CASH_IN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_CASH_OUT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SHIFT_NOT_OPEN
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.domain.model.CashOperationRequest
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.domain.model.CashOperationResult
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/kkm")
@Tag(name = "Внесение и изъятие наличных денег", description = "Операции с наличными")
class CashOperationsController(private val kkmService: KkmService) {

    /**
     * Внесение наличных денег в кассу (Cash In).
     * 
     * Операция внесения наличных денег в кассовый аппарат. Требует открытой смены.
     * Сумма автоматически преобразуется из тенге (Double) в формат ОФД (bills/coins).
     */
    @PostMapping("/{kkmId}/cash/in")
    @Operation(
        summary = "Внесение наличных",
        description = """
            Вносит наличные деньги в кассовый аппарат.
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Должна быть открыта смена (shift)
            - Сумма должна быть положительным числом
            
            Параметры запроса:
            - **pin** (обязательно): ПИН-код пользователя для авторизации операции.
              Должен соответствовать пользователю с правами CASHIER или ADMIN.
            - **amount** (обязательно): Сумма внесения в тенге (Double).
              Например: 1234.56 означает 1234 тенге 56 тиынов.
              Система автоматически преобразует сумму в формат ОФД (bills/coins):
              * bills = целая часть (тенге)
              * coins = дробная часть * 100 (тиыны)
            - **idempotencyKey** (обязательно): Уникальный ключ идемпотентности операции.
              Используется для защиты от дублирования операций при повторных запросах.
              Должен быть уникальным для каждой операции. Рекомендуется использовать UUID.
              Пример: "550e8400-e29b-41d4-a716-446655440000"
              Если операция с таким ключом уже была выполнена, возвращается результат
              предыдущей операции без повторного выполнения.
            
            Процесс выполнения:
            1. Проверяется существование ККМ и её состояние
            2. Проверяется наличие открытой смены
            3. Проверяется идемпотентность (если операция уже выполнена, возвращается результат)
            4. Создается документ операции в БД
            5. Отправляется команда COMMAND_MONEY_PLACEMENT в ОФД
            6. Ожидается ответ от ОФД и обновляется статус документа
            7. Возвращается результат с идентификатором документа
            
            Возвращаемые коды:
            - 200 OK: Операция успешно выполнена
            - 404 Not Found: ККМ не найдена
            - 409 Conflict: Смена не открыта
            - 403 Forbidden: Недостаточно прав для выполнения операции
        """
    )
    @KkmApiResponses(
        ok = MSG_200_CASH_IN,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun cashIn(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid body: CashOperationRequest
    ): CashOperationResult {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.cashIn(kkmId, pin, body)
    }

    /**
     * Изъятие наличных денег из кассы (Cash Out).
     * 
     * Операция изъятия наличных денег из кассового аппарата. Требует открытой смены.
     * Сумма автоматически преобразуется из тенге (Double) в формат ОФД (bills/coins).
     */
    @PostMapping("/{kkmId}/cash/out")
    @Operation(
        summary = "Изъятие наличных",
        description = """
            Изымает наличные деньги из кассового аппарата.
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Должна быть открыта смена (shift)
            - Сумма должна быть положительным числом
            
            Параметры запроса:
            - **pin** (обязательно): ПИН-код пользователя для авторизации операции.
              Должен соответствовать пользователю с правами CASHIER или ADMIN.
            - **amount** (обязательно): Сумма изъятия в тенге (Double).
              Например: 1234.56 означает 1234 тенге 56 тиынов.
              Система автоматически преобразует сумму в формат ОФД (bills/coins):
              * bills = целая часть (тенге)
              * coins = дробная часть * 100 (тиыны)
            - **idempotencyKey** (обязательно): Уникальный ключ идемпотентности операции.
              Используется для защиты от дублирования операций при повторных запросах.
              Должен быть уникальным для каждой операции. Рекомендуется использовать UUID.
              Пример: "550e8400-e29b-41d4-a716-446655440000"
              Если операция с таким ключом уже была выполнена, возвращается результат
              предыдущей операции без повторного выполнения.
            
            Процесс выполнения:
            1. Проверяется существование ККМ и её состояние
            2. Проверяется наличие открытой смены
            3. Проверяется идемпотентность (если операция уже выполнена, возвращается результат)
            4. Создается документ операции в БД
            5. Отправляется команда COMMAND_MONEY_PLACEMENT в ОФД
            6. Ожидается ответ от ОФД и обновляется статус документа
            7. Возвращается результат с идентификатором документа
            
            Возвращаемые коды:
            - 200 OK: Операция успешно выполнена
            - 404 Not Found: ККМ не найдена
            - 409 Conflict: Смена не открыта
            - 403 Forbidden: Недостаточно прав для выполнения операции
        """
    )
    @KkmApiResponses(
        ok = MSG_200_CASH_OUT,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun cashOut(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid body: CashOperationRequest
    ): CashOperationResult {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.cashOut(kkmId, pin, body)
    }
}
