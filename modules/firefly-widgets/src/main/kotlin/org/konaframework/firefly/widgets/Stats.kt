package org.konaframework.firefly.widgets

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function

open class Stat {
    private val labelSetting = TextSetting()
    private val valueSetting = TextSetting()
    private val descriptionSetting = TextSetting()
    private val colorSetting = TextSetting()
    private val iconSetting = TextSetting()
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
        val points = resolvedChart(get)
        val descriptionHtml = if (description == null || description.isEmpty()) {
            ""
        } else {
            """<p class="ff-stat-description">${escapeHtml(description)}</p>"""
        }
        val colorAttr = if (color == null || color.isEmpty()) "" else """ data-ff-color="${escapeHtml(color)}""""
        val iconHtml = if (icon == null || icon.isEmpty()) "" else """<span class="ff-stat-icon">${escapeHtml(icon)}</span>"""
        val chartAttr = if (points.isEmpty()) "" else """ data-ff-chart="${escapeHtml(points.joinToString(",") { it.toString() })}""""
        return """<article class="ff-stat"$colorAttr$chartAttr>$iconHtml<p class="ff-stat-label">${escapeHtml(label)}</p><p class="ff-stat-value">${escapeHtml(value)}</p>$descriptionHtml</article>"""
    }

    private fun resolvedChart(get: Get): List<Number> {
        val provider = chartUsing
        if (provider != null) return provider.apply(get)
        return chartLiteral
    }
}

open class StatsOverviewWidget protected constructor(name: String) : Widget(name) {
    private val statList = ArrayList<Stat>()

    fun stats(vararg stats: Stat): StatsOverviewWidget = apply {
        statList.clear()
        statList.addAll(stats)
    }

    override fun extraAttributes(values: Map<String, String>): StatsOverviewWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        if (statList.isEmpty()) throw FireflyException("Stats widget [$name] has no stats")
        val get = WidgetBridge.reader(schema, this)
        val cards = statList.joinToString("") { it.render(get) }
        return WidgetAssets.tag(WidgetScripts.WIDGET) + """<div class="ff-stats">$cards</div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): StatsOverviewWidget = StatsOverviewWidget(name)
    }
}
