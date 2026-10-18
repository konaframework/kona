package org.konaframework.brew

import org.konaframework.brew.features.brewRoute
import org.konaframework.brew.hydration.Synth
import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.testing.Testable
import org.konaframework.http.Handler
import org.konaframework.http.Route
import org.konaframework.http.Router
import java.util.function.Function
import java.util.function.Supplier

/** Static facade over the application's [BrewManager] (`Livewire::` facade). */
object Brew {
    @Volatile
    private var manager: BrewManager? = null

    @JvmStatic
    fun setManager(value: BrewManager) {
        manager = value
    }

    @JvmStatic
    fun manager(): BrewManager = manager ?: throw BrewException("BrewManager is not set; register BrewServiceProvider")

    @JvmStatic
    fun reset() {
        manager = null
    }

    @JvmStatic
    @JvmOverloads
    fun mount(name: String, params: Map<String, Any?> = emptyMap(), key: String? = null): String =
        manager().mount(name, params, key)

    /** Run [block] with lazy/defer disabled (`Livewire::withoutLazyLoading`). */
    @JvmStatic
    fun <T> withoutLazyLoading(block: () -> T): T = manager().withoutLazyLoading(block)

    @JvmStatic
    fun component(name: String, type: Class<out Component>): BrewManager = manager().component(name, type)

    @JvmStatic
    fun component(type: Class<out Component>): BrewManager = manager().component(type)

    @JvmStatic
    fun component(name: String, factory: Supplier<out Component>): BrewManager = manager().component(name, factory)

    @JvmStatic
    fun addNamespace(namespace: String, packageName: String): BrewManager {
        manager().registry.addNamespace(namespace, packageName)
        return manager()
    }

    @JvmStatic
    fun addLocation(packageName: String): BrewManager {
        manager().registry.addLocation(packageName)
        return manager()
    }

    @JvmStatic
    fun discover(packageName: String): BrewManager {
        manager().registry.discover(packageName)
        return manager()
    }

    @JvmStatic
    fun exists(name: String): Boolean = manager().registry.has(name)

    @JvmStatic
    fun componentHook(factory: Supplier<out ComponentHook>): BrewManager = manager().componentHook(factory)

    @JvmStatic
    fun propertySynthesizer(synth: Synth): BrewManager = manager().propertySynthesizer(synth)

    @JvmStatic
    fun releaseToken(token: String?): BrewManager = manager().releaseToken(token)

    @JvmStatic
    fun addPersistentMiddleware(vararg middleware: org.konaframework.http.Middleware): BrewManager =
        manager().addPersistentMiddleware(*middleware)

    @JvmStatic
    fun updateMiddleware(vararg middleware: org.konaframework.http.Middleware): BrewManager =
        manager().updateMiddleware(*middleware)

    /** Component currently executing on this thread (`Livewire::current()`). */
    @JvmStatic
    fun current(): Component? = manager().current()

    /** True while handling `POST /brew/update` (`Livewire::isLivewireRequest()`). */
    @JvmStatic
    fun isBrewRequest(): Boolean = manager().currentRequest()?.getAttribute(BrewManager.UPDATE_ATTRIBUTE) == true

    @JvmStatic
    fun setUpdateRoute(registrar: Function<Handler, Route>): Route = manager().setUpdateRoute(registrar)

    @JvmStatic
    fun setScriptRoute(registrar: Function<Handler, Route>): Route = manager().setScriptRoute(registrar)

    /** Full-page component route (`Route::livewire` / `router.brew`). */
    @JvmStatic
    fun route(router: Router, uri: String, name: String): Route =
        brewRoute(router, uri, name, manager())

    @JvmStatic
    fun styles(): String = manager().assets.styles()

    @JvmStatic
    fun scripts(): String = manager().assets.scripts()

    @JvmStatic
    fun scriptConfig(): String = manager().assets.scriptConfig()

    /** `Livewire::test(...)`. */
    @JvmStatic
    @JvmOverloads
    fun test(name: String, params: Map<String, Any?> = emptyMap()): Testable = Testable.create(manager(), name, params)

    @JvmStatic
    @JvmOverloads
    fun test(type: Class<out Component>, params: Map<String, Any?> = emptyMap()): Testable {
        val m = manager()
        val name = m.registry.nameOf(type)
        m.component(name, type)
        return Testable.create(m, name, params)
    }
}
