package org.konaframework.firefly.widgets

import org.konaframework.firefly.schemas.ComponentConfig
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer
import java.util.function.Function

enum class ChartEngine {
    CHART_JS,
    APEX_CHARTS,
}

class ChartSeries(val name: String, val data: List<Number>)

/**
 * Chart widget. The default engine is Chart.js. [ChartEngine.APEX_CHARTS] loads ApexCharts
 * instead. `options` are passed through to the selected library.
 */
open class ChartWidget protected constructor(name: String) : Widget(name) {
    private var engineLiteral: ChartEngine? = null
    private var engineUsing: Function<Get, ChartEngine>? = null
    private var typeLiteral: String = "line"
    private var typeUsing: Function<Get, String>? = null
    private var categoriesLiteral: List<String> = emptyList()
    private var categoriesUsing: Function<Get, List<String>>? = null
    private var seriesLiteral: List<ChartSeries> = emptyList()
    private var seriesUsing: Function<Get, List<ChartSeries>>? = null
    private val keyedSeries = LinkedHashMap<String, List<ChartSeries>>()
    private var optionsLiteral: Map<String, Any?> = emptyMap()
    private var optionsUsing: Function<Get, Map<String, Any?>>? = null
    private var filtersLiteral: Map<String, String> = emptyMap()
    private var filtersUsing: Function<Get, Map<String, String>>? = null
    private var activeLiteral: String? = null
    private var activeUsing: Function<Get, String>? = null
    private val inputAttributes = AttributeSetting()
    private val heightSetting = TextSetting()

    fun engine(engine: ChartEngine): ChartWidget = apply {
        engineLiteral = engine
        engineUsing = null
    }

    fun engine(provider: Function<Get, ChartEngine>): ChartWidget = apply {
        engineUsing = provider
        engineLiteral = null
    }

    fun type(value: String): ChartWidget = apply {
        typeLiteral = knownType(value)
        typeUsing = null
    }

    fun type(provider: Function<Get, String>): ChartWidget = apply { typeUsing = provider }

    fun categories(values: List<String>): ChartWidget = apply {
        categoriesLiteral = values
        categoriesUsing = null
    }

    fun categories(provider: Function<Get, List<String>>): ChartWidget = apply { categoriesUsing = provider }

    fun series(values: List<ChartSeries>): ChartWidget = apply {
        seriesLiteral = values
        seriesUsing = null
    }

    fun series(provider: Function<Get, List<ChartSeries>>): ChartWidget = apply {
        seriesUsing = provider
        seriesLiteral = emptyList()
    }

    fun series(filter: String, values: List<ChartSeries>): ChartWidget = apply { keyedSeries[filter] = values }

    fun options(values: Map<String, Any?>): ChartWidget = apply {
        optionsLiteral = LinkedHashMap(values)
        optionsUsing = null
    }

    fun options(provider: Function<Get, Map<String, Any?>>): ChartWidget = apply { optionsUsing = provider }

    fun filters(values: Map<String, String>): ChartWidget = apply {
        filtersLiteral = LinkedHashMap(values)
        filtersUsing = null
    }

    fun filters(provider: Function<Get, Map<String, String>>): ChartWidget = apply { filtersUsing = provider }

    fun activeFilter(name: String): ChartWidget = apply {
        if (name.isBlank()) throw FireflyException("Chart filter is blank")
        activeLiteral = name
        activeUsing = null
    }

    fun activeFilter(provider: Function<Get, String>): ChartWidget = apply { activeUsing = provider }

    fun maxHeight(value: String): ChartWidget = apply { heightSetting.setLiteral(value) }

    fun maxHeight(provider: Function<Get, String>): ChartWidget = apply { heightSetting.setUsing(provider) }

    fun extraInputAttributes(values: Map<String, String>): ChartWidget = apply { inputAttributes.setLiteral(values) }

    fun extraInputAttributes(provider: Function<Get, Map<String, String>>): ChartWidget = apply {
        inputAttributes.setUsing(provider)
    }

    override fun extraAttributes(values: Map<String, String>): ChartWidget {
        super.extraAttributes(values)
        return this
    }

    override fun extraAttributes(provider: Function<Get, Map<String, String>>): ChartWidget {
        super.extraAttributes(provider)
        return this
    }

    override fun label(value: String): ChartWidget {
        super.label(value)
        return this
    }

    override fun label(value: Function<Get, String>): ChartWidget {
        super.label(value)
        return this
    }

    fun resolvedEngine(get: Get): ChartEngine {
        val provider = engineUsing
        if (provider != null) return provider.apply(get)
        val chosen = engineLiteral
        if (chosen != null) return chosen
        return ChartEngine.CHART_JS
    }

    override fun renderBody(schema: Schema): String {
        val get = WidgetBridge.reader(schema, this)
        val engine = resolvedEngine(get)
        val chartType = knownType(resolvedType(get))
        val libraryType = libraryType(engine, chartType)
        val active = resolvedActive(get)
        val points = resolvedSeries(get, active)
        val labels = resolvedCategories(get)
        val options = resolvedOptions(get)
        val filters = resolvedFilters(get)
        val engineName = if (engine == ChartEngine.APEX_CHARTS) "apexcharts" else "chartjs"
        val scripts = scriptsFor(engine)
        val height = heightSetting.resolve(get)
        val heightAttr = if (height == null) "" else """ data-ff-max-height="${escapeHtml(height)}""""
        val config = chartJson(
            linkedMapOf(
                "engine" to engineName,
                "type" to chartType,
                "libraryType" to libraryType,
                "categories" to labels,
                "series" to points,
                "options" to options,
            ),
        )
        return """$scripts<div class="ff-chart" data-ff-engine="$engineName" data-ff-type="${escapeHtml(chartType)}" data-ff-library-type="${escapeHtml(libraryType)}"$heightAttr>${filterHtml(get, filters, active)}<canvas></canvas><div class="ff-chart-target"></div><script type="application/json" class="ff-chart-config">$config</script></div>"""
    }

