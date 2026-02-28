package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable

/**
 * Режим работы кассового ядра.
 */
@Serializable
enum class CoreMode {
    DESKTOP,
    SERVER
}
