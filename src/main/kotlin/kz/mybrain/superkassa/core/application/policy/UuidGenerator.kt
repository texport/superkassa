package kz.mybrain.superkassa.core.application.policy

import kz.mybrain.superkassa.core.domain.port.IdGenerator
import java.util.UUID

/**
 * UUID-генератор идентификаторов.
 */
object UuidGenerator : IdGenerator {
    override fun nextId(): String = UUID.randomUUID().toString()
}
