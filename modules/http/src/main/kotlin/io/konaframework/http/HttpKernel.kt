package io.konaframework.http

/**
 * HTTP kernel (Laravel Kernel shape): global middleware wraps routing and exception handling, so outer
 * middleware observes 404/405 and handled-exception responses on the way out.
 *
 * After the response is produced, [TerminableMiddleware.terminate] runs for every terminable middleware
 * that participated (global + route), in reverse order.
 */
class HttpKernel @JvmOverloads constructor(
    val router: Router,
    globalMiddleware: List<Middleware> = emptyList(),
    private val exceptionHandler: ExceptionHandler = DefaultExceptionHandler(),
) {
    @Volatile
    private var middleware: List<Middleware> = globalMiddleware.toList()

    @Volatile
    private var priority: List<Class<out Middleware>> = emptyList()

    val globalMiddleware: List<Middleware> get() = middleware

    fun setGlobalMiddleware(value: List<Middleware>): HttpKernel {
        middleware = value.toList()
        return this
    }

    fun pushMiddleware(value: Middleware): HttpKernel {
        if (!hasMiddleware(value)) {
            middleware = middleware + value
        }
        return this
    }

    fun prependMiddleware(value: Middleware): HttpKernel {
        if (!hasMiddleware(value)) {
            middleware = listOf(value) + middleware
        }
        return this
    }

    fun hasMiddleware(value: Middleware): Boolean = middleware.contains(value)

    /**
     * Prefer listed middleware types earlier in the global pipeline (Laravel `$middlewarePriority`).
     */
    fun priority(ordered: List<Class<out Middleware>>): HttpKernel {
        priority = ordered.toList()
        return this
    }

    fun priority(vararg ordered: Class<out Middleware>): HttpKernel = priority(ordered.toList())

    fun handle(request: Request): Response {
        val global = MiddlewarePipeline.sortByPriority(middleware, priority)
        val terminable = mutableListOf<TerminableMiddleware>()
        global.filterIsInstanceTo(terminable)

        val response = try {
            if (global.isEmpty()) {
                dispatchToRouter(request, terminable)
            } else {
                MiddlewarePipeline.pipe(global, Handler { dispatchToRouter(it, terminable) })(request)
            }
        } catch (t: Throwable) {
            exceptionHandler.handle(request, t)
        }
        if (request.method.equals("HEAD", ignoreCase = true)) {
            response.body = ""
        }
        terminable.asReversed().forEach { it.terminate(request, response) }
        return response
    }

    private fun dispatchToRouter(request: Request, terminable: MutableList<TerminableMiddleware>): Response {
        return try {
            val match = router.resolve(request.method, request.path)
            match.parameters.forEach { (k, v) ->
                request.setAttribute("route.$k", v)
                request.attributes[k] = v
            }
            val routeMw = match.route.middleware
            routeMw.filterIsInstanceTo(terminable)
            MiddlewarePipeline.pipe(routeMw, match.route.action)(request)
        } catch (t: Throwable) {
            exceptionHandler.handle(request, t)
        }
    }
}
