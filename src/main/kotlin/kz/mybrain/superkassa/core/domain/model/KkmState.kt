package kz.mybrain.superkassa.core.domain.model

/**
 * Текущее состояние кассы.
 */
enum class KkmState {
    IDLE,
    ACTIVE,
    PROGRAMMING,
    BLOCKED
}
