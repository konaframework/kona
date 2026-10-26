package org.konaframework.firefly.forms

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema

/**
 * Schema's relative [Get] and child list are module-internal. Fields live in this module
 * and read them through the public JVM names those methods compile to.
 */
internal object Reads {
    fun of(schema: Schema, component: Component): Get {
        val method = schema.javaClass.getMethod("getFor\$kona_firefly_schemas", Component::class.java)
        return method.invoke(schema, component) as Get
    }

    fun children(component: Component): List<Component> {
        val method = component.javaClass.getMethod("allChildren\$kona_firefly_schemas")
        @Suppress("UNCHECKED_CAST")
        return method.invoke(component) as List<Component>
    }

    fun walk(roots: Iterable<Component>): List<Component> {
        val out = ArrayList<Component>()
        fun visit(component: Component) {
            out.add(component)
            for (child in children(component)) visit(child)
        }
        for (root in roots) visit(root)
        return out
    }
}
