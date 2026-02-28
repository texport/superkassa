package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SHIFT_CLOSE_ACCEPTED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SHIFTS_LIST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_DELIVERY_RETRY
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_PRINT_HTML
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_RECEIPT_HTML
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_RECEIPT_PDF
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_RECEIPT_IMAGE
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SHIFT_DOCUMENTS
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SHIFT_OPENED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_REQUEST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_DOCUMENT_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SHIFT_NOT_OPEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SHIFT_OPEN
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.model.CloseShiftRequest
import kz.mybrain.superkassa.core.application.model.DeliveryRetryItemResponse
import kz.mybrain.superkassa.core.application.model.DeliveryRetryResponse
import kz.mybrain.superkassa.core.application.model.PinRequest
import kz.mybrain.superkassa.core.application.model.PrintDocumentType
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.ReportResult
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/kkm")
@Tag(name = "Управление сменой(Z-Отчет) ККМ", description = "Операции со сменами, чеками и отчетами")
class KkmController(private val kkmService: KkmService) {

        /** Открыть новую смену. */
        @PostMapping("/{kkmId}/shift/open")
        @Operation(
            summary = "Открыть смену",
            description = """
                Открывает новую смену для работы с ККМ.
                
                Что делает метод:
                - Создает новую смену для указанной ККМ
                - Инициализирует счетчики смены
                - Возвращает информацию об открытой смене
                
                Требования:
                - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
                - Предыдущая смена должна быть закрыта (если была открыта)
                - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
                - ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN
                
                Что передавать:
                - kkmId (в пути): Идентификатор ККМ
                - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
                - Тело запроса может быть пустым (все параметры берутся из пути и заголовка)
                
                Что возвращается:
                - ShiftInfo с полями:
                  * shiftId: идентификатор смены
                  * shiftNumber: номер смены
                  * openedAt: время открытия смены
                  * kkmId: идентификатор ККМ
                
                Важно:
                - После открытия смены можно создавать чеки и выполнять операции
                - Для закрытия смены используйте POST /kkm/{kkmId}/shift/close
                - Нельзя открыть новую смену, если предыдущая не закрыта
            """
        )
        @KkmApiResponses(
                ok = MSG_200_SHIFT_OPENED,
                forbidden = MSG_403_FORBIDDEN,
                conflict = MSG_409_SHIFT_OPEN,
                notFound = MSG_404_KKM_NOT_FOUND
        )
        fun openShift(
                @PathVariable kkmId: String,
                @RequestHeader("Authorization") authHeader: String?
        ): ShiftInfo {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                return kkmService.openShift(kkmId, pin)
        }

        /** Закрыть текущую смену и сформировать Z-отчет. */
        @PostMapping("/{kkmId}/shift/close")
        @Operation(
            summary = "Закрыть смену (Z-отчет)",
            description = """
                Закрывает текущую смену и создает Z-отчет.
                
                Что делает метод:
                - Закрывает текущую открытую смену
                - Формирует Z-отчет с итоговыми данными по смене
                - Отправляет данные в ОФД
                - Возвращает результат закрытия смены
                
                Z-отчет показывает:
                - Итоговые данные по смене
                - Общее количество и сумму операций
                - Статистику по операциям
                - После закрытия смены необходимо открыть новую смену для продолжения работы
                
                Требования:
                - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
                - Смена должна быть открыта
                - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
                - ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN
                
                Что передавать:
                - kkmId (в пути): Идентификатор ККМ
                - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
                - Тело запроса может быть пустым (все параметры берутся из пути и заголовка)
                
                Что возвращается:
                - ReportResult с полями:
                  * reportNumber: номер созданного Z-отчета
                  * fiscalSign: фискальный признак
                  * fiscalDocumentNumber: номер фискального документа
                  * status: статус операции
                
                Важно:
                - После закрытия смены необходимо открыть новую смену через POST /kkm/{kkmId}/shift/open
                - Z-отчет закрывает смену окончательно, после этого нельзя создавать чеки в закрытой смене
                - Тип отчета (Z_REPORT) определяется автоматически, не нужно указывать в запросе
            """
        )
        @KkmApiResponses(
                ok = MSG_200_SHIFT_CLOSE_ACCEPTED,
                badRequest = MSG_400_BAD_REQUEST,
                forbidden = MSG_403_FORBIDDEN,
                conflict = MSG_409_SHIFT_NOT_OPEN,
                notFound = MSG_404_KKM_NOT_FOUND
        )
        fun closeShift(
                @PathVariable kkmId: String,
                @RequestHeader("Authorization") authHeader: String?,
                @RequestBody @Valid request: CloseShiftRequest
        ): ReportResult {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                return kkmService.closeShift(kkmId, pin)
        }

