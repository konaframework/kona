package org.konaframework.support.number

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Currency
import java.util.Locale
import java.util.function.Supplier

/**
 * Locale-aware number helpers (Laravel `Number` shape).
 *
 * Every method converts its input to [BigDecimal] first, so `BigDecimal` amounts are formatted
 * exactly (never through `double`). Rounding follows `java.text` defaults (`HALF_EVEN`).
 * Spell-out and ordinal words are English for every locale.
 */
object Number {
    const val DEFAULT_LOCALE: String = "en"
    const val DEFAULT_CURRENCY: String = "USD"

    private val FILE_SIZE_UNITS = listOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    private val KIBI = BigDecimal.valueOf(1024)
    private val FILE_SIZE_STEP = BigDecimal("0.9")
    private val QUADRILLION = BigDecimal.TEN.pow(15)

    private val ABBREVIATED_UNITS = mapOf(3 to "K", 6 to "M", 9 to "B", 12 to "T", 15 to "Q")
    private val HUMAN_UNITS = mapOf(
        3 to " thousand",
        6 to " million",
        9 to " billion",
        12 to " trillion",
        15 to " quadrillion",
    )

    @Volatile
    private var locale: String = DEFAULT_LOCALE

    @Volatile
    private var currency: String = DEFAULT_CURRENCY

    private val scopedLocale = ThreadLocal<String?>()
    private val scopedCurrency = ThreadLocal<String?>()

    /**
     * Formats [number] with locale grouping. [precision] fixes the fraction digits;
     * [maxPrecision] caps them (and wins when both are given). Default: up to 3 fraction digits.
     */
    @JvmStatic
    @JvmOverloads
    fun format(
        number: kotlin.Number,
        precision: Int? = null,
        maxPrecision: Int? = null,
        locale: String? = null,
    ): String {
        val formatter = NumberFormat.getNumberInstance(localeOf(locale))
        applyPrecision(formatter, precision, maxPrecision)
        return formatter.format(toBigDecimal(number))
    }

    /**
     * Spells [number] in English words. When [after] is given and `number <= after`, or [until] is
     * given and `number >= until`, the number is [format]ted instead.
     */
    @JvmStatic
    @JvmOverloads
    fun spell(
        number: kotlin.Number,
        locale: String? = null,
        after: kotlin.Number? = null,
        until: kotlin.Number? = null,
    ): String {
        val value = toBigDecimal(number)
        if (after != null && value <= toBigDecimal(after)) {
            return format(value, locale = locale)
        }
        if (until != null && value >= toBigDecimal(until)) {
            return format(value, locale = locale)
        }
        if (!EnglishSpeller.canSpell(value)) {
            return format(value, locale = locale)
        }
        return EnglishSpeller.spell(value)
    }

    /** Formats [number] with an English ordinal suffix: `1st`, `22nd`, `1,013th`. */
    @JvmStatic
    @JvmOverloads
    fun ordinal(number: kotlin.Number, locale: String? = null): String {
        val value = toBigDecimal(number)
        val formatted = format(value, locale = locale)
        if (!isWhole(value)) {
            return formatted
        }
        return formatted + EnglishSpeller.ordinalSuffix(value.toBigInteger())
    }

    /** Spells [number] as an English ordinal: `first`, `twenty-second`, `one hundredth`. */
    @JvmStatic
    @JvmOverloads
    fun spellOrdinal(number: kotlin.Number, locale: String? = null): String {
        val value = toBigDecimal(number)
        if (!isWhole(value) || !EnglishSpeller.canSpell(value)) {
            return format(value, locale = locale)
        }
        return EnglishSpeller.spellOrdinal(value.toBigInteger())
    }

    /** Formats [number] (already a percentage, e.g. `10` for 10%) with the locale percent sign. */
    @JvmStatic
    @JvmOverloads
    fun percentage(
        number: kotlin.Number,
        precision: Int = 0,
        maxPrecision: Int? = null,
        locale: String? = null,
    ): String {
        val formatter = NumberFormat.getPercentInstance(localeOf(locale))
        applyPrecision(formatter, precision, maxPrecision)
        return formatter.format(toBigDecimal(number).movePointLeft(2))
    }

