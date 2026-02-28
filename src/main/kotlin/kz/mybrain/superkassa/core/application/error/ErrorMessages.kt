package kz.mybrain.superkassa.core.application.error

object ErrorMessages {
    fun badRequest(): String = "Bad request / некорректный запрос"

    fun invalidRequestBody(): String = "Invalid request body / некорректное тело запроса"

    fun internalError(): String = "Internal server error / внутренняя ошибка сервера"

    fun kkmNotFound(): String = "KKM not found / ККМ не найдена"

    fun kkmExists(): String = "KKM already exists / ККМ уже зарегистрирована"

    fun shiftNotOpen(): String = "Shift is not open / смена не открыта"

    fun shiftAlreadyOpen(): String = "Shift already open / смена уже открыта"

    fun shiftTooLong(): String = "Shift exceeds 24 hours / смена превышает 24 часа"

    fun systemTimeInvalid(): String = "System time invalid / неверное системное время"

    fun configMissing(): String = "Config missing / конфигурация отсутствует"

    fun configChangesForbidden(): String = "Config changes forbidden / изменения конфигурации запрещены"

    fun kkmIdRequired(): String = "kkmId required / kkmId обязателен"

    fun userIdRequired(): String = "userId required / userId обязателен"

    fun invalidIntParam(name: String): String = "$name must be an integer / $name должен быть числом"

    fun limitOutOfRange(): String = "limit must be between 1 and 500 / limit должен быть от 1 до 500"

    fun offsetOutOfRange(): String = "offset must be >= 0 / offset должен быть >= 0"

    fun ofdProviderUnknown(id: String): String = "Unknown OFD provider: $id / неизвестный ОФД: $id"

    fun ofdEnvironmentUnknown(env: String): String = "Unknown OFD environment: $env / неизвестная площадка ОФД: $env"

    fun ofdProviderRequired(): String = "OFD provider required / ОФД обязателен"

    fun ofdProviderTagInvalid(tag: String): String = "Invalid OFD tag: $tag / неверный тег ОФД: $tag"

    fun ofdTokenRequired(): String = "OFD token required / токен ОФД обязателен"

    fun ofdTokenInvalid(token: String): String = "Invalid OFD token: $token / неверный токен ОФД: $token"

    fun ofdServiceInfoRequired(): String = "OFD service info required / сервисные данные ОФД обязательны"

    fun kkmRegistrationRequired(): String = "KKM registration number required / регистрационный номер ККМ обязателен"

    fun kkmFactoryRequired(): String = "KKM factory number required / заводской номер ККМ обязателен"

    fun kkmSystemIdRequired(): String = "KKM systemId required / systemId ККМ обязателен"

    fun kkmSystemIdInvalid(systemId: String): String = "Invalid KKM systemId: $systemId / неверный systemId ККМ: $systemId"

    fun kkmSystemIdExists(systemId: String): String = "KKM systemId already exists: $systemId / systemId ККМ уже существует: $systemId"

    fun draftMismatch(): String = "Draft KKM details mismatch / реквизиты черновика ККМ не совпадают"

    fun userPinRequired(): String = "User PIN required / PIN пользователя обязателен"

    fun userNotFound(): String = "User not found or invalid PIN / пользователь не найден или неверный PIN"

    fun userForbidden(): String = "Operation forbidden for role / у пользователя нет прав на операцию"

    fun userNameRequired(): String = "User name required / имя пользователя обязательно"

    fun userRoleRequired(role: kz.mybrain.superkassa.core.domain.model.UserRole): String =
        "At least one ${role.name} required / нужен хотя бы один ${role.name}"

    fun userPinConflict(): String = "User PIN already exists / PIN уже используется"

    fun userUpdateEmpty(): String = "User update requires at least one field / нужно передать хотя бы одно поле"

    fun draftUpdateEmpty(): String = "Draft update requires at least one field / нужно передать хотя бы одно поле"

    fun ofdPairRequired(): String = "ofdId and ofdEnvironment must be provided together / ofdId и ofdEnvironment должны быть вместе"

    fun kkmNotDraft(): String = "KKM is not draft / ККМ не черновик"

    fun kkmDraftNotIdle(): String = "Draft KKM must be IDLE / черновик ККМ должен быть в статусе IDLE"

    fun kkmDeleteRequiresProgramming(): String = "KKM must be in PROGRAMMING / ККМ должна быть в режиме PROGRAMMING"

    fun kkmDeleteShiftOpen(): String = "Shift is open / смена не закрыта"

    fun kkmDeleteQueueNotEmpty(): String = "Queue is not empty / очередь не пустая"

    fun kkmSyncShiftOpen(): String = "Shift is open / смена не закрыта"

    fun kkmSyncQueueNotEmpty(): String = "Queue is not empty / очередь не пустая"

    fun kkmAutonomousTooLong(): String =
        "Autonomous mode exceeds 72 hours / автономный режим превышает 72 часа"

    fun kkmBlocked(): String =
        "KKM is blocked by OFD instruction / ККМ заблокирована по требованию ОФД"

    fun kkmSettingsUpdateEmpty(): String =
        "Settings update requires at least one field / нужно передать хотя бы одно поле"

    fun kkmSettingsRequiresProgramming(): String =
        "KKM must be in PROGRAMMING / ККМ должна быть в режиме PROGRAMMING"

    fun kkmInProgramming(): String = "KKM is in programming mode / ККМ в режиме программирования"

    fun unauthorized(): String = "Unauthorized / не авторизован"

    fun ofdRequestFailed(details: String?): String {
        return if (details.isNullOrBlank()) {
            "OFD request failed / ошибка ОФД"
        } else {
            "OFD request failed / ошибка ОФД: $details"
        }
    }

    fun userRoleInvalid(role: String): String = "Invalid user role in database: $role / неверная роль пользователя в БД: $role"

    fun shiftStatusInvalid(status: String): String = "Invalid shift status in database: $status / неверный статус смены в БД: $status"

    fun invalidBase64Format(): String = "Invalid Base64 format in database / неверный формат Base64 в БД"

    fun measureUnitCodeInvalid(code: String): String =
        "Invalid measure unit code: $code. Use OKEI codes (e.g. 796, 116). See GET /units-of-measurement / неверный код единицы измерения: $code"

    fun measureUnitNotFound(code: String): String =
        "Unit of measurement not found: $code / единица измерения не найдена: $code"

    fun documentNotFound(): String = "Document not found or receipt content unavailable / документ не найден или содержимое чека недоступно"
}
