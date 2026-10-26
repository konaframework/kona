package org.konaframework.firefly

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function

/** A tenant the panel can show in the tenant menu and apply to a resource query. */
open class Tenant(val id: String) {
    private var labelLiteral: String = id
    private var labelUsing: Function<Get, String>? = null

    init {
        if (id.isBlank()) throw FireflyException("Tenant id is blank")
    }

    fun label(value: String): Tenant = apply {
        if (value.isBlank()) throw FireflyException("Tenant label is blank")
        labelLiteral = value
        labelUsing = null
    }

    fun label(value: Function<Get, String>): Tenant = apply { labelUsing = value }

    fun label(): String {
        val using = labelUsing
        if (using == null) return labelLiteral
        val text = using.apply(SchemaBridge.open(emptyMap()))
        if (text.isBlank()) throw FireflyException("Tenant label is blank")
        return text
    }
}

/** Request-scoped tenant id. The panel sets it while a page renders. */
object FireflyTenant {
    private val current = ThreadLocal<String?>()

    @JvmStatic
    fun id(): String? = current.get()

    @JvmStatic
    fun <R> use(id: String?, block: java.util.function.Supplier<R>): R {
        val previous = current.get()
        current.set(id)
        try {
            return block.get()
        } finally {
            current.set(previous)
        }
    }
}