    private fun scriptsFor(engine: ChartEngine): String {
        val paths = if (engine == ChartEngine.APEX_CHARTS) {
            listOf(WidgetScripts.WIDGET, WidgetScripts.CHART, WidgetScripts.APEX)
        } else {
            listOf(WidgetScripts.WIDGET, WidgetScripts.CHART, WidgetScripts.CHART_JS)
        }
        val out = StringBuilder()
        for (path in paths) out.append(WidgetAssets.tag(path))
        return out.toString()
    }

    private fun resolvedType(get: Get): String {
        val provider = typeUsing
        if (provider != null) return provider.apply(get)
        return typeLiteral
    }

    private fun resolvedCategories(get: Get): List<String> {
        val provider = categoriesUsing
        if (provider != null) return provider.apply(get)
        return categoriesLiteral
    }

    private fun resolvedOptions(get: Get): Map<String, Any?> {
        val provider = optionsUsing
        if (provider != null) return provider.apply(get)
        return optionsLiteral
    }

    private fun resolvedFilters(get: Get): Map<String, String> {
        val provider = filtersUsing
        if (provider != null) return provider.apply(get)
        return filtersLiteral
    }

    private fun resolvedActive(get: Get): String? {
        val provider = activeUsing
        if (provider != null) {
            val value = provider.apply(get)
            if (value.isBlank()) throw FireflyException("Chart filter is blank")
            return value
        }
        return activeLiteral
    }

    private fun resolvedSeries(get: Get, active: String?): List<ChartSeries> {
        if (active != null && keyedSeries.isNotEmpty()) {
            val match = keyedSeries[active]
            if (match == null) throw FireflyException("Chart [$name] has no series for [$active]")
            return match
        }
        val provider = seriesUsing
        if (provider != null) return provider.apply(get)
        return seriesLiteral
    }

    private fun filterHtml(get: Get, filters: Map<String, String>, active: String?): String {
        if (filters.isEmpty()) return ""
        val options = StringBuilder()
        for ((key, label) in filters) {
            val selected = if (key == active) " selected" else ""
            options.append("""<option value="${escapeHtml(key)}"$selected>${escapeHtml(label)}</option>""")
        }
        return """<select class="ff-chart-filter"${htmlAttributes(inputAttributes.resolve(get))}>$options</select>"""
    }

    companion object {
        private val TYPES = setOf("line", "bar", "area", "pie", "donut")

        @JvmStatic
        fun configureUsing(block: Consumer<ChartWidget>?) {
            if (block == null) {
                ComponentConfig.set(ChartWidget::class.java, null)
                return
            }
            ComponentConfig.set(ChartWidget::class.java, Consumer { component ->
                block.accept(component as ChartWidget)
            })
        }

        @JvmStatic
        fun make(name: String): ChartWidget {
            val widget = ChartWidget(name)
            ComponentConfig.apply(widget)
            return widget
        }

        internal fun knownType(type: String): String {
            if (!TYPES.contains(type)) throw FireflyException("Unknown chart type [$type]")
            return type
        }

        internal fun libraryType(engine: ChartEngine, type: String): String {
            if (engine == ChartEngine.CHART_JS && type == "area") return "line"
            if (engine == ChartEngine.CHART_JS && type == "donut") return "doughnut"
            return type
        }
    }
}

internal fun chartJson(value: Any?): String {
    if (value == null) return "null"
    if (value is String) return "\"" + escapeJson(value) + "\""
    if (value is Boolean) {
        if (value) return "true"
        return "false"
    }
    if (value is Number) return value.toString()
    if (value is ChartSeries) return "{\"name\":${chartJson(value.name)},\"data\":${chartJson(value.data)}}"
    if (value is Map<*, *>) {
        val parts = ArrayList<String>()
        for ((key, item) in value) {
            if (key !is String) throw FireflyException("Chart option key must be a string")
            parts.add(chartJson(key) + ":" + chartJson(item))
        }
        return "{" + parts.joinToString(",") + "}"
    }
    if (value is Iterable<*>) return "[" + value.joinToString(",") { chartJson(it) } + "]"
    throw FireflyException("Cannot encode chart value [${value.javaClass.name}]")
}

internal fun escapeJson(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
        if (ch == '\\') {
            out.append("\\\\")
        } else if (ch == '"') {
            out.append("\\\"")
        } else if (ch == '\n') {
            out.append("\\n")
        } else if (ch == '<') {
            out.append("\\u003c")
        } else {
            out.append(ch)
        }
    }
    return out.toString()
}

/** Chart.js bar chart used by the Kotlin sample. */
class RevenueChart : ChartWidget("revenue") {
    init {
        type("bar")
        categories(listOf("Mon", "Tue", "Wed"))
        series(listOf(ChartSeries("Revenue", listOf(10, 20, 15))))
    }
}

/** ApexCharts area chart used by the Kotlin sample. */
class OrdersChart : ChartWidget("orders") {
    init {
        engine(ChartEngine.APEX_CHARTS)
        type("area")
        categories(listOf("Mon", "Tue"))
        series(listOf(ChartSeries("Orders", listOf(1, 2))))
    }
}
