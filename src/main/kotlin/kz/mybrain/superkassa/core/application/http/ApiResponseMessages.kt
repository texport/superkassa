package kz.mybrain.superkassa.core.application.http

object ApiResponseMessages {
    // 200 OK
    const val MSG_200_KKM_INFO = "Информация о ККМ"
    const val MSG_200_KKM_LIST = "Список ККМ"
    const val MSG_200_KKM_DELETED = "ККМ успешно удалена"
    const val MSG_200_COUNTERS = "Счетчики ККМ"
    const val MSG_200_SHIFT_OPENED = "Смена успешно открыта"
    const val MSG_200_SHIFT_CLOSE_ACCEPTED = "Запрос на закрытие смены принят"
    const val MSG_200_SHIFT_DOCUMENTS = "Список документов смены"
    const val MSG_200_SHIFTS_LIST = "Список смен"
    const val MSG_200_RECEIPT_CREATED = "Чек создан (или получен идемпотентный ответ)"
    const val MSG_200_REPORT_ACCEPTED = "Запрос на отчет принят"
    const val MSG_200_CASH_IN = "Операция внесения выполнена"
    const val MSG_200_CASH_OUT = "Операция изъятия выполнена"
    const val MSG_200_DRAFT_CREATED = "Черновик создан"
    const val MSG_200_KKM_INIT = "ККМ успешно инициализирована"
    const val MSG_200_DRAFT_FISCALIZED = "Черновик успешно фискализирован"
    const val MSG_200_DRAFT_UPDATED = "Черновик обновлен"
    const val MSG_200_OFD_PING = "Запрос выполнен (см. статус в ответе)"
    const val MSG_200_OFD_AUTH = "Данные авторизации получены"
    const val MSG_200_OFD_TOKEN_UPDATED = "Токен обновлен"
    const val MSG_200_OFD_INFO = "Информация получена"
    const val MSG_200_OFD_SYNC = "Синхронизация выполнена"
    const val MSG_200_SETTINGS_UPDATED = "Настройки обновлены"
    const val MSG_200_PROGRAMMING_ENTER = "ККМ переведена в режим программирования"
    const val MSG_200_PROGRAMMING_EXIT = "ККМ вышла из режима программирования"
    const val MSG_200_USERS_LIST = "Список пользователей"
    const val MSG_200_USER_CREATED = "Пользователь создан"
    const val MSG_200_USER_UPDATED = "Пользователь обновлен"
    const val MSG_200_USER_DELETED = "Пользователь удален"
    const val MSG_200_OK = "Сервис работает"
    const val MSG_200_VERSION = "Информация о версии"
    const val MSG_200_SETTINGS = "Текущие настройки"
    const val MSG_200_UNITS_LIST = "Список единиц измерения"
    const val MSG_200_UNIT_FOUND = "Единица измерения"
    const val MSG_200_RECEIPT_HTML = "Чек в формате HTML"
    const val MSG_200_RECEIPT_PDF = "Чек в формате PDF"
    const val MSG_200_RECEIPT_IMAGE = "Чек в формате изображения"
    const val MSG_200_PRINT_HTML = "Печатная форма (HTML)"
    const val MSG_200_DELIVERY_RETRY = "Результаты повторной отправки по каналам"

    // 400 Bad Request
    const val MSG_400_VALIDATION = "Ошибка валидации"
    const val MSG_400_BAD_STATUS_OR_SHIFT = "Некорректный статус ККМ или есть активная смена"
    const val MSG_400_NOT_PROGRAMMING_MODE = "ККМ не в режиме программирования или ошибка валидации"
    const val MSG_400_NOT_IDLE = "ККМ не в статусе IDLE или ошибка валидации"
    const val MSG_400_BAD_REQUEST = "Некорректный запрос"

    // 401 Unauthorized
    const val MSG_401_UNAUTHORIZED = "Необходима авторизация"

    // 403 Forbidden
    const val MSG_403_FORBIDDEN = "Недостаточно прав"
    const val MSG_403_SETTINGS_FROZEN = "Изменение настроек запрещено"
    const val MSG_403_SYNC_ISSUE = "Недостаточно прав или таймаут синхронизации"

    // 404 Not Found
    const val MSG_404_NOT_FOUND = "Ресурс не найден"
    const val MSG_404_KKM_NOT_FOUND = "ККМ не найдена"
    const val MSG_404_DRAFT_NOT_FOUND = "Черновик не найден"
    const val MSG_404_USER_OR_KKM_NOT_FOUND = "Пользователь или ККМ не найдены"
    const val MSG_404_UNIT_NOT_FOUND = "Единица измерения не найдена"
    const val MSG_404_DOCUMENT_NOT_FOUND = "Документ не найден или содержимое чека недоступно"

    // 409 Conflict
    const val MSG_409_CONFLICT = "Конфликт состояния"
    const val MSG_409_SHIFT_OPEN = "Смена уже открыта"
    const val MSG_409_SHIFT_NOT_OPEN = "Смена не открыта"
    const val MSG_409_PIN_BUSY = "ПИН-код пользователя уже занят"
    const val MSG_409_DELETE_BLOCKED = "Нельзя удалить: смена открыта или очередь не пуста"
    const val MSG_409_SYNC_BLOCKED = "Синхронизация невозможна: смена открыта или очередь не пуста"

    // 422 Unprocessable Entity
    const val MSG_422_UNPROCESSABLE_ENTITY = "Ошибка обработки сущности"

    // 500 Internal Server Error
    const val MSG_500_INTERNAL = "Внутренняя ошибка сервера"
    const val MSG_503_SERVICE_UNAVAILABLE = "Сервис временно недоступен"
}
