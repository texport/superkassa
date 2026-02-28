package kz.mybrain.superkassa.core.domain.tax

import kz.mybrain.superkassa.core.domain.model.Money
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.model.TaxRegime
import kz.mybrain.superkassa.core.domain.model.VatGroup

/**
 * Сервис агрегирования налоговых данных для X/Z-отчётов и счётчиков.
 *
 * Чистый доменный интерфейс: не знает о БД, HTTP или JSON.
 * Реализация должна использовать TaxCalculationService и данные чеков смены.
 */
interface TaxReportService {

    /**
     * Входные данные по документу смены для налоговой агрегации.
     *
     * Предполагается, что приложение собирает такие записи из БД
     * (например, на основе сохранённых ReceiptRequest или фискальных документов).
     */
    data class DocumentTaxInput(
        val operationType: ReceiptOperationType,
        val total: Money,
        val taxRegime: TaxRegime,
        val defaultVatGroup: VatGroup
    )

    /**
     * Агрегат по одной налоговой группе (например, NO_VAT / VAT_0 / VAT_16).
     */
    data class TaxGroupAggregate(
        val vatGroup: VatGroup,
        val taxableAmount: Money,
        val taxAmount: Money,
        val documentsCount: Long
    )

    /**
     * Рассчитывает агрегаты по налоговым группам для одной смены.
     *
     * Результат может быть использован:
     * - для формирования X/Z-отчётов,
     * - для обновления счетчиков в StoragePort/CounterRepository.
     */
    fun computeShiftAggregates(
        documents: List<DocumentTaxInput>
    ): List<TaxGroupAggregate>
}

