package kz.mybrain.superkassa.core.application.http.annotation

import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_401_UNAUTHORIZED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_500_INTERNAL

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KkmApiResponses(
        val ok: String,
        val notFound: String = "",
        val conflict: String = "",
        val badRequest: String = MSG_400_VALIDATION,
        val unauthorized: String = MSG_401_UNAUTHORIZED,
        val forbidden: String = MSG_403_FORBIDDEN,
        val internal: String = MSG_500_INTERNAL
)
