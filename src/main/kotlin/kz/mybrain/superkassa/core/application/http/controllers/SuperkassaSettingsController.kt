package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.error.SettingsFrozenException
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SETTINGS
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SETTINGS_UPDATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_SETTINGS_FROZEN
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.application.service.FileCoreSettingsRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/")
@Tag(name = "Настройки Superkassa", description = "Управление системными настройками Superkassa.")
class SuperkassaSettingsController(
    private val settingsRepository: FileCoreSettingsRepository,
    private val coreSettings: CoreSettings
) {

    /**
     * Получение текущих настроек ядра Superkassa.
     * 
     * Возвращает полную конфигурацию системы, включая:
     * - Режим работы (DESKTOP, SERVER)
     * - Настройки хранилища данных (тип БД, JDBC URL)
     * - Идентификатор узла
     * - Версию протокола ОФД
     * - Каналы доставки
     * - Таймауты ОФД
     * - Флаг разрешения изменений настроек
     */
    @GetMapping("/settings")
    @Operation(
        summary = "Получить текущие настройки.",
        description = """
            Возвращает текущую конфигурацию системы Superkassa Core.
            
            Настройки загружаются из файла config/core-settings.json. Если файл не существует,
            возвращаются настройки по умолчанию.
            
            Параметры конфигурации:
            
            **mode** - Режим работы системы:
            - DESKTOP: Десктопное приложение для локального использования. Использует SQLite как БД
              и in-memory очередь. Подходит для небольшого количества ККМ (до нескольких тысяч).
            - SERVER: Серверный режим для кластера. Использует PostgreSQL или MySQL как БД и JDBC-очередь
              для распределенной обработки. Подходит для больших масштабов (десятки и сотни тысяч ККМ).
              Требует настройки внешней БД в параметре storage.
            
            **storage** - Настройки базы данных:
            - engine: Тип СУБД. Для DESKTOP режима должен быть "SQLITE". Для SERVER режима - "POSTGRESQL"
              или "MYSQL".
            - jdbcUrl: JDBC URL для подключения к БД. Для SQLite: "jdbc:sqlite:<путь к файлу>".
              Для PostgreSQL: "jdbc:postgresql://<host>:<port>/<database>".
              Для MySQL: "jdbc:mysql://<host>:<port>/<database>".
            - user: Имя пользователя БД (опционально для SQLite, обязательно для PostgreSQL/MySQL).
            - password: Пароль БД (опционально для SQLite, обязательно для PostgreSQL/MySQL).
            
            **allowChanges** - Разрешены ли изменения настроек через API:
            - true: Настройки можно изменять через PUT /settings
            - false: Изменения через API запрещены (только через редактирование файла)
            
            **nodeId** - Уникальный идентификатор узла в кластере:
            - Используется в SERVER режиме для идентификации узла при распределенной обработке очереди.
            - Должен быть уникальным для каждого узла в кластере.
            - По умолчанию: "node-1"
            
            **ofdProtocolVersion** - Версия протокола ОФД:
            - Определяет версию протокола для обмена с ОФД.
            - Текущая версия: "203"
            - Изменение может потребоваться при обновлении протокола ОФД.
            
            **deliveryChannels** - Каналы доставки документов:
            - Список каналов, через которые доставляются документы (например, ["PRINT"]).
            - PRINT: Печать документов на принтере.
            
            **ofdTimeoutSeconds** - Общее время на обработку транзакции (протокол ОФД п. 5):
            - Не менее 5 сек. По умолчанию: 30 сек.
            - При превышении — соединение разрывается, устройство переходит в OFFLINE.
            
            **ofdReconnectIntervalSeconds** - Интервал между попытками восстановления связи (протокол ОФД п. 5):
            - Не менее 60 сек. По умолчанию: 60 сек.
            
            Используется для просмотра текущей конфигурации системы.
        """
    )
    @KkmApiResponses(ok = MSG_200_SETTINGS)
    fun getSettings(): CoreSettings {
        return settingsRepository.loadOrCreate(coreSettings)
    }

    /**
     * Обновление настроек ядра Superkassa.
     * 
     * Позволяет изменить конфигурацию системы через API.
     * Настройки сохраняются в файл config/core-settings.json.
     * 
     * Ограничения:
     * - Изменения разрешены только если allowChanges=true в текущих настройках
     * - При allowChanges=false возвращается ошибка 403 Forbidden
     * 
     * После обновления настроек может потребоваться перезапуск приложения
     * для применения некоторых изменений (например, настройки БД).
     */
    @PutMapping("/settings")
    @Operation(
        summary = "Обновить настройки.",
        description = """
            Обновляет конфигурацию системы Superkassa Core.
            
            Требования:
            - В текущих настройках должно быть установлено allowChanges=true
            - Если allowChanges=false, операция будет отклонена с ошибкой 403 Forbidden
            
            Процесс обновления:
            1. Проверяется флаг allowChanges
            2. Новые настройки валидируются
            3. Настройки сохраняются в файл config/core-settings.json
            4. Возвращаются обновленные настройки
            
            Правила заполнения настроек:
            
            **Режим работы (mode):**
            - DESKTOP: Используйте для локального использования. Обязательно укажите storage.engine="SQLITE"
              и корректный путь к файлу SQLite в storage.jdbcUrl.
            - SERVER: Используйте для кластера. Обязательно настройте PostgreSQL или MySQL:
              * storage.engine: "POSTGRESQL" или "MYSQL"
              * storage.jdbcUrl: полный JDBC URL с хостом, портом и именем БД
              * storage.user и storage.password: учетные данные для подключения
              * nodeId: уникальный идентификатор для каждого узла в кластере
            
            **Настройки хранилища (storage):**
            - Для DESKTOP: engine="SQLITE", jdbcUrl="jdbc:sqlite:<путь>", user и password не требуются.
            - Для SERVER: engine="POSTGRESQL" или "MYSQL", укажите полный JDBC URL, user и password.
              БД должна быть создана заранее, миграции выполнятся автоматически при первом запуске.
            
            **Важные замечания:**
            - Изменение режима с DESKTOP на SERVER требует настройки внешней БД и может потребовать
              миграции данных из SQLite.
            - Изменение настроек БД (engine, jdbcUrl, user, password) требует перезапуска приложения.
            - Изменение nodeId в SERVER режиме может повлиять на распределение задач в кластере.
            - Изменение ofdProtocolVersion должно соответствовать версии протокола, поддерживаемой ОФД.
            - Изменение allowChanges на false предотвратит дальнейшие изменения через API.
            
            Рекомендуется делать резервную копию настроек перед изменением.
        """
    )
    @KkmApiResponses(ok = MSG_200_SETTINGS_UPDATED, forbidden = MSG_403_SETTINGS_FROZEN)
    fun updateSettings(@RequestBody @Valid newSettings: CoreSettings): CoreSettings {
        if (!coreSettings.allowChanges) {
            throw SettingsFrozenException(MSG_403_SETTINGS_FROZEN)
        }
        settingsRepository.save(newSettings)
        return newSettings
    }
}