    /**
     * Formats [amount] as money in the ISO-4217 [code] (blank → [defaultCurrency]).
     * [precision] defaults to the currency's own minor units (2 for KES/USD, 0 for JPY).
     *
     * @throws IllegalArgumentException when [code] is not a known ISO-4217 currency.
     */
    @JvmStatic
    @JvmOverloads
    fun currency(
        amount: kotlin.Number,
        code: String = "",
        locale: String? = null,
        precision: Int? = null,
    ): String {
        val iso = code.trim().ifEmpty { defaultCurrency() }.uppercase(Locale.ROOT)
        val unit = Currency.getInstance(iso)
        val formatter = NumberFormat.getCurrencyInstance(localeOf(locale))
        formatter.currency = unit
        val digits = precision ?: unit.defaultFractionDigits.coerceAtLeast(0)
        formatter.minimumFractionDigits = digits
        formatter.maximumFractionDigits = digits
        return formatter.format(toBigDecimal(amount))
    }

    /** Human-readable byte size using 1024 steps: `1 KB`, `1.50 MB`. */
    @JvmStatic
    @JvmOverloads
    fun fileSize(bytes: kotlin.Number, precision: Int = 0, maxPrecision: Int? = null): String {
        var value = toBigDecimal(bytes)
        var unit = 0
        while (unit < FILE_SIZE_UNITS.size - 1 && value.divide(KIBI) > FILE_SIZE_STEP) {
            value = value.divide(KIBI)
            unit++
        }
        return format(value, precision, maxPrecision) + " " + FILE_SIZE_UNITS[unit]
    }

    /** Short human form: `1K`, `1.23M`, `5B`, `2T`, `1Q`. */
    @JvmStatic
    @JvmOverloads
    fun abbreviate(number: kotlin.Number, precision: Int = 0, maxPrecision: Int? = null): String =
        forHumans(number, precision, maxPrecision, abbreviate = true)

    /** Word form: `1 thousand`, `1.23 million`. With [abbreviate], same as [Number.abbreviate]. */
    @JvmStatic
    @JvmOverloads
    fun forHumans(
        number: kotlin.Number,
        precision: Int = 0,
        maxPrecision: Int? = null,
        abbreviate: Boolean = false,
    ): String {
        val units = if (abbreviate) ABBREVIATED_UNITS else HUMAN_UNITS
        return summarize(toBigDecimal(number), precision, maxPrecision, units)
    }

    /** Restricts [number] to the inclusive range [min]..[max]. */
    @JvmStatic
    fun <T> clamp(number: T, min: T, max: T): T where T : kotlin.Number, T : Comparable<T> {
        val lower = if (number < min) min else number
        return if (lower > max) max else lower
    }

    /**
     * Splits `[start, to)` into consecutive ranges of size [by]:
     * `pairs(25, 10)` → `(0, 9), (10, 19), (20, 25)`; with `offset = 0` → `(0, 10), (10, 20), (20, 25)`.
     */
    @JvmStatic
    @JvmOverloads
    fun pairs(to: Long, by: Long, start: Long = 0, offset: Long = 1): List<Pair<Long, Long>> {
        require(by > 0) { "pairs step must be positive, got $by" }
        val output = mutableListOf<Pair<Long, Long>>()
        var lower = start
        while (lower < to) {
            output.add(lower to minOf(lower + by - offset, to))
            lower += by
        }
        return output
    }

    /** [pairs] for decimal ranges, computed exactly with [BigDecimal]. */
    @JvmStatic
    @JvmOverloads
    fun pairs(
        to: BigDecimal,
        by: BigDecimal,
        start: BigDecimal = BigDecimal.ZERO,
        offset: BigDecimal = BigDecimal.ONE,
    ): List<Pair<BigDecimal, BigDecimal>> {
        require(by.signum() > 0) { "pairs step must be positive, got $by" }
        val output = mutableListOf<Pair<BigDecimal, BigDecimal>>()
        var lower = start
        while (lower < to) {
            output.add(lower to (lower + by - offset).min(to))
            lower += by
        }
        return output
    }

    /**
     * Drops trailing fractional zeros: `12.0` → `12`, `12.30` → `12.3`. Decimal inputs come back as
     * [BigDecimal]; whole-number types are returned unchanged.
     */
    @JvmStatic
    fun trim(number: kotlin.Number): kotlin.Number =
        when (number) {
            is BigDecimal, is Double, is Float -> {
                val stripped = toBigDecimal(number).stripTrailingZeros()
                if (stripped.scale() < 0) stripped.setScale(0) else stripped
            }
            else -> number
        }

