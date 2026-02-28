package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_UNIT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_UNITS_LIST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_UNIT_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.model.PaginatedResponse
import kz.mybrain.superkassa.core.application.model.UnitOfMeasurementResponse
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.NotFoundException
import kz.mybrain.superkassa.core.domain.model.UnitOfMeasurement
import org.springframework.web.bind.annotation.*

/**
 * Контроллер справочника единиц измерения (ОКЕИ).
 * Используется для measure_unit_code в позициях чека.
 */
@RestController
@RequestMapping("/units-of-measurement")
@Tag(name = "Единицы измерения (ОКЕИ)", description = "Справочник единиц измерения для measureUnitCode в чеках")
class UnitsOfMeasurementController {

    private val entries = UnitOfMeasurement.entries.filter { it != UnitOfMeasurement.UNKNOWN }

    @GetMapping
    @Operation(
        summary = "Список единиц измерения",
        description = """
            Возвращает список единиц измерения (ОКЕИ) с пагинацией и поиском.
            
            Параметры:
            - limit: количество записей (1-100, по умолчанию 50)
            - offset: смещение для пагинации (по умолчанию 0)
            - search: поиск по коду, краткому или полному названию (рус/каз)
        """
    )
    @KkmApiResponses(ok = MSG_200_UNITS_LIST)
    fun list(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) search: String?
    ): PaginatedResponse<UnitOfMeasurementResponse> {
        val validLimit = limit.coerceIn(1, 100)
        val validOffset = offset.coerceAtLeast(0)
        val filtered = if (search.isNullOrBlank()) {
            entries
        } else {
            val q = search.trim().lowercase()
            entries.filter {
                it.code == q ||
                    it.shortRus.lowercase().contains(q) ||
                    it.shortKaz.lowercase().contains(q) ||
                    it.nameRus.lowercase().contains(q) ||
                    it.nameKaz.lowercase().contains(q)
            }
        }
        val total = filtered.size
        val page = filtered.drop(validOffset).take(validLimit)
        return PaginatedResponse(
            items = page.map { UnitOfMeasurementResponse.from(it) },
            total = total,
            limit = validLimit,
            offset = validOffset,
            hasMore = validOffset + validLimit < total
        )
    }

    @GetMapping("/{code}")
    @Operation(
        summary = "Единица измерения по коду",
        description = "Возвращает единицу измерения по коду ОКЕИ (например, 796 — штука)"
    )
    @KkmApiResponses(ok = MSG_200_UNIT_FOUND, notFound = MSG_404_UNIT_NOT_FOUND)
    fun getByCode(@PathVariable code: String): UnitOfMeasurementResponse {
        val uom = entries.find { it.code == code.trim() }
            ?: throw NotFoundException(ErrorMessages.measureUnitNotFound(code), "UNIT_NOT_FOUND")
        return UnitOfMeasurementResponse.from(uom)
    }
}
