package io.konaframework.http

import io.konaframework.core.Application
import java.util.function.Supplier

/**
 * Bootstrap middleware configurator (Almasix / Laravel `bootstrap` middleware shape).
 *
 * Register aliases, `web` / `api` stacks, and optional trust proxies/hosts, then [apply] onto
 * the application's [HttpKernel] after boot.
 */
class HttpMiddleware {
    private val aliases = linkedMapOf<String, Supplier<Middleware>>()
    private val web = mutableListOf<String>()
    private val api = mutableListOf<String>()
    private var trustProxies: TrustProxies? = null
    private var trustHosts: TrustHosts? = null

    fun alias(name: String, factory: Supplier<Middleware>): HttpMiddleware {
        aliases[name] = factory
        return this
    }

    fun alias(map: Map<String, Supplier<Middleware>>): HttpMiddleware {
        map.forEach { (name, factory) -> alias(name, factory) }
        return this
    }

    /** Kotlin-friendly alias registration. */
    fun alias(name: String, factory: () -> Middleware): HttpMiddleware =
        alias(name, Supplier { factory() })

    @JvmOverloads
    fun web(prepend: List<String> = emptyList(), append: List<String> = emptyList()): HttpMiddleware {
        web.clear()
        web.addAll(prepend)
        web.addAll(append)
        return this
    }

    @JvmOverloads
    fun api(prepend: List<String> = emptyList(), append: List<String> = emptyList()): HttpMiddleware {
        api.clear()
        api.addAll(prepend)
        api.addAll(append)
        return this
    }

    @JvmOverloads
    fun trustProxies(at: List<String> = listOf("*"), headers: Set<String> = TrustProxies.DEFAULT_HEADERS): HttpMiddleware {
        trustProxies = TrustProxies(at, headers)
        return this
    }

    fun trustHosts(at: List<String>): HttpMiddleware {
        trustHosts = TrustHosts(at)
        return this
    }

    fun webStack(): List<Middleware> = resolve(web)

    fun apiStack(): List<Middleware> = resolve(api)

    /**
     * Pushes trust middleware and the `web` stack onto the global kernel.
     * The `api` stack is available via [apiStack] for route groups.
     */
    fun apply(app: Application) {
        val kernel = app.resolve(HttpKernel::class.java)
        trustProxies?.let { kernel.prependMiddleware(it) }
        trustHosts?.let { kernel.prependMiddleware(it) }
        webStack().forEach { kernel.pushMiddleware(it) }
        app.container.instance(HttpMiddleware::class.java, this)
    }

    private fun resolve(names: List<String>): List<Middleware> =
        names.map { name ->
            val factory = aliases[name]
                ?: throw IllegalArgumentException("Unknown middleware alias [$name]")
            factory.get()
        }
}