        /**
         * Список смен по ККМ (постранично).
         * По нему можно получить количество и номера смен, затем для каждой смены запросить документы через GET .../shifts/{shiftId}/documents.
         */
        @GetMapping("/{kkmId}/shifts")
        @Operation(
            summary = "Список смен",
            description = "Возвращает смены по ККМ (id, номер смены, статус, время открытия/закрытия). По убыванию времени открытия."
        )
        @KkmApiResponses(ok = MSG_200_SHIFTS_LIST, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_KKM_NOT_FOUND)
        fun listShifts(
                @PathVariable kkmId: String,
                @RequestParam(defaultValue = "100") limit: Int,
                @RequestParam(defaultValue = "0") offset: Int,
                @RequestHeader("Authorization") authHeader: String?
        ): List<ShiftInfo> {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                return kkmService.listShifts(kkmId, limit, offset, pin)
        }

        /**
         * Список фискальных документов за смену.
         * Возвращает все документы смены: чеки (в т.ч. автономные), внесения, изъятия, отчёты.
         */
        @GetMapping("/{kkmId}/shifts/{shiftId}/documents")
        @Operation(
            summary = "Документы смены",
            description = """
                Возвращает список фискальных документов за указанную смену.
                Включает чеки (обычные и автономные), внесения/изъятия наличных, X/Z отчёты.
                Поля каждого документа: id, docType, docNo, shiftNo, createdAt, totalAmount,
                fiscalSign, autonomousSign, isAutonomous, ofdStatus, deliveredAt.
            """
        )
        @KkmApiResponses(ok = MSG_200_SHIFT_DOCUMENTS, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_KKM_NOT_FOUND)
        fun listShiftDocuments(
                @PathVariable kkmId: String,
                @PathVariable shiftId: String,
                @RequestParam(defaultValue = "100") limit: Int,
                @RequestParam(defaultValue = "0") offset: Int,
                @RequestHeader("Authorization") authHeader: String?
        ): List<FiscalDocumentSnapshot> {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                return kkmService.listShiftDocuments(kkmId, shiftId, limit, offset, pin)
        }

        /** Документы текущей открытой смены. */
        @GetMapping("/{kkmId}/shift/documents")
        @Operation(
            summary = "Документы текущей смены",
            description = "Список фискальных документов текущей открытой смены. 409 если смена не открыта."
        )
        @KkmApiResponses(ok = MSG_200_SHIFT_DOCUMENTS, forbidden = MSG_403_FORBIDDEN, conflict = MSG_409_SHIFT_NOT_OPEN, notFound = MSG_404_KKM_NOT_FOUND)
        fun listCurrentShiftDocuments(
                @PathVariable kkmId: String,
                @RequestParam(defaultValue = "100") limit: Int,
                @RequestParam(defaultValue = "0") offset: Int,
                @RequestHeader("Authorization") authHeader: String?
        ): List<FiscalDocumentSnapshot> {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                val shift = kkmService.getOpenShift(kkmId, pin)
                return kkmService.listShiftDocuments(kkmId, shift.id, limit, offset, pin)
        }

