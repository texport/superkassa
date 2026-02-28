package kz.mybrain.superkassa.core.application.http.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Наименование товара: не допускаются обобщённые названия вроде "Товар", "Продукты", "Товар один".
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ItemNameValidator::class])
annotation class ItemNameValid(
    val message: String = "Укажите конкретное наименование товара/услуги (не допускаются: Товар, Продукт, Продукты, Товар один и т.п.)",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