    /** Parses a locale-formatted number exactly (`"1,234.5"` in `en`). Null when not a number. */
    @JvmStatic
    @JvmOverloads
    fun parse(value: String, locale: String? = null): BigDecimal? {
        val formatter = NumberFormat.getNumberInstance(localeOf(locale)) as DecimalFormat
        formatter.isParseBigDecimal = true
        val grouping = formatter.decimalFormatSymbols.groupingSeparator
        var text = value.trim()
        if (Character.isSpaceChar(grouping)) {
            text = text.replace(Regex("[\\s\\u00A0\\u202F]"), grouping.toString())
        }
        if (text.isEmpty()) {
            return null
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(text, position)
        if (position.index != text.length) {
            return null
        }
        return parsed as? BigDecimal
    }

    /** [parse] truncated toward zero to an `Int`; null when not a number or out of range. */
    @JvmStatic
    @JvmOverloads
    fun parseInt(value: String, locale: String? = null): Int? {
        val parsed = parse(value, locale) ?: return null
        return try {
            parsed.setScale(0, RoundingMode.DOWN).intValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** [parse] as a `Double`; null when not a number. */
    @JvmStatic
    @JvmOverloads
    fun parseFloat(value: String, locale: String? = null): Double? = parse(value, locale)?.toDouble()

    /** Sets the process-wide default locale (BCP 47 or `en_KE` style). */
    @JvmStatic
    fun useLocale(locale: String) {
        this.locale = locale.trim().ifEmpty { DEFAULT_LOCALE }
    }

    /** Runs [callback] with [locale] as the default on the current thread only. */
    @JvmStatic
    fun <T> withLocale(locale: String, callback: Supplier<T>): T =
        scoped(scopedLocale, locale, callback)

    /** Current default locale: a [withLocale] scope on this thread, else [useLocale]. */
    @JvmStatic
    fun defaultLocale(): String = scopedLocale.get() ?: locale

    /** Sets the process-wide default ISO-4217 currency code. */
    @JvmStatic
    fun useCurrency(currency: String) {
        this.currency = currency.trim().ifEmpty { DEFAULT_CURRENCY }.uppercase(Locale.ROOT)
    }

    /** Runs [callback] with [currency] as the default on the current thread only. */
    @JvmStatic
    fun <T> withCurrency(currency: String, callback: Supplier<T>): T =
        scoped(scopedCurrency, currency.uppercase(Locale.ROOT), callback)

    /** Current default currency: a [withCurrency] scope on this thread, else [useCurrency]. */
    @JvmStatic
    fun defaultCurrency(): String = scopedCurrency.get() ?: currency

    /** Restores the `en` / `USD` defaults (useful between tests). */
    @JvmStatic
    fun resetDefaults() {
        locale = DEFAULT_LOCALE
        currency = DEFAULT_CURRENCY
    }

    internal fun localeOf(tag: String?): Locale {
        val resolved = if (tag.isNullOrBlank()) defaultLocale() else tag
        return Locale.forLanguageTag(resolved.trim().replace('_', '-'))
    }

    internal fun toBigDecimal(number: kotlin.Number): BigDecimal =
        when (number) {
            is BigDecimal -> number
            is BigInteger -> BigDecimal(number)
            is Long, is Int, is Short, is Byte -> BigDecimal.valueOf(number.toLong())
            is Double, is Float -> {
                require(number.toDouble().isFinite()) { "Cannot format non-finite number $number" }
                BigDecimal(number.toString())
            }
            else -> BigDecimal(number.toString())
        }

    private fun isWhole(value: BigDecimal): Boolean = value.stripTrailingZeros().scale() <= 0

    private fun applyPrecision(formatter: NumberFormat, precision: Int?, maxPrecision: Int?) {
        if (maxPrecision != null) {
            formatter.maximumFractionDigits = maxPrecision
        } else if (precision != null) {
            formatter.minimumFractionDigits = precision
            formatter.maximumFractionDigits = precision
        }
    }

    private fun summarize(
        value: BigDecimal,
        precision: Int,
        maxPrecision: Int?,
        units: Map<Int, String>,
    ): String {
        if (value.signum() == 0) {
            return if (precision > 0) format(0, precision, maxPrecision) else "0"
        }
        if (value.signum() < 0) {
            return "-" + summarize(value.negate(), precision, maxPrecision, units)
        }
        if (value >= QUADRILLION) {
            return summarize(value.movePointLeft(15), precision, maxPrecision, units) + units.getValue(15)
        }
        val exponent = value.precision() - value.scale() - 1
        val display = exponent - (exponent % 3)
        val scaled = value.movePointLeft(display)
        return (format(scaled, precision, maxPrecision) + (units[display] ?: "")).trim()
    }

    private fun <T> scoped(slot: ThreadLocal<String?>, value: String, callback: Supplier<T>): T {
        val previous = slot.get()
        slot.set(value.trim().ifEmpty { null })
        try {
            return callback.get()
        } finally {
            slot.set(previous)
        }
    }
}
