package kz.mybrain.superkassa.core.domain.port

/**
 * Генератор идентификаторов.
 */
fun interface IdGenerator {
    fun nextId(): String
}
