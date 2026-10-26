package org.konaframework.firefly.notifications

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

internal class IntSetting(private val fallback: Int) {
    private var literal: Int? = null
    private var using: Function<Get, Int>? = null

    fun setLiteral(value: Int) {
        if (value < 0) throw FireflyException("Duration must be at least 0")
        literal = value
        using = null
    }

    fun setUsing(provider: Function<Get, Int>) {
        using = provider
        literal = null
    }

    fun resolve(get: Get): Int {
        val provider = using
        if (provider != null) {
            val value = provider.apply(get)
            if (value < 0) throw FireflyException("Duration must be at least 0")
            return value
        }
        val chosen = literal
        if (chosen != null) return chosen
        return fallback
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

internal class NoticeAnchor : Component("notice")

internal object SchemaBridge {
    private val getFor = Schema::class.java.methods.first { it.name.startsWith("getFor$") }

    fun reader(schema: Schema, component: Component): Get = getFor.invoke(schema, component) as Get

    fun open(state: Map<String, Any?>): Pair<Schema, Get> {
        val anchor = NoticeAnchor()
        val schema = Schema().fill(state).components(anchor)
        return schema to reader(schema, anchor)
    }
}
