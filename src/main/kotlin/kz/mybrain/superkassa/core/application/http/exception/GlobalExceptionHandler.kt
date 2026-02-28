package kz.mybrain.superkassa.core.application.http.exception

import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ForbiddenException
import kz.mybrain.superkassa.core.application.error.NotFoundException
import kz.mybrain.superkassa.core.application.error.ServiceException
import kz.mybrain.superkassa.core.application.error.SettingsFrozenException
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.application.model.ApiErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(ex: ValidationException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse(code = ex.code, message = ex.message ?: "Validation error"))
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiErrorResponse(
                                code = ex.code,
                                message = ex.message ?: "Resource not found"
                        )
                )
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbiddenException(ex: ForbiddenException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse(code = ex.code, message = ex.message ?: "Access denied"))
    }

    @ExceptionHandler(SettingsFrozenException::class)
    fun handleSettingsFrozenException(
            ex: SettingsFrozenException
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse(code = ex.code, message = ex.message ?: "Changes forbidden"))
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflictException(ex: ConflictException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse(code = ex.code, message = ex.message ?: "Conflict"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleSpringValidationException(
            ex: MethodArgumentNotValidException
    ): ResponseEntity<ApiErrorResponse> {
        val messages =
                ex.bindingResult.allErrors.joinToString("; ") { error ->
                    val fieldName =
                            (error as? org.springframework.validation.FieldError)?.field
                                    ?: error.objectName
                    "$fieldName: ${error.defaultMessage}"
                }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse(code = "VALIDATION_ERROR", message = messages))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(
            ex: MethodArgumentTypeMismatchException
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse(
                                code = "INVALID_PARAM",
                                message = "Parameter '${ex.name}' has invalid type: ${ex.value}"
                        )
                )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleJsonException(ex: HttpMessageNotReadableException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse(
                                code = "INVALID_JSON",
                                message = "Malformed JSON request or invalid format"
                        )
                )
    }

    @ExceptionHandler(ServiceException::class)
    fun handleServiceException(ex: ServiceException): ResponseEntity<ApiErrorResponse> {
        val status = HttpStatus.resolve(ex.status) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status)
                .body(ApiErrorResponse(code = ex.code, message = ex.message ?: "Service error"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiErrorResponse> {
        logger.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse(code = "INTERNAL_ERROR", message = "Internal server error"))
    }

    @ExceptionHandler(Throwable::class)
    fun handleThrowable(ex: Throwable): ResponseEntity<ApiErrorResponse> {
        logger.error("Critical error (Throwable/Error)", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse(code = "CRITICAL_ERROR", message = "Critical system error occurred"))
    }
}
