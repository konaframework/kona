package org.konaframework.firefly.schemas

import org.konaframework.support.number.Number
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/** ISO-4217 currency display options (Shamar `CurrencyOptions` parity). */
data class CurrencyOptions(
    val code: String = "USD",
    val locale: String? = null,
    val precision: Int = 2,
)

/**
 * Shared currency formatting for form prefixes, table columns, and infolist entries.
 * Delegates to [Number.currency] (exact `BigDecimal` formatting); unknown ISO codes fall back to
 * symbol + fixed decimals.
 */
object CurrencyFormat {
    @JvmStatic
    @JvmOverloads
    fun options(code: String? = "USD", locale: String? = null, precision: Int = 2): CurrencyOptions {
        val digits = if (precision < 0) 0 else precision
        val trimmedCode = code?.trim().orEmpty()
        val iso = (if (trimmedCode.isEmpty()) "USD" else trimmedCode).uppercase(Locale.ROOT)
        val trimmedLocale = locale?.trim()
        val resolvedLocale = if (trimmedLocale.isNullOrEmpty()) null else trimmedLocale
        return CurrencyOptions(iso, resolvedLocale, digits)
    }

    @JvmStatic
    @JvmOverloads
    fun symbol(code: String, locale: String? = null): String {
        return try {
            Currency.getInstance(code.trim().uppercase(Locale.ROOT)).getSymbol(localeOf(locale))
        } catch (_: Exception) {
            code.trim().uppercase(Locale.ROOT).ifEmpty { "USD" }
        }
    }

    /**
     * Formats [value] as currency. Returns null when the value is blank or not a finite number
     * so callers can fall back to placeholders or raw text.
     */
    @JvmStatic
    @JvmOverloads
    fun format(value: Any?, options: CurrencyOptions = CurrencyOptions()): String? {
        val number = toDecimal(value) ?: return null
        val opts = options(code = options.code, locale = options.locale, precision = options.precision)
        return try {
            Number.currency(number, opts.code, localeOf(opts.locale).toLanguageTag(), opts.precision)
        } catch (_: IllegalArgumentException) {
            symbol(opts.code, opts.locale) + number.setScale(opts.precision, RoundingMode.HALF_EVEN).toPlainString()
        }
    }

    /** [format] when numeric; otherwise empty for null/blank, else `toString()`. */
    @JvmStatic
    @JvmOverloads
    fun formatOrRaw(value: Any?, options: CurrencyOptions = CurrencyOptions()): String {
        val formatted = format(value, options)
        if (formatted != null) return formatted
        if (value == null) return ""
        if (value is String && value.isBlank()) return ""
        return value.toString()
    }

    internal fun localeOf(tag: String?): Locale {
        if (tag.isNullOrBlank()) return Locale.US
        return Locale.forLanguageTag(tag)
    }

    /** Exact decimal for numbers and numeric strings; null for blanks, NaN, infinities, and text. */
    internal fun toDecimal(value: Any?): BigDecimal? {
        val text = when (value) {
            is BigDecimal -> return value
            is kotlin.Number -> value.toString()
            is String -> value.trim()
            else -> return null
        }
        return text.toBigDecimalOrNull()
    }
}
