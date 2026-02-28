package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_RECEIPT_CREATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_REQUEST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SHIFT_NOT_OPEN
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.model.receipt.*
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.model.ReceiptResult
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для работы с чеками.
 * Отвечает за создание чеков различных типов операций.
 */
@RestController
@RequestMapping("/kkm/{kkmId}/receipt")
@Tag(name = "Чеки", description = "Создание чеков различных типов операций")
class ReceiptsController(private val kkmService: KkmService) {

    /**
     * Создать чек продажи.
     */
    @PostMapping("/sell")
    @Operation(
        summary = "Продажа",
        description = """
            Создает чек продажи (приходный чек).
            
            Что делает метод:
            - Создает чек продажи с указанными позициями и способами оплаты
            - Отправляет данные в ОФД
            - Возвращает результат создания чека с фискальными данными
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Смена должна быть открыта
            - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
            - ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            - idempotencyKey: Уникальный ключ для предотвращения дублирования операций
            - items: Список позиций чека. Для каждой позиции:
              * barcode (опционально) — штрихкод товара;
              * name — наименование (3–128 символов, без обобщённых значений вроде "Товар", "Продукты");
              * price — цена за единицу (Double);
              * quantity — количество (> 0);
              * sum — итоговая сумма позиции (Double);
              * discountPercent / discountSum (опционально, взаимоисключающие) — скидка на позицию в процентах или суммой;
              * markupPercent / markupSum (опционально, взаимоисключающие) — наценка на позицию в процентах или суммой;
              * vatGroup (опционально) — группа НДС: NO_VAT, VAT_0, VAT_16.
            - discountPercent / discountSum (опционально, взаимоисключающие): скидка на весь чек в процентах либо суммой.
            - markupPercent / markupSum (опционально, взаимоисключающие): наценка на весь чек в процентах либо суммой.
            - payments: Список способов оплаты; для каждой оплаты:
              * type — тип оплаты: CASH, CARD, ELECTRONIC;
              * sum — сумма (Double).
            - taken (опционально): Получено от покупателя в тенге (Double)
            - change (опционально): Сдача в тенге (Double)
            - total: Общая сумма чека в тенге (Double)
            
            Что возвращается:
            - ReceiptResult с полями:
              * receiptNumber: номер созданного чека
              * fiscalSign: фискальный признак
              * fiscalDocumentNumber: номер фискального документа
              * status: статус операции
            
            Важно:
            - Все суммы передаются в тенге как Double (например, 1234.56)
            - Система автоматически преобразует суммы в формат Money (bills/coins)
            - Используйте idempotencyKey для предотвращения дублирования при повторных запросах
        """
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_CREATED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createSellReceipt(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: ReceiptSellRequest
    ): ReceiptResult {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val receiptRequest = ReceiptMapper.toReceiptRequest(
            kkmId = kkmId,
            pin = pin,
            operation = ReceiptOperationType.SELL,
            idempotencyKey = request.idempotencyKey,
            items = request.items,
            discountPercent = request.discountPercent,
            discountSum = request.discountSum,
            markupPercent = request.markupPercent,
            markupSum = request.markupSum,
            payments = request.payments,
            taken = request.taken,
            change = request.change,
            defaultVatGroup = request.defaultVatGroup
        )
        return kkmService.createReceipt(receiptRequest)
    }

    /**
     * Создать чек возврата продажи.
     */
    @PostMapping("/sell-return")
    @Operation(
        summary = "Возврат продажи",
        description = """
            Создает чек возврата продажи (возврат приходного чека).
            
            Что делает метод:
            - Создает чек возврата продажи с указанными позициями и способами оплаты
            - Отправляет данные в ОФД
            - Возвращает результат создания чека с фискальными данными
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Смена должна быть открыта
            - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
            - ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            - idempotencyKey: Уникальный ключ для предотвращения дублирования операций
            - items: Список позиций чека (структура аналогична методу продажи /sell).
            - discountPercent / discountSum (опционально, взаимоисключающие): скидка на весь чек.
            - markupPercent / markupSum (опционально, взаимоисключающие): наценка на весь чек.
            - payments: Список способов оплаты (type: CASH/CARD/ELECTRONIC, sum).
            - taken (опционально): Получено от покупателя в тенге (Double)
            - change (опционально): Сдача в тенге (Double)
            - total: Общая сумма чека в тенге (Double)
            - parentTicket (опционально): данные исходного чека для возврата (номер, дата/время, РНМ ККМ, сумма, признак офлайн).
            
            Что возвращается:
            - ReceiptResult с полями:
              * receiptNumber: номер созданного чека
              * fiscalSign: фискальный признак
              * fiscalDocumentNumber: номер фискального документа
              * status: статус операции
            
            Важно:
            - Все суммы передаются в тенге как Double (например, 1234.56)
            - Система автоматически преобразует суммы в формат Money (bills/coins)
            - Используйте idempotencyKey для предотвращения дублирования при повторных запросах
        """
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_CREATED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createSellReturnReceipt(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: ReceiptSellReturnRequest
    ): ReceiptResult {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val receiptRequest = ReceiptMapper.toReceiptRequest(
            kkmId = kkmId,
            pin = pin,
            operation = ReceiptOperationType.SELL_RETURN,
            idempotencyKey = request.idempotencyKey,
            items = request.items,
            discountPercent = request.discountPercent,
            discountSum = request.discountSum,
            markupPercent = request.markupPercent,
            markupSum = request.markupSum,
            payments = request.payments,
            taken = request.taken,
            change = request.change,
            parentTicket = request.parentTicket,
            defaultVatGroup = request.defaultVatGroup
        )
        return kkmService.createReceipt(receiptRequest)
    }

    /**
     * Создать чек покупки.
     */
    @PostMapping("/buy")
    @Operation(
        summary = "Покупка",
        description = """
            Создает чек покупки (расходный чек).
            
            Что делает метод:
            - Создает чек покупки с указанными позициями и способами оплаты
            - Отправляет данные в ОФД
            - Возвращает результат создания чека с фискальными данными
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Смена должна быть открыта
            - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
            - ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            - idempotencyKey: Уникальный ключ для предотвращения дублирования операций
            - items: Список позиций чека (структура аналогична методу продажи /sell).
            - discountPercent / discountSum (опционально, взаимоисключающие): скидка на весь чек.
            - markupPercent / markupSum (опционально, взаимоисключающие): наценка на весь чек.
            - payments: Список способов оплаты (type: CASH/CARD/ELECTRONIC, sum).
            - taken (опционально): Получено от покупателя в тенге (Double)
            - change (опционально): Сдача в тенге (Double)
            - total: Общая сумма чека в тенге (Double)
            
            Что возвращается:
            - ReceiptResult с полями:
              * receiptNumber: номер созданного чека
              * fiscalSign: фискальный признак
              * fiscalDocumentNumber: номер фискального документа
              * status: статус операции
            
            Важно:
            - Все суммы передаются в тенге как Double (например, 1234.56)
            - Система автоматически преобразует суммы в формат Money (bills/coins)
            - Используйте idempotencyKey для предотвращения дублирования при повторных запросах
        """
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_CREATED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createBuyReceipt(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: ReceiptBuyRequest
    ): ReceiptResult {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val receiptRequest = ReceiptMapper.toReceiptRequest(
            kkmId = kkmId,
            pin = pin,
            operation = ReceiptOperationType.BUY,
            idempotencyKey = request.idempotencyKey,
            items = request.items,
            discountPercent = request.discountPercent,
            discountSum = request.discountSum,
            markupPercent = request.markupPercent,
            markupSum = request.markupSum,
            payments = request.payments,
            taken = request.taken,
            change = request.change,
            defaultVatGroup = request.defaultVatGroup
        )
        return kkmService.createReceipt(receiptRequest)
    }

    /**
     * Создать чек возврата покупки.
     */
    @PostMapping("/buy-return")
    @Operation(
        summary = "Возврат покупки",
        description = """
            Создает чек возврата покупки (возврат расходного чека).
            
            Что делает метод:
            - Создает чек возврата покупки с указанными позициями и способами оплаты
            - Отправляет данные в ОФД
            - Возвращает результат создания чека с фискальными данными
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Смена должна быть открыта
            - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
            - ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN
            
            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            - idempotencyKey: Уникальный ключ для предотвращения дублирования операций
            - items: Список позиций чека (структура аналогична методу продажи /sell).
            - discountPercent / discountSum (опционально, взаимоисключающие): скидка на весь чек.
            - markupPercent / markupSum (опционально, взаимоисключающие): наценка на весь чек.
            - payments: Список способов оплаты (type: CASH/CARD/ELECTRONIC, sum).
            - taken (опционально): Получено от покупателя в тенге (Double)
            - change (опционально): Сдача в тенге (Double)
            - total: Общая сумма чека в тенге (Double)
            
            Что возвращается:
            - ReceiptResult с полями:
              * receiptNumber: номер созданного чека
              * fiscalSign: фискальный признак
              * fiscalDocumentNumber: номер фискального документа
              * status: статус операции
            
            Важно:
            - Все суммы передаются в тенге как Double (например, 1234.56)
            - Система автоматически преобразует суммы в формат Money (bills/coins)
            - Используйте idempotencyKey для предотвращения дублирования при повторных запросах
        """
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_CREATED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createBuyReturnReceipt(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: ReceiptBuyReturnRequest
    ): ReceiptResult {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val receiptRequest = ReceiptMapper.toReceiptRequest(
            kkmId = kkmId,
            pin = pin,
            operation = ReceiptOperationType.BUY_RETURN,
            idempotencyKey = request.idempotencyKey,
            items = request.items,
            discountPercent = request.discountPercent,
            discountSum = request.discountSum,
            markupPercent = request.markupPercent,
            markupSum = request.markupSum,
            payments = request.payments,
            taken = request.taken,
            change = request.change,
            parentTicket = request.parentTicket,
            defaultVatGroup = request.defaultVatGroup
        )
        return kkmService.createReceipt(receiptRequest)
    }
}
