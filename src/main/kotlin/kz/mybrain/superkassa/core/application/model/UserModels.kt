package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import kz.mybrain.superkassa.core.domain.model.UserRole

@Serializable
@Schema(description = "Запрос на создание пользователя")
data class UserCreateRequest(
        @Schema(description = "Имя нового пользователя", example = "Иван Иванов")
        @field:NotBlank(message = "Name is required")
        val name: String,
        @Schema(description = "Роль пользователя (ADMIN или CASHIER)", example = "CASHIER")
        val role: UserRole,
        @Schema(description = "ПИН-код нового пользователя", example = "1234")
        @field:NotBlank(message = "User PIN is required")
        @field:Size(min = 4, max = 10, message = "PIN length must be between 4 and 10")
        val userPin: String
)

@Serializable
@Schema(description = "Запрос на обновление пользователя")
data class UserUpdateRequest(
        @Schema(description = "Новое имя пользователя", example = "Петр Петров")
        val name: String? = null,
        @Schema(description = "Новая роль", example = "ADMIN") val role: UserRole? = null,
        @Schema(description = "Новый ПИН-код пользователя", example = "4321")
        val userPin: String? = null
)

@Serializable
@Schema(description = "Запрос на удаление пользователя")
data class UserDeleteRequest(
        @Schema(description = "Не используется. ПИН-код передается только в заголовке Authorization", example = "deprecated")
        val _unused: String? = null
)

@Serializable
@Schema(description = "Данные пользователя")
data class UserResponse(
        @Schema(description = "ID пользователя", example = "user-123") val userId: String,
        @Schema(description = "Имя пользователя", example = "Иван Иванов") val name: String,
        @Schema(description = "Роль пользователя", example = "CASHIER") val role: UserRole,
        @Schema(
                description = "ПИН-код пользователя (возвращается только для справки)",
                example = "1234"
        )
        val pin: String?
)

@Serializable
@Schema(description = "Запрос с подтверждением ПИН-кода")
data class PinRequest(
        @Schema(
                description = "Не используется. ПИН-код передается только в заголовке Authorization",
                example = "deprecated"
        )
        val _unused: String? = null
)

@Serializable
@Schema(description = "Информация об авторизации в ОФД")
data class OfdAuthInfoResponse(
        @Schema(description = "Текущий токен ОФД", example = "token-xyz") val token: String?,
        @Schema(description = "Следующий номер запроса (reqNum)", example = "105")
        val nextReqNum: Int
)

@Serializable
@Schema(description = "Запрос на обновление токена ОФД")
data class OfdTokenUpdateRequest(
        @Schema(description = "Новый токен ОФД", example = "new-token-123")
        @field:NotBlank
        val token: String
)

@Serializable
@Schema(description = "Запрос на обновление параметров черновика ККМ")
data class KkmDraftUpdateRequest(
        @Schema(description = "ID ОФД провайдера", example = "kazakhtelecom")
        val ofdId: String? = null,
        @Schema(description = "Среда ОФД", example = "test") val ofdEnvironment: String? = null,
        @Schema(description = "Системный ID в ОФД", example = "system-id-123")
        val ofdSystemId: String? = null,
        @Schema(description = "Заводской номер ККМ", example = "ZAVOD-001")
        val factoryNumber: String? = null,
        @Schema(description = "Год выпуска", example = "2024") val manufactureYear: Int? = null
)
