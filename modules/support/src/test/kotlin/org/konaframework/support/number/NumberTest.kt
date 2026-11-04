package org.konaframework.support.number

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

class NumberTest {
    @AfterEach
    fun reset() {
        Number.resetDefaults()
    }

    @Test
    fun formatUsesPrecisionMaxPrecisionAndLocale() {
        assertEquals("100,000", Number.format(100000))
        assertEquals("100,000.00", Number.format(100000, precision = 2))
        assertEquals("100,000.12", Number.format(100000.123, maxPrecision = 2))
        assertEquals("1,234.568", Number.format(1234.5678))
        assertEquals("2", Number.format(1.5, precision = 2, maxPrecision = 0))
        assertEquals("100.000", Number.format(100000, locale = "de"))
        assertEquals("1,234.50", Number.format(1234.5, 2, null, "en_KE"))
        assertEquals("1,234.5", Number.format(1234.5, locale = "  "))
        assertEquals("1,234,567.891", Number.format(1234567.891))
        assertEquals("2", Number.format(2.5, 0))
        assertEquals("4", Number.format(3.5, 0))
        assertEquals("1.24", Number.format(1.236, 2))
    }

    @Test
    fun documentedExamples() {
        assertEquals("1 MB", Number.fileSize(1_536_000))
        assertEquals("1,000K", Number.abbreviate(999_999))
        assertEquals("490K", Number.abbreviate(489939))
        assertEquals("-1 thousand", Number.forHumans(-1000))
        assertEquals("one point zero five", Number.spell(1.05))
        assertEquals("113th", Number.ordinal(113))
        assertEquals(100, Number.clamp(105, 0, 100))
        assertEquals(
            "1.234,50\u00A0€",
            Number.withLocale("de") { Number.withCurrency("EUR") { Number.currency(BigDecimal("1234.5")) } },
        )
    }

    @Test
    fun formatKeepsBigDecimalPrecision() {
        val huge = BigDecimal("12345678901234567890.123456789")
        assertEquals("12,345,678,901,234,567,890.123456789", Number.format(huge, maxPrecision = 9))
        assertEquals("0.10", Number.format(BigDecimal("0.1"), precision = 2))
        assertEquals("12,345,678,901,234,567,890", Number.format(BigInteger("12345678901234567890")))
    }

