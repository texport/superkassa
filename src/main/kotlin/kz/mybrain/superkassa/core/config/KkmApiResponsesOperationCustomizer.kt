import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod

@Component
class KkmApiResponsesOperationCustomizer : OperationCustomizer {
    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        val annotation = handlerMethod.getMethodAnnotation(KkmApiResponses::class.java)
        if (annotation != null) {
            val responses = operation.responses ?: ApiResponses()

            // 200 OK
            addResponse(responses, "200", annotation.ok)

            // 400 Bad Request
            addResponse(responses, "400", annotation.badRequest)

            // 401 Unauthorized
            addResponse(responses, "401", annotation.unauthorized)

            // 403 Forbidden
            addResponse(responses, "403", annotation.forbidden)

            // 404 Not Found (optional)
            if (annotation.notFound.isNotEmpty()) {
                addResponse(responses, "404", annotation.notFound)
            }

            // 409 Conflict (optional)
            if (annotation.conflict.isNotEmpty()) {
                addResponse(responses, "409", annotation.conflict)
            }

            // 500 Internal Server Error
            addResponse(responses, "500", annotation.internal)

            operation.responses = responses
        }
        return operation
    }

    private fun addResponse(responses: ApiResponses, code: String, description: String) {
        if (description.isBlank()) return // Do not add response if description is empty or blank

        if (!responses.containsKey(code)) {
            val apiResponse = ApiResponse().description(description)
            if (code != "200") {
                val content = Content()
                val mediaType = MediaType()
                val schema = Schema<Any>()
                schema.`$ref` = "#/components/schemas/ApiErrorResponse"
                mediaType.schema = schema
                content.addMediaType("application/json", mediaType)
                apiResponse.content = content
            }
            responses.addApiResponse(code, apiResponse)
        } else {
            val existing = responses[code]
            if (existing?.description.isNullOrEmpty()) {
                existing?.description = description
            }
        }
    }
}
