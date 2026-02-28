package kz.mybrain.superkassa.core.application.http.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

/**
 * Локальная валидация названия товара/услуги без внешних вызовов.
 * Требует конкретное, осмысленное название (не обобщения, не мусор).
 */
class ItemNameValidator : ConstraintValidator<ItemNameValid, String?> {

    /** Запрещённые слова (RU, KZ, EN) — как отдельное слово в любом месте */
    private val forbiddenWords =
        setOf(
            "товар",
            "товары",
            "продукт",
            "продукты",
            "продукция",
            "изделие",
            "услуга",
            "услуги",
            "сервис",
            "сервисы",
            "работа",
            "работы",
            "product",
            "products",
            "item",
            "items",
            "service",
            "services",
            "goods",
            "тауар",
            "қызмет",
            "жұмыс",
            "өнім"
        ).map { it.lowercase() }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value.isNullOrBlank()) return true
        val normalized = value.trim().lowercase()
        if (normalized.length < 3) return false

        // — Конкретность: минимум 3 буквы (не только цифры/пробелы/знаки)
        val letters = normalized.filter { it.isLetter() }
        if (letters.length < 3) return false

        // — Конкретность: минимум 2 разных буквы (отсекаем "ааа", "xxx", "111" уже выше)
        if (letters.toSet().size < 2) return false

        // — Не допускаем название из одних цифр и пробелов (дублирует проверку букв, но явно)
        if (normalized.all { it.isDigit() || it.isWhitespace() }) return false

        // — Название не должно состоять в основном из цифр (> половины символов)
        val digitShare = normalized.count { it.isDigit() }.toDouble() / normalized.length
        if (digitShare > 0.5) return false

        // — Запрещённое слово: точное совпадение, "слово + число", или как токен в любом месте
        for (word in forbiddenWords) {
            if (normalized == word) return false
            if (normalized.matches(Regex("^${Regex.escape(word)}\\s+\\d+$"))) return false
            if (normalized.startsWith("$word ") || normalized.endsWith(" $word")) return false
            if (" $word " in normalized) return false
        }

        // — Каждый токен не должен быть запрещённым словом или "слово+цифры" без пробела (товар123, услуга456)
        //   "товарище", "товарняк", "товарик" — не отсекаем (другие слова)
        val tokens = normalized.split(Regex("\\s+"))
        for (token in tokens) {
            if (token in forbiddenWords) return false
            for (word in forbiddenWords) {
                if (token.matches(Regex("^${Regex.escape(word)}\\d+$"))) return false
            }
        }

        return true
    }
}
