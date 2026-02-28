package kz.mybrain.superkassa.core.application.model

/**
 * Тип печатного документа. Один набор эндпоинтов (print/html, print/pdf) поддерживает все варианты.
 *
 * - DOCUMENT — чек, внесение или изъятие по documentId (тип определяется по документу в БД)
 * - X_REPORT — X-отчёт по текущей открытой смене (только kkmId)
 * - OPEN_SHIFT — печатная форма открытия смены (текущая открытая смена, только kkmId)
 * - CLOSE_SHIFT — Z-отчёт по смене (kkmId + shiftId)
 */
enum class PrintDocumentType {
    DOCUMENT,
    X_REPORT,
    OPEN_SHIFT,
    CLOSE_SHIFT
}
