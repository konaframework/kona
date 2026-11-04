package org.konaframework.cadence

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Exact decimal attribute cast (`decimal:<scale>`).
 *
 * Reads return a [BigDecimal] with [scale] and [RoundingMode.HALF_UP].
 * Writes accept [BigDecimal], whole-number types, or numeric [String] without routing
 * through `double`. Floating [Number] inputs use [BigDecimal.valueOf] / string form
 * rather than the binary `BigDecimal(double)` constructor.
 */
class DecimalCast @JvmOverloads constructor(
    val scale: Int = 2,
    val roundingMode: RoundingMode = RoundingMode.HALF_UP,
) : CastsAttributes {
    init {
        require(scale >= 0) { "Decimal cast scale must be >= 0 (got $scale)" }
    }

    override fun get(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) {
            return null
        }
        return toBigDecimal(value).setScale(scale, roundingMode)
    }

    override fun set(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) {
            return null
        }
        return toBigDecimal(value).setScale(scale, roundingMode)
    }

    companion object {
        /**
         * Converts [value] to [BigDecimal] without the binary `BigDecimal(double)` constructor.
         * Prefer exact types ([BigDecimal], integral numbers, numeric strings).
         */
        @JvmStatic
        fun toBigDecimal(value: Any): BigDecimal =
            when (value) {
                is BigDecimal -> value
                is BigInteger -> BigDecimal(value)
                is Long, is Int, is Short, is Byte -> BigDecimal.valueOf((value as Number).toLong())
                is Double -> BigDecimal.valueOf(value)
                is Float -> BigDecimal.valueOf(value.toDouble())
                is String ->
                    try {
                        BigDecimal(value.trim())
                    } catch (ex: NumberFormatException) {
                        throw CadenceException("Cannot cast [$value] to BigDecimal", ex)
                    }
                is Number -> BigDecimal(value.toString())
                else -> throw CadenceException("Cannot cast [$value] (${value.javaClass.name}) to BigDecimal")
            }
    }
}