    @Test
    fun acceptsEveryNumberType() {
        assertEquals("5", Number.format(5))
        assertEquals("5", Number.format(5L))
        assertEquals("5", Number.format(5.toShort()))
        assertEquals("5", Number.format(5.toByte()))
        assertEquals("1.1", Number.format(1.1f))
        assertEquals("7", Number.format(AtomicInteger(7)))
        assertThrows(IllegalArgumentException::class.java) { Number.format(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Number.format(Float.POSITIVE_INFINITY) }
    }

    @Test
    fun spellsEnglishCardinals() {
        assertEquals("zero", Number.spell(0))
        assertEquals("five", Number.spell(5))
        assertEquals("nineteen", Number.spell(19))
        assertEquals("twenty", Number.spell(20))
        assertEquals("twenty-one", Number.spell(21))
        assertEquals("one hundred", Number.spell(100))
        assertEquals("one hundred one", Number.spell(101))
        assertEquals("one thousand two hundred thirty-four", Number.spell(1234))
        assertEquals("one thousand five", Number.spell(1005))
        assertEquals("one million one", Number.spell(1_000_001))
        assertEquals("minus three", Number.spell(-3))
        assertEquals("one point five", Number.spell(1.5))
        assertEquals("one point zero five", Number.spell(BigDecimal("1.050")))
        assertEquals("two", Number.spell(BigDecimal("2.00")))
        val largest = BigInteger.TEN.pow(36).subtract(BigInteger.ONE)
        assertTrue(Number.spell(largest).startsWith("nine hundred ninety-nine decillion"))
        val tooLarge = BigInteger.TEN.pow(36)
        assertEquals(Number.format(tooLarge), Number.spell(tooLarge))
    }

    @Test
    fun spellHonoursAfterAndUntil() {
        assertEquals("10", Number.spell(10, after = 10))
        assertEquals("eleven", Number.spell(11, after = 10))
        assertEquals("five", Number.spell(5, until = 10))
        assertEquals("10", Number.spell(10, until = 10))
        assertEquals("1.000", Number.spell(1000, "de", null, 1000))
    }

    @Test
    fun ordinalSuffixes() {
        assertEquals("0th", Number.ordinal(0))
        assertEquals("1st", Number.ordinal(1))
        assertEquals("2nd", Number.ordinal(2))
        assertEquals("3rd", Number.ordinal(3))
        assertEquals("4th", Number.ordinal(4))
        assertEquals("11th", Number.ordinal(11))
        assertEquals("12th", Number.ordinal(12))
        assertEquals("13th", Number.ordinal(13))
        assertEquals("21st", Number.ordinal(21))
        assertEquals("22nd", Number.ordinal(22))
        assertEquals("23rd", Number.ordinal(23))
        assertEquals("111th", Number.ordinal(111))
        assertEquals("1,013th", Number.ordinal(1013))
        assertEquals("-1st", Number.ordinal(-1))
        assertEquals("2nd", Number.ordinal(BigDecimal("2.0")))
        assertEquals("1.5", Number.ordinal(1.5))
        assertEquals("1.001st", Number.ordinal(1001, "de"))
    }

    @Test
    fun spellsEnglishOrdinals() {
        assertEquals("zeroth", Number.spellOrdinal(0))
        assertEquals("first", Number.spellOrdinal(1))
        assertEquals("second", Number.spellOrdinal(2))
        assertEquals("third", Number.spellOrdinal(3))
        assertEquals("fourth", Number.spellOrdinal(4))
        assertEquals("fifth", Number.spellOrdinal(5))
        assertEquals("eighth", Number.spellOrdinal(8))
        assertEquals("ninth", Number.spellOrdinal(9))
        assertEquals("twelfth", Number.spellOrdinal(12))
        assertEquals("twentieth", Number.spellOrdinal(20))
        assertEquals("twenty-first", Number.spellOrdinal(21))
        assertEquals("one hundredth", Number.spellOrdinal(100))
        assertEquals("one hundred first", Number.spellOrdinal(101))
        assertEquals("one thousandth", Number.spellOrdinal(1000))
        assertEquals("minus second", Number.spellOrdinal(-2))
        assertEquals("1.5", Number.spellOrdinal(1.5))
        val tooLarge = BigInteger.TEN.pow(36)
        assertEquals(Number.format(tooLarge, locale = "de"), Number.spellOrdinal(tooLarge, "de"))
    }

    @Test
    fun percentages() {
        assertEquals("10%", Number.percentage(10))
        assertEquals("10.00%", Number.percentage(10, precision = 2))
        assertEquals("10.12%", Number.percentage(10.123, maxPrecision = 2))
        assertEquals("10.1%", Number.percentage(10.1, maxPrecision = 2))
        assertEquals("10,00\u00A0%", Number.percentage(10, 2, null, "de"))
        assertEquals("33.333333333333333333%", Number.percentage(BigDecimal("33.333333333333333333"), 18))
    }

    @Test
    fun currencies() {
        assertEquals("$1,000.00", Number.currency(1000))
        assertEquals("€1,000.00", Number.currency(1000, "EUR"))
        assertEquals("1.000,00\u00A0€", Number.currency(1000, "EUR", "de"))
        assertEquals("Ksh1,234.50", Number.currency(BigDecimal("1234.5"), "KES", "en_KE"))
        assertEquals("Ksh\u00A01,234.50", Number.currency(BigDecimal("1234.5"), "KES", "sw-KE"))
        assertEquals("KES1,234.50", Number.currency(BigDecimal("1234.5"), " kes "))
        assertEquals("¥1,234", Number.currency(1234.4, "JPY"))
        assertEquals("$1,000", Number.currency(1000, "USD", precision = 0))
        assertEquals("$1,000.000", Number.currency(1000, "USD", null, 3))
        assertEquals("$12,345,678,901,234,567.89", Number.currency(BigDecimal("12345678901234567.89")))
        assertEquals("¤1", Number.currency(1, "XXX"))
        assertThrows(IllegalArgumentException::class.java) { Number.currency(1, "NOPE") }
    }

    @Test
    fun fileSizes() {
        assertEquals("1 B", Number.fileSize(1))
        assertEquals("921 B", Number.fileSize(921))
        assertEquals("1 KB", Number.fileSize(922))
        assertEquals("1 KB", Number.fileSize(1024))
        assertEquals("2 KB", Number.fileSize(2048))
        assertEquals("1.23 KB", Number.fileSize(1264.12345, precision = 2))
        assertEquals("1.234 KB", Number.fileSize(1264.12345, maxPrecision = 3))
        assertEquals("1.00 KB", Number.fileSize(1024, 2))
        assertEquals("5 GB", Number.fileSize(5L * 1024 * 1024 * 1024))
        assertEquals("10 PB", Number.fileSize(BigInteger.valueOf(1024).pow(5).multiply(BigInteger.TEN)))
        assertEquals("1,024 YB", Number.fileSize(BigInteger.valueOf(1024).pow(9)))
        assertEquals("-5 B", Number.fileSize(-5))
    }

    @Test
    fun abbreviations() {
        assertEquals("0", Number.abbreviate(0))
        assertEquals("0.00", Number.abbreviate(0, 2))
        assertEquals("1", Number.abbreviate(1))
        assertEquals("10", Number.abbreviate(10))
        assertEquals("1K", Number.abbreviate(1000))
        assertEquals("1K", Number.abbreviate(1489))
        assertEquals("1.5K", Number.abbreviate(1489, 1))
        assertEquals("1.23M", Number.abbreviate(1_230_000, 2))
        assertEquals("1.23M", Number.abbreviate(1_234_567, maxPrecision = 2))
        assertEquals("1B", Number.abbreviate(1_000_000_000))
        assertEquals("1T", Number.abbreviate(1_000_000_000_000L))
        assertEquals("1Q", Number.abbreviate(1_000_000_000_000_000L))
        assertEquals("1KQ", Number.abbreviate(BigInteger.TEN.pow(18)))
        assertEquals("-1K", Number.abbreviate(-1000))
        assertEquals("1", Number.abbreviate(0.7))
        assertEquals("1K", Number.abbreviate(BigDecimal("1E+3")))
    }

    @Test
    fun humanReadableWords() {
        assertEquals("1", Number.forHumans(1))
        assertEquals("1 thousand", Number.forHumans(1000))
        assertEquals("490 thousand", Number.forHumans(489939))
        assertEquals("1.23 million", Number.forHumans(1_230_000, 2))
        assertEquals("1 billion", Number.forHumans(1_000_000_000))
        assertEquals("1 trillion", Number.forHumans(1_000_000_000_000L))
        assertEquals("1 quadrillion", Number.forHumans(1_000_000_000_000_000L))
        assertEquals("1 thousand quadrillion", Number.forHumans(BigInteger.TEN.pow(18)))
        assertEquals("1K", Number.forHumans(1000, abbreviate = true))
    }

    @Test
    fun clamps() {
        assertEquals(3, Number.clamp(5, 1, 3))
        assertEquals(1, Number.clamp(0, 1, 3))
        assertEquals(2, Number.clamp(2, 1, 3))
        assertEquals(BigDecimal("2.5"), Number.clamp(BigDecimal("2.5"), BigDecimal.ONE, BigDecimal.TEN))
        assertEquals(1.5, Number.clamp(9.0, 0.0, 1.5))
    }

    @Test
    fun pairsOfLongs() {
        assertEquals(listOf(0L to 9L, 10L to 19L, 20L to 25L), Number.pairs(25, 10))
        assertEquals(listOf(0L to 10L, 10L to 20L, 20L to 25L), Number.pairs(25, 10, offset = 0))
        assertEquals(listOf(5L to 14L, 15L to 24L), Number.pairs(25, 10, start = 5))
        assertEquals(emptyList<Pair<Long, Long>>(), Number.pairs(0, 10))
        assertThrows(IllegalArgumentException::class.java) { Number.pairs(10, 0) }
    }

    @Test
    fun pairsOfDecimals() {
        val quarter = BigDecimal("0.25")
        assertEquals(
            listOf(
                BigDecimal("0") to BigDecimal("0.25"),
                BigDecimal("0.25") to BigDecimal("0.50"),
                BigDecimal("0.50") to BigDecimal("0.75"),
                BigDecimal("0.75") to BigDecimal("1.00"),
            ),
            Number.pairs(BigDecimal("1.00"), quarter, offset = BigDecimal.ZERO),
        )
        assertEquals(
            listOf(BigDecimal("0") to BigDecimal("9"), BigDecimal("10") to BigDecimal("19"), BigDecimal("20") to BigDecimal("25")),
            Number.pairs(BigDecimal("25"), BigDecimal("10")),
        )
        assertThrows(IllegalArgumentException::class.java) { Number.pairs(BigDecimal.TEN, BigDecimal.ZERO) }
    }

    @Test
    fun trimsTrailingZeros() {
        assertEquals(BigDecimal("12"), Number.trim(12.0))
        assertEquals(BigDecimal("12.3"), Number.trim(12.30))
        assertEquals(BigDecimal("1.5"), Number.trim(1.5f))
        assertEquals(BigDecimal("1200"), Number.trim(BigDecimal("1200.00")))
        assertEquals(BigDecimal("0.05"), Number.trim(BigDecimal("0.0500")))
        assertEquals(5, Number.trim(5))
        assertEquals(5L, Number.trim(5L))
    }

    @Test
    fun parsesLocaleFormattedNumbers() {
        assertEquals(BigDecimal("1234.5"), Number.parse("1,234.5"))
        assertEquals(BigDecimal("-12"), Number.parse(" -12 "))
        assertEquals(BigDecimal("1234.5"), Number.parse("1.234,5", "de"))
        assertEquals(BigDecimal("1234.5"), Number.parse("1 234,5", "fr"))
        assertEquals(BigDecimal("1234.5"), Number.parse("1\u00A0234,5", "fr"))
        assertEquals(BigDecimal("1234.5"), Number.parse("1\u202F234,5", "fr"))
        assertEquals(BigDecimal("12345678901234567890.123456789"), Number.parse("12,345,678,901,234,567,890.123456789"))
        assertNull(Number.parse("   "))
        assertNull(Number.parse("abc"))
        assertNull(Number.parse("12abc"))
        assertNull(Number.parse("∞"))
        assertNull(Number.parse("1 234", "en"))

        assertEquals(1234, Number.parseInt("1,234.9"))
        assertEquals(-1, Number.parseInt("-1.9"))
        assertEquals(1234, Number.parseInt("1.234", "de"))
        assertNull(Number.parseInt("99,999,999,999"))
        assertNull(Number.parseInt("x"))

        assertEquals(1234.5, Number.parseFloat("1,234.5"))
        assertEquals(1234.5, Number.parseFloat("1.234,5", "de"))
        assertNull(Number.parseFloat("x"))
    }

    @Test
    fun localeDefaults() {
        assertEquals("en", Number.defaultLocale())
        Number.useLocale("de")
        assertEquals("de", Number.defaultLocale())
        assertEquals("1.234,5", Number.format(1234.5))
        Number.useLocale("  ")
        assertEquals(Number.DEFAULT_LOCALE, Number.defaultLocale())

        assertEquals("1 234,5".replace(' ', '\u202F'), Number.withLocale("fr") { Number.format(1234.5) })
        val nested = Number.withLocale("fr") {
            Number.withLocale("de") { Number.defaultLocale() } + "/" + Number.defaultLocale()
        }
        assertEquals("de/fr", nested)
        assertEquals("en", Number.withLocale(" ") { Number.defaultLocale() })
        assertThrows(IllegalStateException::class.java) {
            Number.withLocale("de") { error("boom") }
        }
        assertEquals("en", Number.defaultLocale())

        val seenElsewhere = Number.withLocale("de") {
            var other = ""
            val thread = Thread { other = Number.defaultLocale() }
            thread.start()
            thread.join()
            other
        }
        assertEquals("en", seenElsewhere)
    }

    @Test
    fun currencyDefaults() {
        assertEquals("USD", Number.defaultCurrency())
        Number.useCurrency(" kes ")
        assertEquals("KES", Number.defaultCurrency())
        assertEquals("KES5.00", Number.currency(5))
        Number.useCurrency("")
        assertEquals(Number.DEFAULT_CURRENCY, Number.defaultCurrency())

        assertEquals("€1.00", Number.withCurrency("eur") { Number.currency(1) })
        assertEquals("USD", Number.withCurrency("  ") { Number.defaultCurrency() })
        assertEquals("USD", Number.defaultCurrency())

        Number.useLocale("de")
        Number.useCurrency("EUR")
        Number.resetDefaults()
        assertEquals("en", Number.defaultLocale())
        assertEquals("USD", Number.defaultCurrency())
    }

    @Test
    fun toBigDecimalIsExact() {
        val exact = BigDecimal("0.1")
        assertSame(exact, Number.toBigDecimal(exact))
        assertEquals(BigDecimal("0.1"), Number.toBigDecimal(0.1))
        assertEquals(BigDecimal("0.1"), Number.toBigDecimal(0.1f))
    }
}
