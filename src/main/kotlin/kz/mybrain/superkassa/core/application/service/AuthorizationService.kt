package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ForbiddenException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.NotFoundException
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.port.PinHasherPort
import kz.mybrain.superkassa.core.domain.port.StoragePort

/**
 * Сервис авторизации для проверки прав доступа и существования ККМ.
 * Вынесен для устранения дублирования между KkmService и KkmUserService.
 */
class AuthorizationService(
    private val storage: StoragePort,
    private val pinHasher: PinHasherPort
) {
    /**
     * Проверяет существование ККМ и выбрасывает NotFoundException, если не найдена.
     */
    fun requireKkm(kkmId: String): KkmInfo {
        return storage.findKkm(kkmId)
            ?: throw NotFoundException(ErrorMessages.kkmNotFound(), "KKM_NOT_FOUND")
    }

    /**
     * Проверяет права доступа пользователя по PIN-коду.
     * @param kkmId Идентификатор ККМ
     * @param pin PIN-код пользователя
     * @param allowed Разрешённые роли
     * @throws ValidationException Если PIN пустой
     * @throws ForbiddenException Если пользователь не найден или роль не разрешена
     */
    fun requireRole(kkmId: String, pin: String, allowed: Set<UserRole>) {
        if (pin.isBlank()) {
            throw ValidationException(ErrorMessages.userPinRequired(), "PIN_REQUIRED")
        }
        val pinHash = pinHasher.hash(pin)
        val user = storage.findUserByPin(kkmId, pinHash)
            ?: throw ForbiddenException(ErrorMessages.userNotFound(), "USER_NOT_FOUND")
        if (!allowed.contains(user.role)) {
            throw ForbiddenException(ErrorMessages.userForbidden(), "USER_FORBIDDEN")
        }
    }
}
