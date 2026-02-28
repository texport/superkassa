package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_USER_CREATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_USER_DELETED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_USER_UPDATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_USERS_LIST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_USER_OR_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_PIN_BUSY
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.model.*
import kz.mybrain.superkassa.core.application.service.KkmService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/kkm/{kkmId}")
@Tag(name = "Управление кассирами и операторами ККМ", description = "Операции с пользователями ККМ")
class KkmUsersController(private val kkmService: KkmService) {

    /** Получить список пользователей, зарегистрированных в ККМ. */
    @GetMapping("/users")
    @Operation(summary = "Получить список пользователей ККМ", description = "Возвращает список пользователей (кассиров и операторов) данной ККМ. Требуется авторизация (ПИН с правами CASHIER или ADMIN).")
    @KkmApiResponses(
        ok = MSG_200_USERS_LIST,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun listUsers(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): List<UserResponse> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.listUsers(kkmId, pin)
    }

    /** Создать нового пользователя (кассира/админа) для ККМ. */
    @PostMapping("/users")
    @Operation(summary = "Создать пользователя ККМ", description = "Регистрирует нового пользователя ККМ (кассир или администратор) с указанным ПИН-кодом и именем. Требуются права ADMIN.")
    @KkmApiResponses(
        ok = MSG_200_USER_CREATED,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_PIN_BUSY,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createUser(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: UserCreateRequest
    ): UserResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.createUser(kkmId, pin, request)
    }

    /** Обновить данные пользователя ККМ. */
    @PutMapping("/users/{userId}")
    @Operation(summary = "Редактировать пользователя ККМ", description = "Изменяет данные пользователя ККМ (имя, роль, ПИН). Требуются права ADMIN.")
    @KkmApiResponses(
        ok = MSG_200_USER_UPDATED,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_PIN_BUSY,
        notFound = MSG_404_USER_OR_KKM_NOT_FOUND
    )
    fun updateUser(
        @PathVariable kkmId: String,
        @PathVariable userId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: UserUpdateRequest
    ): UserResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.updateUser(kkmId, userId, pin, request)
    }

    /** Удалить пользователя из ККМ. */
    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Удалить пользователя ККМ", description = "Удаляет пользователя из ККМ. Требуются права ADMIN.")
    @KkmApiResponses(
        ok = MSG_200_USER_DELETED,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_NOT_FOUND
    )
    fun deleteUser(
        @PathVariable kkmId: String,
        @PathVariable userId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): Map<String, Boolean> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return mapOf("ok" to kkmService.deleteUser(kkmId, userId, pin))
    }
}
