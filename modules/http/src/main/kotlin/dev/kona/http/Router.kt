package dev.kona.http

/**
 * HTTP router with groups, prefixes, named routes, and resource-style registration.
 * Kotlin DSL via [routes]; Java fluent API via chainable methods.
 */
class Router {
    private val routes = mutableListOf<Route>()
    private var prefix: String = ""
    private var namePrefix: String = ""
    private var groupMiddleware: List<Middleware> = emptyList()

    fun get(uri: String, action: (Request) -> Response): Route = add("GET", uri, action)

    fun post(uri: String, action: (Request) -> Response): Route = add("POST", uri, action)

    fun put(uri: String, action: (Request) -> Response): Route = add("PUT", uri, action)

    fun patch(uri: String, action: (Request) -> Response): Route = add("PATCH", uri, action)

    fun delete(uri: String, action: (Request) -> Response): Route = add("DELETE", uri, action)

    fun match(methods: Collection<String>, uri: String, action: (Request) -> Response): List<Route> =
        methods.map { add(it.uppercase(), uri, action) }

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
        block()
        this.prefix = previousPrefix
        this.namePrefix = previousName
        this.groupMiddleware = previousMw
        return this
    }

    /** Resource-style REST registration: index/show/store/update/destroy. */
    fun resource(name: String, controller: ResourceController): Router {
        get("/$name") { controller.index(it) }.name("$name.index")
        get("/$name/{id}") { req -> controller.show(req) }.name("$name.show")
        post("/$name") { controller.store(it) }.name("$name.store")
        put("/$name/{id}") { controller.update(it) }.name("$name.update")
        patch("/$name/{id}") { controller.update(it) }
        delete("/$name/{id}") { controller.destroy(it) }.name("$name.destroy")
        return this
    }

    fun routes(block: Router.() -> Unit): Router {
        block()
        return this
    }

    fun all(): List<Route> = routes.toList()

    fun find(method: String, path: String): MatchResult? {
        for (route in routes) {
            val params = route.match(method, path) ?: continue
            return MatchResult(route, params)
        }
        return null
    }

    fun route(name: String, params: Map<String, String> = emptyMap()): String {
        val route = routes.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Route [$name] not defined")
        return route.uriFor(params)
    }

    private fun add(method: String, uri: String, action: (Request) -> Response): Route {
        val fullUri = join(prefix, uri)
        val route = Route(method, fullUri, action, groupMiddleware.toMutableList(), namePrefix)
        routes.add(route)
        return route
    }

    private fun join(left: String, right: String): String {
        val parts = listOf(left, right)
            .map { it.trim('/') }
            .filter { it.isNotEmpty() }
        return if (parts.isEmpty()) "/" else parts.joinToString("/", prefix = "/")
    }
}

data class MatchResult(val route: Route, val parameters: Map<String, String>)

class Route(
    val method: String,
    val uri: String,
    val action: (Request) -> Response,
    private val middlewareList: MutableList<Middleware> = mutableListOf(),
    private val nameNamespace: String = "",
) {
    var name: String? = null
        private set

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
        if (method.uppercase() != requestMethod.uppercase()) {
            return null
        }
        val match = pattern.matchEntire(path)
        if (match == null) {
            return null
        }
        val out = linkedMapOf<String, String>()
        for (index in paramNames.indices) {
            out[paramNames[index]] = match.groupValues[index + 1]
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
}

interface ResourceController {
    fun index(request: Request): Response
    fun show(request: Request): Response
    fun store(request: Request): Response
    fun update(request: Request): Response
    fun destroy(request: Request): Response
}
