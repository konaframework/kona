package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema

class Infolist private constructor() {
    private val schema = Schema.make()

    fun components(vararg components: Component): Infolist = apply { schema.components(*components) }

    fun statePath(path: String): Infolist = apply { schema.statePath(path) }

    fun fill(values: Map<String, Any?>): Infolist = apply { schema.fill(values) }

    fun record(value: Any?): Infolist = apply { schema.record(value) }

    fun record(): Any? = schema.record()

    fun operation(name: String): Infolist = apply { schema.operation(name) }

    fun operation(): String = schema.operation()

    fun hydrate(): Infolist = apply { schema.hydrate() }

    fun render(): String = schema.render()

    fun schema(): Schema = schema

    fun get(path: String): Any? = schema.get(path)

    companion object {
        @JvmStatic
        fun make(): Infolist = Infolist()
    }
}
