package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OFD_AUTH
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OFD_INFO
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OFD_PING
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.model.OfdAuthInfoResponse
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.application.service.KkmService
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для диагностики ККМ и проверки связи с ОФД.
 * Отвечает за проверку доступности ОФД, получение информации и авторизационных данных.
 */
@RestController
@RequestMapping("/kkm/{kkmId}/ofd")
@Tag(name = "Диагностика ККМ", description = "Диагностика ККМ и проверка связи с ОФД")
class KkmDiagnosticsController(private val kkmService: KkmService) {

    /**
     * Получить информацию о ККМ от ОФД.
     * 
     * Запрашивает актуальную информацию о ККМ напрямую из системы ОФД.
     */
    @GetMapping("/info")
    @Operation(
        summary = "Получить информацию от ОФД",
        description = """
            Запрашивает актуальную информацию о ККМ из системы ОФД через команду COMMAND_INFO.
            
            Что делает метод:
            - Отправляет запрос COMMAND_INFO в ОФД
            - Получает актуальную информацию о ККМ из системы ОФД
            - Возвращает данные без обновления локальной базы данных
            
            Когда использовать:
            - Для проверки актуальности данных в ОФД
            - При диагностике расхождений между локальными данными и данными ОФД
            - Для получения информации о ККМ без изменения локальной БД
            - Перед синхронизацией для просмотра данных в ОФД
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            
            Что возвращается:
            - OfdCommandResult с полями:
              * status: статус операции (OK, FAILED)
              * responseBin: бинарные данные ответа от ОФД с информацией о ККМ
              * errorMessage: описание ошибки (если запрос не удался)
            
            Важно:
            - Метод не требует авторизации (публичный доступ)
            - Данные не обновляются в локальной БД автоматически
            - Для обновления локальных данных используйте POST /kkm/{kkmId}/ofd/sync
            - Информация может отличаться от данных в локальной БД
        """
    )
    @KkmApiResponses(ok = MSG_200_OFD_INFO, notFound = MSG_404_KKM_NOT_FOUND)
    fun getOfdInfo(@PathVariable kkmId: String): OfdCommandResult {
        return kkmService.getOfdInfo(kkmId)
    }

    /**
     * Проверить доступность и связь с ОФД для конкретной ККМ.
     * 
     * Отправляет тестовую команду COMMAND_SYSTEM в ОФД для проверки связи.
     */
    @GetMapping("/ping")
    @Operation(
        summary = "Проверить связь с ОФД",
        description = """
            Проверяет доступность и работоспособность связи с ОФД для указанной ККМ.
            
            Что делает метод:
            - Отправляет команду COMMAND_SYSTEM в ОФД
            - Проверяет возможность доставки команды и получения ответа
            - Возвращает результат проверки связи
            
            Когда использовать:
            - При диагностике проблем с ОФД
            - Перед важными операциями для проверки доступности ОФД
            - Для мониторинга состояния связи с ОФД
            - После настройки или изменения параметров ОФД
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            
            Что возвращается:
            - OfdCommandResult с полями:
              * status: статус операции (OK - связь работает, FAILED - проблемы с связью)
              * errorMessage: описание ошибки (если связь не работает)
              * responseBin: бинарные данные ответа от ОФД
            
            Важно:
            - Метод не требует авторизации (публичный доступ)
            - Проверка связи не влияет на работу ККМ
            - При недоступности ОФД операции с ККМ могут работать в автономном режиме
        """
    )
    @KkmApiResponses(ok = MSG_200_OFD_PING, notFound = MSG_404_KKM_NOT_FOUND)
    fun checkOfdConnection(@PathVariable kkmId: String): OfdCommandResult {
        return kkmService.checkOfdConnection(kkmId)
    }

    /**
     * Получить данные авторизации для работы с ОФД.
     * 
     * Возвращает токен доступа и номер следующего запроса для отладки и диагностики.
     */
    @PostMapping("/auth")
    @Operation(
        summary = "Получить данные авторизации ОФД",
        description = """
            Возвращает информацию об авторизации для работы с ОФД.
            
            Что возвращается:
            - Токен доступа к ОФД (зашифрованный)
            - Номер следующего запроса (reqNum) для последовательности команд
            - Информация о провайдере и окружении ОФД
            
            Когда использовать:
            - Для отладки проблем с авторизацией в ОФД
            - При диагностике ошибок взаимодействия с ОФД
            - Для проверки корректности настроек ОФД
            - При разработке и тестировании интеграции
            
            Требования:
            - ККМ должна быть зарегистрирована
            - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
            - ПИН-код должен соответствовать пользователю с правами ADMIN или CASHIER
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            
            Что возвращается:
            - OfdAuthInfoResponse с полями:
              * token: зашифрованный токен доступа к ОФД
              * reqNum: номер следующего запроса
              * ofdId: идентификатор провайдера ОФД
              * ofdEnvironment: окружение ОФД (TEST/PROD)
            
            Важно:
            - Данные авторизации чувствительны, не передавайте их третьим лицам
            - Токен автоматически обновляется системой при необходимости
            - Используйте этот метод только для диагностики, не для ручной работы с ОФД
        """
    )
    @KkmApiResponses(
        ok = MSG_200_OFD_AUTH,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun getOfdAuthInfo(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): OfdAuthInfoResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.getOfdAuthInfo(kkmId, pin)
    }
}
