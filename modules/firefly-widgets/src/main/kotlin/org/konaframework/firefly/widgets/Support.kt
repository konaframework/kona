package org.konaframework.firefly.widgets

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function

internal fun escapeHtml(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
        if (ch == '&') {
            out.append("&amp;")
        } else if (ch == '<') {
            out.append("&lt;")
        } else if (ch == '>') {
            out.append("&gt;")
        } else if (ch == '"') {
            out.append("&quot;")
        } else {
            out.append(ch)
        }
    }
    return out.toString()
}

internal class TextSetting {
    private var literal: String? = null
    private var using: Function<Get, String>? = null

    fun setLiteral(value: String) {
        literal = value
        using = null
    }

    fun setUsing(provider: Function<Get, String>) {
        using = provider
        literal = null
    }

    fun resolve(get: Get): String? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class AttributeSetting {
    private var literal: Map<String, String> = emptyMap()
    private var using: Function<Get, Map<String, String>>? = null

    fun setLiteral(values: Map<String, String>) {
        literal = LinkedHashMap(values)
        using = null
    }

    fun setUsing(provider: Function<Get, Map<String, String>>) {
        using = provider
        literal = emptyMap()
    }

    fun resolve(get: Get): Map<String, String> {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class PageAnchor : Component("page")

internal object WidgetBridge {
    private val getFor = Schema::class.java.methods.first { it.name.startsWith("getFor$") }

    fun reader(schema: Schema, component: Component): Get = getFor.invoke(schema, component) as Get

    fun open(state: Map<String, Any?>): Get {
        val anchor = PageAnchor()
        val schema = Schema().fill(state).components(anchor)
        return reader(schema, anchor)
    }
}

internal object WidgetAssets {
    private val emitted = LinkedHashSet<String>()

    fun reset() {
        emitted.clear()
    }

    fun tag(path: String): String {
        if (!emitted.add(path)) return ""
        return """<script src="${escapeHtml(path)}"></script>"""
    }
}

object WidgetScripts {
    const val WIDGET: String = "/_firefly/widgets/ff-widget.js"
    const val CHART: String = "/_firefly/widgets/ff-chart.js"
    const val CHART_JS: String = "/_firefly/widgets/chart.umd.js"
    const val APEX: String = "/_firefly/widgets/apexcharts.min.js"

    @JvmStatic
    fun source(publicPath: String): String {
        val name = publicPath.substringAfterLast('/')
        val stream = WidgetScripts::class.java.getResourceAsStream("/firefly/$name")
        if (stream == null) throw FireflyException("Missing widget asset [$name]")
        return stream.readAllBytes().toString(Charsets.UTF_8)
    }
}
