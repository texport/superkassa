package kz.mybrain.superkassa.core.application.error

/**
 * Базовая ошибка сервиса с HTTP-статусом.
 */
open class ServiceException(
    val code: String,
    val status: Int,
    message: String
) : RuntimeException(message)

class NotFoundException(message: String, code: String = "NOT_FOUND") : ServiceException(code, 404, message)

class ConflictException(message: String, code: String = "CONFLICT") : ServiceException(code, 409, message)

class ForbiddenException(message: String, code: String = "FORBIDDEN") : ServiceException(code, 403, message)

class ValidationException(message: String, code: String = "BAD_REQUEST") : ServiceException(code, 400, message)
