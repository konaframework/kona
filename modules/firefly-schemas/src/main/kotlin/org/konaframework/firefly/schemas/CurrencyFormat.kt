package org.konaframework.firefly.schemas

import java.text.NumberFormat
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
 * Uses `NumberFormat` currency style (symbol + amount); falls back to symbol + fixed decimals.
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
        val number = toFinite(value) ?: return null
        val opts = options(code = options.code, locale = options.locale, precision = options.precision)
        return try {
            val format = NumberFormat.getCurrencyInstance(localeOf(opts.locale))
            format.currency = Currency.getInstance(opts.code)
            format.minimumFractionDigits = opts.precision
            format.maximumFractionDigits = opts.precision
            format.format(number)
        } catch (_: Exception) {
            symbol(opts.code, opts.locale) + String.format(Locale.US, "%.${opts.precision}f", number)
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

    internal fun toFinite(value: Any?): Double? {
        val number = when (value) {
            null -> return null
            is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) return null
                trimmed.toDoubleOrNull() ?: return null
            }
            else -> return null
        }
        if (number.isNaN() || number.isInfinite()) return null
        return number
    }
}
