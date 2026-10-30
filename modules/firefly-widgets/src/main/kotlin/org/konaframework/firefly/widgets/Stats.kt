package org.konaframework.firefly.widgets

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

open class Stat {
    private val labelSetting = TextSetting()
    private val valueSetting = TextSetting()
    private val descriptionSetting = TextSetting()
    private val colorSetting = TextSetting()
    private val iconSetting = TextSetting()
    private val urlSetting = TextSetting()
    private var openUrlInNewTabFlag = false
    private var chartLiteral: List<Number> = emptyList()
    private var chartUsing: Function<Get, List<Number>>? = null

    fun label(value: String): Stat = apply { labelSetting.setLiteral(value) }

    fun label(provider: Function<Get, String>): Stat = apply { labelSetting.setUsing(provider) }

    fun value(text: String): Stat = apply { valueSetting.setLiteral(text) }

    fun value(provider: Function<Get, String>): Stat = apply { valueSetting.setUsing(provider) }

    fun description(value: String): Stat = apply { descriptionSetting.setLiteral(value) }

    fun description(provider: Function<Get, String>): Stat = apply { descriptionSetting.setUsing(provider) }

    fun color(value: String): Stat = apply { colorSetting.setLiteral(value) }

    fun color(provider: Function<Get, String>): Stat = apply { colorSetting.setUsing(provider) }

    fun icon(value: String): Stat = apply { iconSetting.setLiteral(value) }

    fun icon(provider: Function<Get, String>): Stat = apply { iconSetting.setUsing(provider) }

    fun url(value: String): Stat = apply { urlSetting.setLiteral(value) }

    fun url(provider: Function<Get, String>): Stat = apply { urlSetting.setUsing(provider) }

    fun openUrlInNewTab(on: Boolean = true): Stat = apply { openUrlInNewTabFlag = on }

    fun chart(values: List<Number>): Stat = apply {
        chartLiteral = values
        chartUsing = null
    }

    fun chart(provider: Function<Get, List<Number>>): Stat = apply { chartUsing = provider }

    fun render(get: Get): String {
        val label = labelSetting.resolve(get)
        if (label == null || label.isBlank()) throw FireflyException("Stat label is blank")
        val value = valueSetting.resolve(get)
        if (value == null || value.isBlank()) throw FireflyException("Stat value is blank")
        val description = descriptionSetting.resolve(get)
        val color = colorSetting.resolve(get)
        val icon = iconSetting.resolve(get)
        val href = urlSetting.resolve(get)
        val points = resolvedChart(get)
        val descriptionHtml = if (description == null || description.isEmpty()) {
            ""
        } else {
            """<p class="ff-stat-description">${escapeHtml(description)}</p>"""
        }
        val colorAttr = if (color == null || color.isEmpty()) "" else """ data-ff-color="${escapeHtml(color)}""""
        val iconHtml = if (icon == null || icon.isEmpty()) "" else """<span class="ff-stat-icon">${escapeHtml(icon)}</span>"""
        val chartAttr = if (points.isEmpty()) "" else """ data-ff-chart="${escapeHtml(points.joinToString(",") { it.toString() })}""""
        val labelHtml = """<p class="ff-stat-label">${escapeHtml(label)}</p>"""
        val valueHtml = """<p class="ff-stat-value">${escapeHtml(value)}</p>"""
        val inner = "$iconHtml$labelHtml$valueHtml$descriptionHtml"
        val body = if (href.isNullOrBlank()) {
            inner
        } else {
            val target = if (openUrlInNewTabFlag) """ target="_blank" rel="noopener noreferrer"""" else ""
            """<a class="ff-stat-link" href="${escapeHtml(href)}"$target>$inner</a>"""
        }
        return """<article class="ff-stat"$colorAttr$chartAttr>$body</article>"""
    }

    private fun resolvedChart(get: Get): List<Number> {
        val provider = chartUsing
        if (provider != null) return provider.apply(get)
        return chartLiteral
    }
}

