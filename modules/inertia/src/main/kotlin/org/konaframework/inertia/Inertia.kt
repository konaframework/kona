package org.konaframework.inertia

import org.konaframework.http.Request
import org.konaframework.http.Response
import java.util.function.Supplier

/**
 * Facade for Inertia page responses (Java-callable statics).
 *
 * Coffee nod: you can think of this as pressing the page into a portable shape
 * the client hydrates — the public name stays **Inertia** for familiarity.
 */
object Inertia {
    @Volatile
    private var manager: InertiaManager? = null

    @JvmStatic
    fun setManager(value: InertiaManager) {
        manager = value
    }

    @JvmStatic
    fun manager(): InertiaManager =
        manager ?: throw InertiaException("InertiaManager is not set; register InertiaServiceProvider")

    @JvmStatic
    fun reset() {
        manager = null
    }

    @JvmStatic
    fun render(component: String): Response = render(component, emptyMap())

    @JvmStatic
    fun render(component: String, props: Map<String, Any?>): Response =
        manager().render(component, props)

    @JvmStatic
    fun render(request: Request, component: String): Response =
        render(request, component, emptyMap())

    @JvmStatic
    fun render(request: Request, component: String, props: Map<String, Any?>): Response =
        manager().render(request, component, props)

    @JvmStatic
    fun share(key: String, value: Any?) {
        manager().share(key, value)
    }

    @JvmStatic
    fun share(props: Map<String, Any?>) {
        manager().share(props)
    }

    @JvmStatic
    fun version(value: String) {
        manager().version = value
    }

    @JvmStatic
    fun location(url: String): Response = manager().location(url)

    @JvmStatic
    fun encryptHistory() {
        encryptHistory(true)
    }

    @JvmStatic
    fun encryptHistory(value: Boolean) {
        manager().encryptHistory(value)
    }

    @JvmStatic
    fun clearHistory() {
        manager().clearHistory()
    }

    /** Optional (lazy) prop — omitted on full visits. */
    @JvmStatic
    fun optional(callback: Supplier<*>): OptionalProp = OptionalProp { callback.get() }

    /** @deprecated Prefer [optional]. */
    @JvmStatic
    fun lazy(callback: Supplier<*>): OptionalProp = optional(callback)

    /**
     * Always included on partial reloads. Pass a value or [Supplier] —
     * callables are invoked when the page is built.
     */
    @JvmStatic
    fun always(value: Any?): AlwaysProp = AlwaysProp(value)

    /**
     * Deferred prop (default group `"default"`).
     * Chain [DeferProp.merge] / [DeferProp.deepMerge] for mergeable deferred data.
     */
    @JvmStatic
    fun defer(callback: Supplier<*>): DeferProp = defer(callback, "default")

    /** Deferred prop in [group] (parallel client fetch per group name). */
    @JvmStatic
    fun defer(callback: Supplier<*>, group: String): DeferProp =
        DeferProp({ callback.get() }, group)

    /** Mergeable prop (append on partial reload). */
    @JvmStatic
    fun merge(value: Any?): MergeProp = MergeProp(value)

    /** Deep-mergeable prop. */
    @JvmStatic
    fun deepMerge(value: Any?): MergeProp = MergeProp(value).deepMerge()
}
