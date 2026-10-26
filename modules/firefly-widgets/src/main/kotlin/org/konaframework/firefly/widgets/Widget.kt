package org.konaframework.firefly.widgets

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.ComponentConfig
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Function

/**
 * A panel widget. It renders on its own and inside a [Page] or [Dashboard].
 *
 * Chainable setters take a static value or a closure that receives [Get].
 */
open class Widget protected constructor(name: String) : Component(name) {
    override fun kind(): String = "widget"

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

    override fun renderBody(schema: Schema): String {
        return WidgetAssets.tag(WidgetScripts.WIDGET) + super.renderBody(schema)
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