/** Compact number formatting for large counters (Shamar `formatCompactNumber`). */
fun formatCompactNumber(value: Number): String {
    val n = value.toDouble()
    val abs = kotlin.math.abs(n)
    val sign = if (n < 0) "-" else ""
    return when {
        abs >= 1_000_000_000 -> sign + trimCompact(abs / 1_000_000_000.0) + "B"
        abs >= 1_000_000 -> sign + trimCompact(abs / 1_000_000.0) + "M"
        abs >= 1_000 -> sign + trimCompact(abs / 1_000.0) + "K"
        else -> sign + abs.toLong().toString()
    }
}

private fun trimCompact(n: Double): String {
    val rounded = (kotlin.math.round(n * 10.0) / 10.0)
    return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

typealias StatsOverviewBreakpoint = String

open class StatsOverviewWidget protected constructor(name: String) : Widget(name) {
    private val statList = ArrayList<Stat>()
    private var columnsLiteral: Any? = null

    init {
        applyDashboardDefaults(lazyDefault = true, pollingDefault = "5s")
    }

    fun stats(vararg stats: Stat): StatsOverviewWidget = apply {
        statList.clear()
        statList.addAll(stats)
    }

    /** Cards-per-row inside the stats grid (Filament/Shamar `getColumns()`). */
    override fun columns(count: Int): StatsOverviewWidget = apply {
        columnsLiteral = count
    }

    override fun columns(breakpoints: Map<String, Int>): StatsOverviewWidget = apply {
        columnsLiteral = LinkedHashMap(breakpoints)
    }

    override fun extraAttributes(values: Map<String, String>): StatsOverviewWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        if (statList.isEmpty() && !shouldDefer(schema)) {
            throw FireflyException("Stats widget [$name] has no stats")
        }
        val get = WidgetBridge.reader(schema, this)
        val count = if (shouldDefer(schema)) 0 else statList.size
        val resolved = resolveStatsOverviewColumns(columnsLiteral, count)
        val style = statsOverviewGridStyle(resolved)
        val cards = if (shouldDefer(schema)) {
            ""
        } else {
            statList.joinToString("") { it.render(get) }
        }
        val inner = """<div class="ff-stats" style="$style" data-ff-stats-grid="1">$cards</div>"""
        return WidgetAssets.tag(WidgetScripts.WIDGET) + wrapDashboardBody(schema, inner)
    }

    companion object {
        @JvmStatic
        fun make(name: String): StatsOverviewWidget = StatsOverviewWidget(name)
    }
}

internal fun clampStatsOverviewColumns(value: Int): Int = min(12, max(1, value))

/** Filament auto `getColumns()` heuristic from stat count. */
fun autoStatsOverviewColumns(statsCount: Int): Int {
    val count = max(0, statsCount)
    if (count < 3) return 3
    if (count % 3 != 1) return 3
    return 4
}

@Suppress("UNCHECKED_CAST")
fun resolveStatsOverviewColumns(
    columns: Any?,
    statsCount: Int = 0,
): Map<String, Int> {
    if (columns == null) {
        val n = autoStatsOverviewColumns(statsCount)
        return linkedMapOf("default" to 1, "sm" to n)
    }
    if (columns is Number) {
        val n = clampStatsOverviewColumns(columns.toInt())
        return linkedMapOf("default" to 1, "sm" to n)
    }
    if (columns is Map<*, *>) {
        val out = LinkedHashMap<String, Int>()
        for ((key, raw) in columns) {
            if (key !is String || raw !is Number) continue
            out[key] = clampStatsOverviewColumns(raw.toInt())
        }
        if (out.isEmpty()) {
            val n = autoStatsOverviewColumns(statsCount)
            return linkedMapOf("default" to 1, "sm" to n)
        }
        if (!out.containsKey("default")) out["default"] = 1
        return out
    }
    val n = autoStatsOverviewColumns(statsCount)
    return linkedMapOf("default" to 1, "sm" to n)
}

fun statsOverviewGridStyle(columns: Map<String, Int>): String {
    val vars = StringBuilder()
    for ((bp, n) in columns) {
        val cssVar = when (bp) {
            "default" -> "--ff-stats-cols"
            else -> "--ff-stats-cols-$bp"
        }
        vars.append("$cssVar: $n; ")
    }
    return vars.toString().trim()
}
