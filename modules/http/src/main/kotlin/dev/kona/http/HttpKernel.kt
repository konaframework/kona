package dev.kona.http

/**
 * HTTP kernel (Laravel Kernel shape): global middleware wraps routing and exception handling, so outer
 * middleware observes 404/405 and handled-exception responses on the way out.
 */
class HttpKernel @JvmOverloads constructor(
    val router: Router,
    globalMiddleware: List<Middleware> = emptyList(),
    private val exceptionHandler: ExceptionHandler = DefaultExceptionHandler(),
) {
    @Volatile
    private var middleware: List<Middleware> = globalMiddleware.toList()

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

    fun handle(request: Request): Response {
        val pipeline = MiddlewarePipeline.pipe(middleware, Handler { dispatchToRouter(it) })
        val response = try {
            pipeline(request)
        } catch (t: Throwable) {
            exceptionHandler.handle(request, t)
        }
        if (request.method.equals("HEAD", ignoreCase = true)) {
            response.body = ""
        }
        return response
    }

    private fun dispatchToRouter(request: Request): Response {
        return try {
            val match = router.resolve(request.method, request.path)
            match.parameters.forEach { (k, v) ->
                request.setAttribute("route.$k", v)
                request.attributes[k] = v
            }
            MiddlewarePipeline.pipe(match.route.middleware, match.route.action)(request)
        } catch (t: Throwable) {
            exceptionHandler.handle(request, t)
        }
    }
}
