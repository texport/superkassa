package kz.mybrain.superkassa.core.domain.model

/**
 * Тип команды ОФД.
 */
enum class OfdCommandType(val value: String) {
    TICKET("COMMAND_TICKET"),
    SYSTEM("COMMAND_SYSTEM"),
    INFO("COMMAND_INFO"),
    MONEY_PLACEMENT("COMMAND_MONEY_PLACEMENT"),
    REPORT("COMMAND_REPORT"),
    CLOSE_SHIFT("COMMAND_CLOSE_SHIFT");

    companion object {
        fun fromString(value: String): OfdCommandType {
            return values().find { it.value == value }
                ?: throw IllegalArgumentException("Unknown OFD command type: $value")
        }
    }
}
