package org.konaframework.inertia

import org.konaframework.http.Request
import org.konaframework.http.Response

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
}
