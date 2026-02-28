package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_COUNTERS
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OFD_SYNC
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_SYNC_ISSUE
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SYNC_BLOCKED
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.domain.model.CounterSnapshot
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/kkm")
@Tag(name = "Управление счетчиками ККМ", description = "Работа со счетчиками кассового аппарата")
class KkmCountersController(private val kkmService: KkmService) {

    /**
     * Получить текущие значения счетчиков ККМ.
     * 
     * Возвращает список всех счетчиков кассового аппарата с их текущими значениями.
     */
    @GetMapping("/{kkmId}/counters")
    @Operation(
        summary = "Получить счетчики ККМ",
        description = """
            Возвращает текущие значения всех счетчиков кассового аппарата.
            
            Счетчики ККМ - это внутренние счетчики кассового аппарата, которые отслеживают:
            - Количество операций (чеков, внесений/изъятий наличных)
            - Суммы операций
            - Количество смен
            - Номера последних документов (чеки, смены, отчеты)
            - И другие статистические данные
            
            Как использовать:
            1. Убедитесь, что ККМ зарегистрирована и находится в состоянии ACTIVE
            2. Передайте в теле запроса JSON с полем "pin" - ПИН-код пользователя
            3. ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            
            Что возвращается:
            - Список объектов CounterSnapshot, каждый содержит:
              * key: ключ счетчика (например, "RECEIPT_COUNT", "TOTAL_AMOUNT")
              * value: текущее значение счетчика
              * updatedAt: время последнего обновления счетчика
            
            Примеры счетчиков:
            - RECEIPT_COUNT: количество созданных чеков
            - TOTAL_AMOUNT: общая сумма всех операций
            - SHIFT_COUNT: количество открытых смен
            - LAST_RECEIPT_NO: номер последнего чека
            - LAST_SHIFT_NO: номер последней смены
            
            Используется для:
            - Мониторинга работы ККМ
            - Проверки корректности операций
            - Отладки и диагностики
            - Формирования отчетов
        """
    )
    @KkmApiResponses(
        ok = MSG_200_COUNTERS,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun listCounters(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): List<CounterSnapshot> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.listCounters(kkmId, pin)
    }

    /**
     * Синхронизировать счетчики ККМ с данными из ОФД.
     * 
     * Запрашивает актуальные значения счетчиков из ОФД и обновляет их в локальной базе данных.
     */
    @PostMapping("/{kkmId}/ofd/counters/sync")
    @Operation(
        summary = "Синхронизировать счетчики с ОФД",
        description = """
            Синхронизирует счетчики ККМ с данными из ОФД.
            
            Что делает метод:
            1. Отправляет запрос COMMAND_COUNTERS в ОФД для получения актуальных значений счетчиков
            2. Получает ответ от ОФД с текущими значениями всех счетчиков
            3. Обновляет значения счетчиков в локальной базе данных Superkassa
            4. Возвращает результат синхронизации
            
            Синхронизируемые счетчики:
            - non_nullable.OPERATION_SELL.sum - сумма всех операций продажи
            - non_nullable.OPERATION_SELL_RETURN.sum - сумма всех операций возврата продажи
            - non_nullable.OPERATION_BUY.sum - сумма всех операций покупки
            - non_nullable.OPERATION_BUY_RETURN.sum - сумма всех операций возврата покупки
            - shiftNumber - номер текущей смены
            
            Формат ключей счетчиков формируется из массива nonNullableSums в ответе ОФД:
            для каждой операции из массива создается счетчик с ключом "non_nullable.{OPERATION_TYPE}.sum",
            где OPERATION_TYPE - тип операции (OPERATION_SELL, OPERATION_SELL_RETURN, OPERATION_BUY, OPERATION_BUY_RETURN).
            
            Когда использовать:
            - После длительного периода автономной работы ККМ
            - При подозрении на расхождение данных между локальной БД и ОФД
            - После восстановления связи с ОФД
            - Перед формированием важных отчетов
            - При необходимости убедиться в актуальности данных
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Должна быть установлена связь с ОФД (проверяется автоматически)
            - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
            - ПИН-код должен соответствовать пользователю с правами ADMIN или CASHIER
            - Не должно быть активных операций, которые могут конфликтовать с синхронизацией
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            
            Что возвращается:
            - OfdCommandResult с полями:
              * status: статус операции (OK, FAILED)
              * fiscalSign: фискальный признак (если применимо)
              * errorMessage: описание ошибки (если операция не удалась)
              * responseBin: бинарные данные ответа от ОФД
            
            Ограничения:
            - Синхронизация может быть заблокирована, если есть активные операции с ККМ
            - При ошибке связи с ОФД операция будет отклонена
            - Слишком частые синхронизации могут привести к блокировке со стороны ОФД
            
            Важно:
            - Синхронизация счетчиков не влияет на текущую смену
            - Данные обновляются только в локальной БД, сам кассовый аппарат не изменяется
            - После успешной синхронизации рекомендуется проверить счетчики через GET /kkm/{kkmId}/counters
        """
    )
    @KkmApiResponses(
        ok = MSG_200_OFD_SYNC,
        forbidden = MSG_403_SYNC_ISSUE,
        notFound = MSG_404_KKM_NOT_FOUND,
        conflict = MSG_409_SYNC_BLOCKED
    )
    fun syncOfdCounters(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): OfdCommandResult {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.syncOfdCounters(kkmId, pin)
    }
}
