package org.konaframework.firefly.schemas

import org.konaframework.firefly.support.FireflyException

/**
 * Responsive schema / layout grid columns (Filament / Shamar shape).
 *
 * - No layout → one column (CSS stacks; no `data-ff-columns`).
 * - [of] / integer [org.konaframework.firefly.schemas.Schema.columns] → count from `lg` up;
 *   smaller breakpoints stay **1**.
 * - [two] / no-arg `columns()` → same as [of] `(2)`.
 * - [from] breakpoint map (`default`, `sm`, `md`, `lg`, `xl`, `2xl`) for per-size counts.
 */
class ColumnLayout private constructor(private val explicit: Map<String, Int>) {
    /** Cascaded count per breakpoint for CSS custom properties. */
    fun resolved(): Map<String, Int> {
        var current = explicit[DEFAULT] ?: 1
        val out = LinkedHashMap<String, Int>()
        for (breakpoint in ORDER) {
            explicit[breakpoint]?.let { current = it }
            out[breakpoint] = current
        }
        return out
    }

    /** Inline `style` value: `--ff-cols` plus `--ff-cols-{sm|md|lg|xl|2xl}`. */
    fun styleAttribute(): String {
        val counts = resolved()
        val parts = ArrayList<String>(ORDER.size)
        parts.add("--ff-cols: ${counts.getValue(DEFAULT)}")
        for (breakpoint in ORDER) {
            if (breakpoint == DEFAULT) continue
            parts.add("--ff-cols-$breakpoint: ${counts.getValue(breakpoint)}")
        }
        return parts.joinToString("; ")
    }

    /** Value for `data-ff-columns` (lg count after cascade — useful for tests / a11y). */
    fun dataValue(): String = resolved().getValue(LG).toString()

    /** Single-int view: explicit `lg`, else `default`, else cascaded `lg`. */
    fun primary(): Int {
        explicit[LG]?.let { return it }
        explicit[DEFAULT]?.let { return it }
        return resolved().getValue(LG)
    }

    companion object {
        const val DEFAULT = "default"
        const val SM = "sm"
        const val MD = "md"
        const val LG = "lg"
        const val XL = "xl"
        const val XXL = "2xl"

        val ORDER: List<String> = listOf(DEFAULT, SM, MD, LG, XL, XXL)

        /** Integer columns apply from `lg` upward; below `lg` the grid stays 1 column. */
        @JvmStatic
        fun of(count: Int): ColumnLayout {
            if (count < 1) throw FireflyException("Schema columns must be at least 1")
            return ColumnLayout(mapOf(LG to count))
        }

        /** Same as [of] `(2)` — used by no-arg `columns()`. */
        @JvmStatic
        fun two(): ColumnLayout = of(2)

        @JvmStatic
        fun from(breakpoints: Map<String, Int>): ColumnLayout {
            if (breakpoints.isEmpty()) {
                throw FireflyException("Schema columns map must not be empty")
            }
            val normalized = LinkedHashMap<String, Int>()
            for ((rawKey, count) in breakpoints) {
                val key = rawKey.trim().lowercase()
                if (key !in ORDER) {
                    throw FireflyException(
                        "Unknown columns breakpoint [$rawKey]; use ${ORDER.joinToString(", ")}",
                    )
                }
                if (count < 1) throw FireflyException("Schema columns must be at least 1")
                normalized[key] = count
            }
            return ColumnLayout(normalized.toMap())
        }
    }
}
