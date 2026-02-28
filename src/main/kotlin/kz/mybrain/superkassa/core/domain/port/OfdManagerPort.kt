package kz.mybrain.superkassa.core.domain.port

import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult

/**
 * Порт отправки команд в ОФД.
 */
interface OfdManagerPort {
    fun send(command: OfdCommandRequest): OfdCommandResult
}
