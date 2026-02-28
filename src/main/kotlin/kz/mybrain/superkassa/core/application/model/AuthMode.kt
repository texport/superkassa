package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthMode {
    NONE,
    BEARER
}
