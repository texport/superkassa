package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.NotFoundException
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.application.model.UserCreateRequest
import kz.mybrain.superkassa.core.application.model.UserResponse
import kz.mybrain.superkassa.core.application.model.UserUpdateRequest
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.IdGenerator
import kz.mybrain.superkassa.core.domain.port.PinHasherPort
import kz.mybrain.superkassa.core.domain.port.StoragePort

/**
 * Сервис управления пользователями ККМ (кассиры, администраторы).
 * Выделен из KkmService для соблюдения SRP.
 */
class KkmUserService(
    private val storage: StoragePort,
    private val idGenerator: IdGenerator,
    private val clock: ClockPort,
    private val pinHasher: PinHasherPort,
    private val authorization: AuthorizationService
) {
    fun listUsers(kkmId: String, pin: String): List<UserResponse> {
        authorization.requireKkm(kkmId)
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        return storage.listUsers(kkmId).map { UserResponse(it.id, it.name, it.role, it.pin) }
    }

    fun createUser(kkmId: String, pin: String, request: UserCreateRequest): UserResponse {
        authorization.requireKkm(kkmId)
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        if (request.name.isBlank()) {
            throw ValidationException(ErrorMessages.userNameRequired(), "USER_NAME_REQUIRED")
        }
        if (request.userPin.isBlank()) {
            throw ValidationException(ErrorMessages.userPinRequired(), "USER_PIN_REQUIRED")
        }
        val now = clock.now()
        val userId = idGenerator.nextId()
        val created = storage.createUser(
            kkmId = kkmId,
            userId = userId,
            name = request.name,
            role = request.role,
            pin = request.userPin,
            pinHash = pinHasher.hash(request.userPin),
            createdAt = now
        )
        if (!created) {
            throw ConflictException(ErrorMessages.userPinConflict(), "USER_PIN_CONFLICT")
        }
        return UserResponse(userId = userId, name = request.name, role = request.role, pin = request.userPin)
    }

    fun updateUser(kkmId: String, userId: String, pin: String, request: UserUpdateRequest): UserResponse {
        authorization.requireKkm(kkmId)
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        if (request.name == null && request.role == null && request.userPin == null) {
            throw ValidationException(ErrorMessages.userUpdateEmpty(), "USER_UPDATE_EMPTY")
        }
        val existing = storage.findUserById(kkmId, userId)
            ?: throw NotFoundException(ErrorMessages.userNotFound(), "USER_NOT_FOUND")
        val updatedName = request.name ?: existing.name
        if (updatedName.isBlank()) {
            throw ValidationException(ErrorMessages.userNameRequired(), "USER_NAME_REQUIRED")
        }
        val updatedRole = request.role ?: existing.role
        if (existing.role != updatedRole) {
            ensureRolePresence(kkmId, existing.role, userId)
        }
        val updatedPin = request.userPin ?: existing.pin
        if (request.userPin != null && request.userPin.isBlank()) {
            throw ValidationException(ErrorMessages.userPinRequired(), "USER_PIN_REQUIRED")
        }
        val updated = storage.updateUser(
            kkmId = kkmId,
            userId = userId,
            name = updatedName,
            role = updatedRole,
            pin = updatedPin,
            pinHash = request.userPin?.let { pinHasher.hash(it) }
        )
        if (!updated) {
            throw ConflictException(ErrorMessages.userPinConflict(), "USER_PIN_CONFLICT")
        }
        return UserResponse(userId = userId, name = updatedName, role = updatedRole, pin = updatedPin)
    }

    fun deleteUser(kkmId: String, userId: String, pin: String): Boolean {
        authorization.requireKkm(kkmId)
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        val existing = storage.findUserById(kkmId, userId)
            ?: throw NotFoundException(ErrorMessages.userNotFound(), "USER_NOT_FOUND")
        ensureRolePresence(kkmId, existing.role, userId)
        val deleted = storage.deleteUser(kkmId, userId)
        if (!deleted) {
            throw NotFoundException(ErrorMessages.userNotFound(), "USER_NOT_FOUND")
        }
        return true
    }

    private fun ensureRolePresence(kkmId: String, role: UserRole, excludeUserId: String) {
        val users = storage.listUsers(kkmId).filter { it.id != excludeUserId }
        val remaining = users.count { it.role == role }
        if (remaining == 0) {
            throw ValidationException(ErrorMessages.userRoleRequired(role), "USER_ROLE_REQUIRED")
        }
    }

}
