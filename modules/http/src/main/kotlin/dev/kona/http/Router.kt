package dev.kona.http

import java.util.function.Consumer

/**
 * HTTP router with groups, prefixes, named routes, method-set routes, and resource-style registration.
 * Kotlin DSL via [routes] / [group]; Java fluent API via [prefix] / [name] / [middleware] + `group(Consumer)`.
 */
class Router {
    private val routes = mutableListOf<Route>()
    private var prefix: String = ""
    private var namePrefix: String = ""
    private var groupMiddleware: List<Middleware> = emptyList()

    fun get(uri: String, action: Handler): Route = add(listOf("GET"), uri, action)

    fun post(uri: String, action: Handler): Route = add(listOf("POST"), uri, action)

    fun put(uri: String, action: Handler): Route = add(listOf("PUT"), uri, action)

    fun patch(uri: String, action: Handler): Route = add(listOf("PATCH"), uri, action)

    fun delete(uri: String, action: Handler): Route = add(listOf("DELETE"), uri, action)

    fun options(uri: String, action: Handler): Route = add(listOf("OPTIONS"), uri, action)

    fun any(uri: String, action: Handler): Route = add(VERBS, uri, action)

    fun match(methods: Collection<String>, uri: String, action: Handler): Route = add(methods, uri, action)

    /** Kotlin DSL group; Java callers use the `Consumer<Router>` overloads or [prefix]/[name]/[middleware]. */
    @JvmSynthetic
    fun group(
        prefix: String = "",
        name: String = "",
        middleware: List<Middleware> = emptyList(),
        block: Router.() -> Unit,
    ): Router {
        val previousPrefix = this.prefix
        val previousName = this.namePrefix
        val previousMw = this.groupMiddleware
        this.prefix = join(previousPrefix, prefix)
        this.namePrefix = previousName + name
        this.groupMiddleware = previousMw + middleware
        try {
            block()
        } finally {
            this.prefix = previousPrefix
            this.namePrefix = previousName
            this.groupMiddleware = previousMw
        }
        return this
    }

    fun group(prefix: String, block: Consumer<Router>): Router =
        group(prefix = prefix) { block.accept(this) }

    fun group(prefix: String, middleware: List<Middleware>, block: Consumer<Router>): Router =
        group(prefix = prefix, middleware = middleware) { block.accept(this) }

    fun group(prefix: String, name: String, middleware: List<Middleware>, block: Consumer<Router>): Router =
        group(prefix, name, middleware) { block.accept(this) }

    /** Fluent group attributes (Laravel `Route::prefix(...)->middleware(...)->group(...)`). */
    fun prefix(value: String): RouteGroup = RouteGroup(this).prefix(value)

    fun name(value: String): RouteGroup = RouteGroup(this).name(value)

    fun middleware(vararg values: Middleware): RouteGroup = RouteGroup(this).middleware(*values)

    /** Resource-style REST registration: index/show/store/update/destroy. */
    fun resource(name: String, controller: ResourceController): Router {
        get("/$name") { controller.index(it) }.name("$name.index")
        get("/$name/{id}") { controller.show(it) }.name("$name.show")
        post("/$name") { controller.store(it) }.name("$name.store")
        match(listOf("PUT", "PATCH"), "/$name/{id}") { controller.update(it) }.name("$name.update")
        delete("/$name/{id}") { controller.destroy(it) }.name("$name.destroy")
        return this
    }

    fun routes(block: Router.() -> Unit): Router {
        block()
        return this
    }

    fun all(): List<Route> = routes.toList()

    /** First route matching method + path; HEAD falls back to GET routes. Null when nothing matches. */
    fun find(method: String, path: String): MatchResult? {
        val verb = method.uppercase()
        findExact(verb, path)?.let { return it }
        if (verb == "HEAD") {
            return findExact("GET", path)
        }
        return null
    }

    /**
     * Like [find], but throws [NotFoundException] / [MethodNotAllowedException] (with allowed methods), and
     * answers OPTIONS automatically with an `Allow` header when the path exists under other methods.
     */
    fun resolve(method: String, path: String): MatchResult {
        find(method, path)?.let { return it }
        val allowed = allowedMethods(path)
        if (allowed.isEmpty()) {
            throw NotFoundException("The route ${displayPath(path)} could not be found.")
        }
        val verb = method.uppercase()
        if (verb == "OPTIONS") {
            val header = allowed.joinToString(",")
            return MatchResult(Route(listOf("OPTIONS"), path, { Response(200, "").header("Allow", header) }), emptyMap())
        }
        throw MethodNotAllowedException(
            allowed,
            "The $verb method is not supported for route ${displayPath(path)}. " +
                "Supported methods: ${allowed.joinToString(", ")}.",
        )
    }

