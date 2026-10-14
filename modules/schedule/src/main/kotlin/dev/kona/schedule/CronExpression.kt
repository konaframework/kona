package dev.kona.schedule

import java.time.LocalDateTime

// Minimal 5-field cron (min hour dom month dow). Supports *, numbers, and step patterns.
class CronExpression(expression: String) {
    private val fields: List<Field>

    init {
        val parts = expression.trim().split(Regex("\\s+"))
        require(parts.size == 5) { "Cron expression must have 5 fields: $expression" }
        fields = listOf(
            Field.parse(parts[0], 0, 59),
            Field.parse(parts[1], 0, 23),
            Field.parse(parts[2], 1, 31),
            Field.parse(parts[3], 1, 12),
            Field.parse(parts[4], 0, 6),
        )
    }

    fun matches(time: LocalDateTime): Boolean =
        fields[0].matches(time.minute) &&
            fields[1].matches(time.hour) &&
            fields[2].matches(time.dayOfMonth) &&
            fields[3].matches(time.monthValue) &&
            fields[4].matches(time.dayOfWeek.value % 7)

    private class Field(
        private val values: Set<Int>?,
        private val step: Int?,
        private val min: Int,
    ) {
        fun matches(value: Int): Boolean {
            if (values == null && step == null) return true
            if (step != null) return (value - min) % step == 0
            return values!!.contains(value)
        }

        companion object {
            fun parse(raw: String, min: Int, max: Int): Field {
                if (raw == "*") return Field(null, null, min)
                if (raw.startsWith("*/")) {
                    val step = raw.removePrefix("*/").toInt()
                    require(step > 0) { "Cron step must be positive" }
                    return Field(null, step, min)
                }
                val value = raw.toInt()
                require(value in min..max) { "Cron value $value out of range $min..$max" }
                return Field(setOf(value), null, min)
            }
        }
    }
}
