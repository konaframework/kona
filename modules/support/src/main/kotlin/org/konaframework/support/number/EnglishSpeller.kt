package org.konaframework.support.number

import java.math.BigDecimal
import java.math.BigInteger

/** English cardinal / ordinal spell-out used by [Number.spell] and [Number.spellOrdinal]. */
internal object EnglishSpeller {
    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen",
    )

    private val TENS = listOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    private val SCALES = listOf(
        "", "thousand", "million", "billion", "trillion", "quadrillion", "quintillion",
        "sextillion", "septillion", "octillion", "nonillion", "decillion",
    )

    private val IRREGULAR_ORDINALS = mapOf(
        "one" to "first",
        "two" to "second",
        "three" to "third",
        "five" to "fifth",
        "eight" to "eighth",
        "nine" to "ninth",
        "twelve" to "twelfth",
    )

    private val THOUSAND = BigInteger.valueOf(1000)

    /** Largest whole number (exclusive) with an English scale word: 10^36. */
    private val LIMIT = BigInteger.TEN.pow(SCALES.size * 3)

    fun canSpell(value: BigDecimal): Boolean = value.abs().toBigInteger() < LIMIT

    fun spell(value: BigDecimal): String {
        if (value.signum() < 0) {
            return "minus " + spell(value.negate())
        }
        val whole = spellInteger(value.toBigInteger())
        val stripped = value.stripTrailingZeros()
        if (stripped.scale() <= 0) {
            return whole
        }
        val digits = stripped.toPlainString().substringAfter('.')
        return whole + " point " + digits.map { ONES[it - '0'] }.joinToString(" ")
    }

    fun spellOrdinal(value: BigInteger): String {
        if (value.signum() < 0) {
            return "minus " + spellOrdinal(value.negate())
        }
        val words = spellInteger(value)
        val cut = maxOf(words.lastIndexOf(' '), words.lastIndexOf('-')) + 1
        return words.substring(0, cut) + ordinalWord(words.substring(cut))
    }

    fun ordinalSuffix(value: BigInteger): String {
        val abs = value.abs()
        val lastTwo = abs.mod(BigInteger.valueOf(100)).toInt()
        if (lastTwo in 11..13) {
            return "th"
        }
        return when (lastTwo % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }

    private fun ordinalWord(word: String): String =
        IRREGULAR_ORDINALS[word]
            ?: if (word.endsWith("y")) word.dropLast(1) + "ieth" else word + "th"

    private fun spellInteger(value: BigInteger): String {
        if (value.signum() == 0) {
            return ONES[0]
        }
        val groups = mutableListOf<Int>()
        var rest = value
        while (rest.signum() > 0) {
            val parts = rest.divideAndRemainder(THOUSAND)
            groups.add(parts[1].toInt())
            rest = parts[0]
        }
        val words = ArrayDeque<String>()
        for ((index, group) in groups.withIndex()) {
            if (group == 0) {
                continue
            }
            words.addFirst(if (index > 0) spellHundreds(group) + " " + SCALES[index] else spellHundreds(group))
        }
        return words.joinToString(" ")
    }

    private fun spellHundreds(value: Int): String {
        val parts = mutableListOf<String>()
        if (value >= 100) {
            parts.add(ONES[value / 100] + " hundred")
        }
        val rest = value % 100
        if (rest > 0) {
            parts.add(spellTens(rest))
        }
        return parts.joinToString(" ")
    }

    private fun spellTens(value: Int): String {
        if (value < 20) {
            return ONES[value]
        }
        val unit = value % 10
        return if (unit == 0) TENS[value / 10] else TENS[value / 10] + "-" + ONES[unit]
    }
}