    /** Methods registered for [path] across all routes; GET implies HEAD. */
    fun allowedMethods(path: String): List<String> {
        val out = linkedSetOf<String>()
        for (route in routes) {
            if (route.matchPath(path) == null) {
                continue
            }
            for (m in route.methods) {
                out.add(m)
                if (m == "GET") {
                    out.add("HEAD")
                }
            }
        }
        return out.toList()
    }

    fun route(name: String, params: Map<String, String> = emptyMap()): String {
        val route = routes.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Route [$name] not defined")
        return route.uriFor(params)
    }

    private fun findExact(method: String, path: String): MatchResult? {
        for (route in routes) {
            val params = route.match(method, path) ?: continue
            return MatchResult(route, params)
        }
        return null
    }

    private fun add(methods: Collection<String>, uri: String, action: Handler): Route {
        val fullUri = join(prefix, uri)
        val route = Route(methods, fullUri, action, groupMiddleware, namePrefix)
        routes.add(route)
        return route
    }

    private fun join(left: String, right: String): String {
        val parts = listOf(left, right)
            .map { it.trim('/') }
            .filter { it.isNotEmpty() }
        return if (parts.isEmpty()) "/" else parts.joinToString("/", prefix = "/")
    }

    private fun displayPath(path: String): String {
        val trimmed = path.trim('/')
        return if (trimmed.isEmpty()) "/" else trimmed
    }

    companion object {
        /** Laravel `Router::$verbs`. */
        @JvmField
        val VERBS: List<String> = listOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    }
}

/** Pending group attributes for the Java fluent API. */
class RouteGroup internal constructor(private val router: Router) {
    private var prefix: String = ""
    private var name: String = ""
    private val middleware = mutableListOf<Middleware>()

    fun prefix(value: String): RouteGroup {
        prefix = value
        return this
    }

    fun name(value: String): RouteGroup {
        name = value
        return this
    }

    fun middleware(vararg values: Middleware): RouteGroup {
        middleware.addAll(values)
        return this
    }

    fun middleware(values: List<Middleware>): RouteGroup {
        middleware.addAll(values)
        return this
    }

    fun group(block: Consumer<Router>): Router =
        router.group(prefix, name, middleware.toList()) { block.accept(this) }
}

data class MatchResult(val route: Route, val parameters: Map<String, String>)

class Route @JvmOverloads constructor(
    methods: Collection<String>,
    val uri: String,
    val action: Handler,
    middleware: List<Middleware> = emptyList(),
    private val nameNamespace: String = "",
) {
    val methods: List<String> = methods.map { it.uppercase() }.distinct()

    init {
        require(this.methods.isNotEmpty()) { "Route $uri must declare at least one method" }
    }

    /** Primary method (first declared). */
    val method: String get() = methods.first()

    var name: String? = null
        private set

    private val middlewareList: MutableList<Middleware> = middleware.toMutableList()

    val middleware: List<Middleware> get() = middlewareList.toList()

    private val paramNames: List<String> =
        Regex("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}").findAll(uri).map { it.groupValues[1] }.toList()

    private val pattern: Regex = run {
        val sb = StringBuilder("^")
        var i = 0
        while (i < uri.length) {
            val c = uri[i]
            if (c == '{') {
                val end = uri.indexOf('}', i)
                require(end > i) { "Unclosed route parameter in $uri" }
                sb.append("([^/]+)")
                i = end + 1
            } else {
                if ("\\.[]{}()*+-?^$|".indexOf(c) >= 0) {
                    sb.append('\\')
                }
                sb.append(c)
                i++
            }
        }
        sb.append('$')
        Regex(sb.toString())
    }

    fun name(value: String): Route {
        name = nameNamespace + value
        return this
    }

    fun middleware(vararg extra: Middleware): Route {
        middlewareList.addAll(extra)
        return this
    }

    fun match(requestMethod: String, path: String): Map<String, String>? {
        if (!methods.contains(requestMethod.uppercase())) {
            return null
        }
        return matchPath(path)
    }

    /** Path-only match (trailing slash ignored, parameters percent-decoded); null when the path differs. */
    fun matchPath(path: String): Map<String, String>? {
        val normalized = if (path.length > 1) path.trimEnd('/').ifEmpty { "/" } else path
        val match = pattern.matchEntire(normalized) ?: return null
        val out = linkedMapOf<String, String>()
        for (index in paramNames.indices) {
            out[paramNames[index]] = decodeSegment(match.groupValues[index + 1])
        }
        return out
    }

    fun uriFor(params: Map<String, String>): String {
        var result = uri
        params.forEach { (k, v) ->
            result = result.replace("{$k}", v)
        }
        return result
    }

    private fun decodeSegment(value: String): String =
        try {
            java.net.URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            value
        }
}

interface ResourceController {
    fun index(request: Request): Response
    fun show(request: Request): Response
    fun store(request: Request): Response
    fun update(request: Request): Response
    fun destroy(request: Request): Response
}
