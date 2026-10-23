package org.konaframework.brew

import org.konaframework.http.Handler
import org.konaframework.http.Route
import org.konaframework.http.Router
import org.konaframework.wire.Component
import org.konaframework.wire.Wire
import org.konaframework.wire.WireConfig
import org.konaframework.wire.WireException
import org.konaframework.wire.WireManager
import org.konaframework.wire.WireServiceProvider
import org.konaframework.wire.WireView
import org.konaframework.wire.hydration.Synth
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.testing.Testable
import java.util.function.Function
import java.util.function.Supplier

/** @deprecated Use [org.konaframework.wire.WireManager]. */
@Deprecated("Use org.konaframework.wire.WireManager", ReplaceWith("WireManager", "org.konaframework.wire.WireManager"))
typealias BrewManager = WireManager

/** @deprecated Use [org.konaframework.wire.WireConfig]. */
@Deprecated("Use org.konaframework.wire.WireConfig", ReplaceWith("WireConfig", "org.konaframework.wire.WireConfig"))
typealias BrewConfig = WireConfig

/** @deprecated Use [org.konaframework.wire.WireView]. */
@Deprecated("Use org.konaframework.wire.WireView", ReplaceWith("WireView", "org.konaframework.wire.WireView"))
typealias BrewView = WireView

/** @deprecated Use [org.konaframework.wire.WireException]. */
@Deprecated("Use org.konaframework.wire.WireException", ReplaceWith("WireException", "org.konaframework.wire.WireException"))
typealias BrewException = WireException

/** @deprecated Use [org.konaframework.wire.WireComponent]. */
@Deprecated("Use org.konaframework.wire.WireComponent", ReplaceWith("WireComponent", "org.konaframework.wire.WireComponent"))
typealias BrewComponent = org.konaframework.wire.BrewComponent

/**
 * Deprecated 0.2 provider FQCN. New apps should register [WireServiceProvider].
 */
@Deprecated("Use org.konaframework.wire.WireServiceProvider")
class BrewServiceProvider : WireServiceProvider()

/**
 * Deprecated facade over [Wire]. Prefer `org.konaframework.wire.Wire`.
 */
@Deprecated("Use org.konaframework.wire.Wire", ReplaceWith("Wire", "org.konaframework.wire.Wire"))
object Brew {
    @JvmStatic
    fun setManager(value: WireManager) = Wire.setManager(value)

    @JvmStatic
    fun manager(): WireManager = Wire.manager()

    @JvmStatic
    fun reset() = Wire.reset()

    @JvmStatic
    @JvmOverloads
    fun mount(name: String, params: Map<String, Any?> = emptyMap(), key: String? = null): String =
        Wire.mount(name, params, key)

    @JvmStatic
    fun <T> withoutLazyLoading(block: () -> T): T = Wire.withoutLazyLoading(block)

    @JvmStatic
    fun component(name: String, type: Class<out Component>): WireManager = Wire.component(name, type)

    @JvmStatic
    fun component(type: Class<out Component>): WireManager = Wire.component(type)

    @JvmStatic
    fun component(name: String, factory: Supplier<out Component>): WireManager = Wire.component(name, factory)

    @JvmStatic
    fun addNamespace(namespace: String, packageName: String): WireManager = Wire.addNamespace(namespace, packageName)

    @JvmStatic
    fun addLocation(packageName: String): WireManager = Wire.addLocation(packageName)

    @JvmStatic
    fun discover(packageName: String): WireManager = Wire.discover(packageName)

    @JvmStatic
    fun exists(name: String): Boolean = Wire.exists(name)

    @JvmStatic
    fun componentHook(factory: Supplier<out ComponentHook>): WireManager = Wire.componentHook(factory)

    @JvmStatic
    fun propertySynthesizer(synth: Synth): WireManager = Wire.propertySynthesizer(synth)

    @JvmStatic
    fun releaseToken(token: String?): WireManager = Wire.releaseToken(token)

    @JvmStatic
    fun addPersistentMiddleware(vararg middleware: org.konaframework.http.Middleware): WireManager =
        Wire.addPersistentMiddleware(*middleware)

    @JvmStatic
    fun updateMiddleware(vararg middleware: org.konaframework.http.Middleware): WireManager =
        Wire.updateMiddleware(*middleware)

    @JvmStatic
    fun current(): Component? = Wire.current()

    @JvmStatic
    fun isBrewRequest(): Boolean = Wire.isWireRequest()

    @JvmStatic
    fun isWireRequest(): Boolean = Wire.isWireRequest()

    @JvmStatic
    fun setUpdateRoute(registrar: Function<Handler, Route>): Route = Wire.setUpdateRoute(registrar)

    @JvmStatic
    fun setScriptRoute(registrar: Function<Handler, Route>): Route = Wire.setScriptRoute(registrar)

    @JvmStatic
    fun route(router: Router, uri: String, name: String): Route = Wire.route(router, uri, name)

    @JvmStatic
    fun styles(): String = Wire.styles()

    @JvmStatic
    fun scripts(): String = Wire.scripts()

    @JvmStatic
    fun scriptConfig(): String = Wire.scriptConfig()

    @JvmStatic
    @JvmOverloads
    fun test(name: String, params: Map<String, Any?> = emptyMap()): Testable = Wire.test(name, params)

    @JvmStatic
    @JvmOverloads
    fun test(type: Class<out Component>, params: Map<String, Any?> = emptyMap()): Testable = Wire.test(type, params)
}
