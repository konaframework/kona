package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import kotlin.jvm.functions.Function1

internal object SchemaBridge {
    private val ctor = Get::class.java.getConstructor(Function1::class.java)

    fun open(schema: Schema, component: Component): Get {
        val method = Schema::class.java.getMethod("getFor\$kona_firefly_schemas", Component::class.java)
        return method.invoke(schema, component) as Get
    }

    fun overlay(schema: Schema, component: Component, lookup: (String) -> Any?): Get {
        val base = open(schema, component)
        val fn = object : Function1<String, Any?> {
            override fun invoke(path: String): Any? {
                if (path.startsWith("/")) return schema.get(path.substring(1))
                val over = lookup(path)
                if (over != MISS) return over
                return base.get(path)
            }
        }
        return ctor.newInstance(fn) as Get
    }
}
