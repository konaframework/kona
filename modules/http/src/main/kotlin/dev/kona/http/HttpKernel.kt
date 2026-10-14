package dev.kona.http

/**
 * HTTP kernel: global middleware + router dispatch (Laravel Kernel shape).
 */
class HttpKernel(
    val router: Router,
    private val globalMiddleware: List<Middleware> = emptyList(),
    private val exceptionHandler: ExceptionHandler = DefaultExceptionHandler(),
) {
    fun handle(request: Request): Response {
        return try {
            val match = router.find(request.method, request.path)
                ?: throw NotFoundException("No route for ${request.method} ${request.path}")
            match.parameters.forEach { (k, v) ->
                request.setAttribute("route.$k", v)
                request.attributes[k] = v
            }
            val pipeline = MiddlewarePipeline.pipe(globalMiddleware + match.route.middleware) { req ->
                match.route.action(req)
            }
            pipeline(request)
        } catch (t: Throwable) {
            exceptionHandler.handle(request, t)
        }
    }
}
