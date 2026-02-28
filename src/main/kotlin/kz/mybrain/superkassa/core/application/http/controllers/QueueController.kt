package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OK
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.service.QueueManagementService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер для управления очередью ОФД.
 *
 * Предназначен для диагностики и сервисных операций и доступен только
 * в режиме программирования ККМ при закрытой смене.
 */
@RestController
@RequestMapping("/kkm/{kkmId}/queue")
@Tag(
    name = "Управление очередью ОФД",
    description = "Диагностика и управление очередью команд ОФД (только в режиме программирования, ADMIN)"
)
class QueueController(
    private val queueManagementService: QueueManagementService
    ) {

    /**
     * Получить список задач очереди ОФД по ККМ.
     *
     * Возвращает элементы online/offline очереди с их статусами.
     */
    @GetMapping
    @Operation(
        summary = "Список задач очереди ОФД",
        description = """
            Возвращает список задач очереди ОФД для указанной ККМ.
            
            Что показывает:
            - lane: ONLINE или OFFLINE
            - type: тип команды (TICKET, REPORT_X, CLOSE_SHIFT, MONEY_PLACEMENT и т.п.)
            - status: текущий статус (PENDING, IN_PROGRESS, SENT, FAILED)
            - attempt: количество попыток отправки
            - nextAttemptAt: время следующей попытки (epoch millis), если запланирована
            - lastError: последнее сообщение об ошибке (если было)
            
            Ограничения:
            - ККМ должна находиться в режиме программирования (mode/state = PROGRAMMING)
            - Не должно быть открытой смены
            - Доступен только пользователю с ролью ADMIN
            - PIN администратора передается в заголовке Authorization (Bearer <pin> или просто <pin>)
        """
    )
    @KkmApiResponses(
        ok = MSG_200_OK,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun listQueue(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): List<QueueManagementService.QueueItemView> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return queueManagementService.listQueue(kkmId, pin)
    }

    /**
     * Перезапустить задачи со статусом FAILED.
     *
     * Переводит все задачи очереди ОФД со статусом FAILED в статус PENDING,
     * чтобы они были повторно обработаны обычным воркером очереди.
     */
    @PostMapping("/retry-failed")
    @Operation(
        summary = "Повторная отправка задач со статусом FAILED",
        description = """
            Переводит все задачи очереди ОФД со статусом FAILED в статус PENDING
            для указанной ККМ, чтобы очередной воркер мог повторно их обработать.
            
            Ограничения:
            - ККМ должна находиться в режиме программирования (mode/state = PROGRAMMING)
            - Не должно быть открытой смены
            - Доступен только пользователю с ролью ADMIN
            - PIN администратора передается в заголовке Authorization (Bearer <pin> или просто <pin>)
            
            Возвращает:
            - количество задач, переведенных в статус PENDING.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_OK,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun retryFailed(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): Map<String, Int> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val updated = queueManagementService.retryFailed(kkmId, pin)
        return mapOf("updated" to updated)
    }
}

