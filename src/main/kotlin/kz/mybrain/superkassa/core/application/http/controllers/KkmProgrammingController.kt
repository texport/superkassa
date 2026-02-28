package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_PROGRAMMING_ENTER
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_PROGRAMMING_EXIT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.toResponse
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.model.KkmResponse
import kz.mybrain.superkassa.core.application.service.KkmService
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для управления режимом программирования ККМ.
 * Отвечает за вход и выход из режима программирования.
 */
@RestController
@RequestMapping("/kkm")
@Tag(
    name = "Режим программирования ККМ",
    description = "Перевод ККМ в режим программирования и выход из него"
)
class KkmProgrammingController(private val kkmService: KkmService) {

    /**
     * Перевести ККМ в режим программирования.
     * 
     * Требования:
     * - Требуются права администратора (ADMIN)
     * 
     * В этом режиме можно:
     * - Обновлять настройки ККМ (PUT /kkm/{kkmId}/settings)
     * - Удалять ККМ (DELETE /kkm/{kkmId})
     * 
     * В этом режиме нельзя:
     * - Создавать чеки
     * - Открывать/закрывать смены
     * - Формировать отчеты
     * - Выполнять другие фискальные операции
     */
    @PostMapping("/{kkmId}/programming/enter")
    @Operation(
        operationId = "enterProgramming",
        summary = "Войти в режим программирования",
        description = """
            Переводит ККМ в режим программирования - специальный режим обслуживания и настройки.
            
            Режим программирования необходим для:
            - Обновления настроек ККМ (PUT /kkm/{kkmId}/settings)
            - Удаления ККМ из системы (DELETE /kkm/{kkmId})
            
            В режиме программирования все фискальные операции блокируются для обеспечения безопасности.
            После завершения операций настройки необходимо выйти из режима программирования 
            (POST /kkm/{kkmId}/programming/exit) для возобновления работы ККМ.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_PROGRAMMING_ENTER,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun enterProgramming(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.enterProgramming(kkmId, pin).toResponse()
    }

    /**
     * Выйти из режима программирования ККМ.
     * 
     * После выхода из режима программирования ККМ возвращается в нормальный режим работы,
     * и становятся доступны все фискальные операции (чеки, смены, отчеты).
     */
    @PostMapping("/{kkmId}/programming/exit")
    @Operation(
        operationId = "exitProgramming",
        summary = "Выйти из режима программирования",
        description = """
            Выводит ККМ из режима программирования и возвращает её в нормальный режим работы.
            
            После выхода из режима программирования:
            - Становятся доступны все фискальные операции (создание чеков, работа со сменами, отчеты)
            - Операции настройки и удаления ККМ становятся недоступны
            
            Используйте этот метод после завершения операций настройки или удаления ККМ.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_PROGRAMMING_EXIT,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun exitProgramming(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.exitProgramming(kkmId, pin).toResponse()
    }
}
