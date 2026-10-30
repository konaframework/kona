package org.konaframework.firefly.widgets

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.ComponentConfig
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Function
import java.util.function.Predicate

/**
 * A panel widget. It renders on its own and inside a [Page] or [Dashboard].
 *
 * Chainable setters take a static value or a closure that receives [Get].
 */
open class Widget protected constructor(name: String) : Component(name) {
    private var sortValue: Int = 0
    private var pollingIntervalValue: String? = null
    private var pollingExplicit: Boolean = false
    private var lazyFlag: Boolean = false
    private var lazyExplicit: Boolean = false
    private var canViewPredicate: Predicate<Get>? = null
    private var refreshUrlValue: String? = null

    override fun kind(): String = "widget"

    /** Dashboard grid order (Shamar `Widget.sort`). Lower first. */
    fun sort(value: Int): Widget = apply { sortValue = value }

    fun sort(): Int = sortValue

    /**
     * Poll interval (Shamar `pollingInterval`), e.g. `"5s"`, `"500ms"`.
     * Pass `null` to disable. Also sets Component [poll] for Livewire-style attrs when non-null.
     */
    fun pollingInterval(interval: String?): Widget = apply {
        pollingExplicit = true
        pollingIntervalValue = interval
        if (interval.isNullOrBlank()) {
            // leave Component.poll untouched when clearing
        } else {
            poll(interval)
        }
    }

    fun pollingInterval(): String? = pollingIntervalValue

    /** Parsed milliseconds, or null when polling is off / unparseable. */
    fun pollingIntervalMs(): Int? = parsePollingIntervalMs(pollingIntervalValue)

    /** Defer body until visible (Shamar `isLazy`). */
    fun lazy(on: Boolean): Widget = apply {
        lazyExplicit = true
        lazyFlag = on
    }

    fun isLazy(): Boolean = lazyFlag

    /** Skip this widget when the predicate is false (Shamar `canView`). */
    fun canView(predicate: Predicate<Get>): Widget = apply { canViewPredicate = predicate }

    fun canView(on: Boolean): Widget = apply { canViewPredicate = Predicate { on } }

    fun visibleFor(get: Get): Boolean {
        val predicate = canViewPredicate ?: return true
        return predicate.test(get)
    }

    /** Panel-relative URL that returns this widget’s HTML fragment (lazy / poll). */
    fun refreshUrl(url: String?): Widget = apply { refreshUrlValue = url }

    fun refreshUrl(): String? = refreshUrlValue

    internal fun applyDashboardDefaults(lazyDefault: Boolean, pollingDefault: String?) {
        if (!lazyExplicit) lazyFlag = lazyDefault
        if (!pollingExplicit) {
            pollingIntervalValue = pollingDefault
            if (!pollingDefault.isNullOrBlank()) poll(pollingDefault)
        }
    }

    override fun extraAttributes(values: Map<String, String>): Widget {
        super.extraAttributes(values)
        return this
    }

    override fun extraAttributes(provider: Function<Get, Map<String, String>>): Widget {
        super.extraAttributes(provider)
        return this
    }

    override fun label(value: String): Widget {
        super.label(value)
        return this
    }

    override fun label(value: Function<Get, String>): Widget {
        super.label(value)
        return this
    }

    override fun heading(value: String): Widget {
        super.heading(value)
        return this
    }

    override fun heading(value: Function<Get, String>): Widget {
        super.heading(value)
        return this
    }

    override fun description(value: String): Widget {
        super.description(value)
        return this
    }

    override fun description(value: Function<Get, String>): Widget {
        super.description(value)
        return this
    }

    protected fun resolvedRefreshUrl(schema: Schema): String? {
        if (!refreshUrlValue.isNullOrBlank()) return refreshUrlValue
        val get = WidgetBridge.reader(schema, this)
        val raw = get.get("widgetRefreshBase") ?: return null
        val base = raw.toString().trimEnd('/')
        if (base.isBlank()) return null
        return "$base/widgets/${java.net.URLEncoder.encode(name, Charsets.UTF_8)}"
    }

    /**
     * True when this render should emit a lazy placeholder.
     * Requires a refresh URL; otherwise the widget renders eagerly (tests / embeds).
     * Hydrate when state `hydrateWidget` equals this widget’s [name], or `hydrateWidgets` is true.
     */
    protected fun shouldDefer(schema: Schema): Boolean {
        if (!isLazy()) return false
        if (resolvedRefreshUrl(schema).isNullOrBlank()) return false
        val get = WidgetBridge.reader(schema, this)
        val hydrateAll = get.get("hydrateWidgets")
        if (hydrateAll == true || hydrateAll?.toString() == "true") return false
        val hydrateId = get.get("hydrateWidget")?.toString()
        if (hydrateId != null && hydrateId == name) return false
        return true
    }

    protected fun dashboardShellAttrs(schema: Schema): String {
        val url = resolvedRefreshUrl(schema)
        val ms = pollingIntervalMs()
        val defer = shouldDefer(schema)
        val parts = StringBuilder(""" data-ff-dashboard-widget="1" data-ff-widget-id="${escapeHtml(name)}"""")
        if (!url.isNullOrBlank()) parts.append(""" data-ff-refresh-url="${escapeHtml(url)}"""")
        if (ms != null && ms > 0) parts.append(""" data-ff-polling-interval="$ms"""")
        if (defer) parts.append(""" data-ff-lazy="1"""")
        parts.append(""" x-data="ffDashboardWidget(${"$"}el)"""")
        return parts.toString()
    }

    protected fun wrapDashboardBody(schema: Schema, inner: String): String {
        val defer = shouldDefer(schema)
        val body = if (defer) {
            """<div class="ff-widget-body" data-ff-widget-body><div class="ff-widget-placeholder">Loading…</div></div>"""
        } else {
            """<div class="ff-widget-body" data-ff-widget-body>$inner</div>"""
        }
        return """<div class="ff-dashboard-widget"${dashboardShellAttrs(schema)}>$body</div>"""
    }

    override fun renderBody(schema: Schema): String {
        return WidgetAssets.tag(WidgetScripts.WIDGET) + wrapDashboardBody(schema, super.renderBody(schema))
    }

    fun render(): String = render(emptyMap())

    fun render(state: Map<String, Any?>): String {
        WidgetAssets.reset()
        return Schema().fill(state).components(this).render()
    }

    companion object {
        @JvmStatic
        fun make(name: String): Widget {
            val widget = Widget(name)
            ComponentConfig.apply(widget)
            return widget
        }
    }
}

/** Parse Filament/Shamar-style `'5s'` / `'500ms'` into milliseconds. */
fun parsePollingIntervalMs(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    val match = Regex("""^(\d+(?:\.\d+)?)\s*(ms|s)?$""", RegexOption.IGNORE_CASE).matchEntire(value.trim())
        ?: return null
    val amount = match.groupValues[1].toDouble()
    val unit = match.groupValues[2].ifEmpty { "s" }.lowercase()
    return if (unit == "ms") amount.toInt() else (amount * 1000).toInt()
}
