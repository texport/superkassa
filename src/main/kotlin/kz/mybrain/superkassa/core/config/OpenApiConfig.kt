package kz.mybrain.superkassa.core.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
                .info(
                        Info().title("Superkassa Core API")
                                .version("1.0")
                                .description(
                                        "API сервиса Superkassa Core для управления ККМ, сменами и чеками."
                                )
                )
    }

    /**
     * Security-схема для авторизации по PIN через заголовок Authorization.
     *
     * Схема:
     * - тип: apiKey
     * - заголовок: Authorization
     * - формат значения: "Bearer <pin>" или просто "<pin>" (не JWT)
     *
     * Схема автоматически подключается ко всем операциям, в которых присутствует
     * заголовок Authorization, за исключением публичных endpoint’ов без этого заголовка.
     */
    @Bean
    fun pinSecurityCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            val components = openApi.components ?: Components()

            // Регистрируем security-схему, если она ещё не зарегистрирована
            val schemeName = "PinAuthorization"
            val existingSchemes = components.securitySchemes ?: mutableMapOf()
            if (!existingSchemes.containsKey(schemeName)) {
                val pinScheme =
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description(
                            "PIN кассира/администратора. Формат: \"Bearer <pin>\" или \"<pin>\" (не JWT)."
                        )
                components.addSecuritySchemes(schemeName, pinScheme)
            }
            openApi.components = components

            val requirement = SecurityRequirement().addList(schemeName)

            // Подключаем security-схему ко всем операциям, где есть заголовок Authorization.
            openApi.paths?.forEach { (_, pathItem) ->
                pathItem.readOperations().forEach operationLoop@{ operation ->
                    val hasAuthHeader =
                        operation.parameters?.any { parameter ->
                            parameter.`in`.equals("header", ignoreCase = true) &&
                                parameter.name.equals("Authorization", ignoreCase = true)
                        } ?: false

                    if (!hasAuthHeader) return@operationLoop

                    val security =
                        (operation.security ?: mutableListOf()).apply {
                            if (none { it.containsKey(schemeName) }) {
                                add(requirement)
                            }
                        }
                    operation.security = security
                }
            }
        }
    }

    /**
     * Кастомайзер для установки порядка тегов в Swagger UI.
     * Теги будут отображаться в указанном порядке.
     * 
     * ВАЖНО: После изменения этого метода необходимо перезапустить приложение,
     * чтобы изменения вступили в силу.
     */
    @Bean
    fun tagsOrderCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            // Определяем желаемый порядок тегов
            val tagOrder = listOf(
                    "О Superkassa",
                    "Диагностика",
                    "Настройки Superkassa",
                    "Режим программирования ККМ",
                    "Управление кассирами и операторами ККМ",
                    "Ввод/Вывод из/в эксплуатацию ККМ",
                    "Управление ККМ",
                    "Диагностика ККМ",
                    "Управление счетчиками ККМ",
                    "Внесение и изъятие наличных денег",
                    "Чеки",
                    "Отчеты",
                    "Управление сменой(Z-Отчет) ККМ"
            )

            // Получаем существующие теги из всех операций
            val allTags = mutableSetOf<String>()
            openApi.paths?.forEach { (_, pathItem) ->
                pathItem.readOperations().forEach { operation ->
                    operation.tags?.forEach { tagName ->
                        allTags.add(tagName)
                    }
                }
            }

            // Получаем существующие теги из openApi.tags (если есть)
            val existingTags = openApi.tags?.toMutableList() ?: mutableListOf()
            
            // Добавляем теги из операций, которых нет в existingTags
            allTags.forEach { tagName ->
                if (existingTags.none { it.name == tagName }) {
                    existingTags.add(Tag().name(tagName))
                }
            }
            
            // Создаем карту существующих тегов по имени
            val tagsMap = existingTags.associateBy { it.name }
            
            // Создаем новый список тегов в нужном порядке
            val orderedTags = mutableListOf<Tag>()
            
            // Добавляем теги в указанном порядке
            for (tagName in tagOrder) {
                tagsMap[tagName]?.let { tag ->
                    orderedTags.add(tag)
                }
            }
            
            // Добавляем оставшиеся теги, которых нет в списке порядка (на случай, если появятся новые)
            for (tag in existingTags) {
                if (!tagOrder.contains(tag.name)) {
                    orderedTags.add(tag)
                }
            }
            
            // Устанавливаем упорядоченный список тегов
            openApi.tags = orderedTags
        }
    }
}