        /**
         * Список фискальных документов за период по дате создания (created_at).
         * from — начало периода (включительно), to — конец (исключительно), в миллисекундах с 01.01.1970 (epoch).
         */
        @GetMapping("/{kkmId}/documents")
        @Operation(
            summary = "Документы за период",
            description = "Список фискальных документов за период по времени создания. Параметры from, to — epoch millis."
        )
        @KkmApiResponses(ok = MSG_200_SHIFT_DOCUMENTS, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_KKM_NOT_FOUND)
        fun listDocumentsByPeriod(
                @PathVariable kkmId: String,
                @RequestParam from: Long,
                @RequestParam to: Long,
                @RequestParam(defaultValue = "100") limit: Int,
                @RequestParam(defaultValue = "0") offset: Int,
                @RequestHeader("Authorization") authHeader: String?
        ): List<FiscalDocumentSnapshot> {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                return kkmService.listFiscalDocumentsByPeriod(kkmId, from, to, limit, offset, pin)
        }

        /**
         * Универсальная печатная форма (HTML). Поддерживает все типы: DOCUMENT (чек, внесение, изъятие),
         * X_REPORT, OPEN_SHIFT, CLOSE_SHIFT. Для DOCUMENT обязателен documentId, для CLOSE_SHIFT — shiftId.
         */
        @GetMapping("/{kkmId}/print/html", produces = [MediaType.TEXT_HTML_VALUE])
        @Operation(
            summary = "Печатная форма (HTML)",
            description = """
                Единый эндпоинт для печатных форм. Параметр type: DOCUMENT | X_REPORT | OPEN_SHIFT | CLOSE_SHIFT.
                - DOCUMENT: documentId обязателен (чек, внесение или изъятие по id документа).
                - X_REPORT, OPEN_SHIFT: только kkmId (текущая открытая смена).
                - CLOSE_SHIFT: shiftId обязателен (Z-отчёт по смене).
            """
        )
        @KkmApiResponses(ok = MSG_200_PRINT_HTML, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_DOCUMENT_NOT_FOUND, conflict = MSG_409_SHIFT_NOT_OPEN)
        fun getPrintHtml(
                @PathVariable kkmId: String,
                @RequestParam type: PrintDocumentType,
                @RequestParam(required = false) documentId: String?,
                @RequestParam(required = false) shiftId: String?,
                @RequestHeader("Authorization") authHeader: String?
        ): String {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                return kkmService.getPrintHtml(kkmId, type, documentId, shiftId, pin)
        }

        /**
         * Универсальная печатная форма (PDF). Те же параметры, что и для print/html.
         */
        @GetMapping("/{kkmId}/print/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
        @Operation(summary = "Печатная форма (PDF)", description = "Тот же набор типов (type, documentId, shiftId), что и для GET .../print/html. Возвращает PDF.")
        @KkmApiResponses(ok = MSG_200_RECEIPT_PDF, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_DOCUMENT_NOT_FOUND, conflict = MSG_409_SHIFT_NOT_OPEN)
        fun getPrintPdf(
                @PathVariable kkmId: String,
                @RequestParam type: PrintDocumentType,
                @RequestParam(required = false) documentId: String?,
                @RequestParam(required = false) shiftId: String?,
                @RequestHeader("Authorization") authHeader: String?
        ): ResponseEntity<ByteArray> {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                val bytes = kkmService.getPrintPdf(kkmId, type, documentId, shiftId, pin)
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(bytes)
        }

        @PostMapping("/{kkmId}/documents/{documentId}/delivery/retry")
        @Operation(summary = "Повторная отправка чека по каналам", description = "Попытка ручной отправки чека по всем настроенным каналам доставки (печать, email, telegram, whatsapp и т.д.). Отправляется документ (PDF/IMAGE); каналы только с ссылкой ОФД (LINK) пропускаются. Требуется Authorization (ПИН).")
        @KkmApiResponses(ok = MSG_200_DELIVERY_RETRY, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_DOCUMENT_NOT_FOUND)
        fun retryReceiptDelivery(
                @PathVariable kkmId: String,
                @PathVariable documentId: String,
                @RequestHeader("Authorization") authHeader: String?
        ): DeliveryRetryResponse {
                val pin = AuthHeaderUtils.extractPin(authHeader)
                val results = kkmService.retryReceiptDelivery(kkmId, documentId, pin)
                return DeliveryRetryResponse(
                        results = results.map { (ch, ok) -> DeliveryRetryItemResponse(channel = ch, success = ok) }
                )
        }
}
