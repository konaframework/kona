package org.konaframework.firefly

import org.konaframework.cadence.Cadence
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.Request
import org.konaframework.http.Response
import java.util.function.Function
import java.util.function.Supplier

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

/**
 * Implement on the user model to use a model-backed tenant ([Panel.tenant] with a model class).
 * The panel asks [canAccessTenant] on every request it serves for a tenant and answers 403 when it
 * returns false. [getTenants] feeds the tenant menu and the page a user lands on after sign-in.
 */
interface HasTenants {
    /** The tenants this user belongs to in [panel], as instances of the panel's tenant model. */
    fun getTenants(panel: Panel): Collection<Any>

    /** Whether the user may work in [tenant], an instance of the panel's tenant model. */
    fun canAccessTenant(tenant: Any): Boolean
}

/** Optional on the user model: the tenant a user lands on when none was used yet in this session. */
interface HasDefaultTenant {
    /** The tenant to open first in [panel], or null to fall back to the first of [HasTenants.getTenants]. */
    fun getDefaultTenant(panel: Panel): Any?
}

/**
 * Runs around every request the panel serves for a tenant, after access was granted
 * ([Panel.tenantMiddleware]). Set app-wide context for [tenant] here, such as a Cadence global
 * scope, then call [next] and return its response.
 */
fun interface TenantMiddleware {
    fun handle(request: Request, tenant: Any, next: Supplier<Response>): Response
}

/**
 * The tenant of the request on this thread. A model-backed tenant ([Panel.tenant] with a model
 * class) is available through [current]; [id] is its key. A static [Tenant] only sets [id].
 */
object FireflyTenant {
    private class State(val id: String?, val model: Any?)

    private val state = ThreadLocal<State?>()

    /** The tenant key (or static tenant id) of the current request, or null. */
    @JvmStatic
    fun id(): String? = state.get()?.id

    /** The tenant model of the current request, or null when the panel has no model-backed tenant. */
    @JvmStatic
    fun current(): Any? = state.get()?.model

    /** [current] as [type]; throws [ClassCastException] when the tenant is another model. */
    @JvmStatic
    fun <T> current(type: Class<T>): T? = type.cast(current())

    @JvmStatic
    fun <R> use(id: String?, block: Supplier<R>): R = run(State(id, null), block)

    /** Runs [block] with [tenant] as the current tenant model; [id] becomes its key. */
    @JvmStatic
    fun <R> useModel(tenant: Cadence<*>, block: Supplier<R>): R = run(State(tenant.getKey().toString(), tenant), block)

    private fun <R> run(next: State, block: Supplier<R>): R {
        val previous = state.get()
        state.set(next)
        try {
            return block.get()
        } finally {
            state.set(previous)
        }
    }
}
