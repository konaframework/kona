package dev.kona.http

fun interface Middleware {
    fun handle(request: Request, next: Handler): Response
}

object MiddlewarePipeline {
    @JvmStatic
    fun pipe(middlewares: List<Middleware>, destination: Handler): Handler {
        return middlewares.foldRight(destination) { middleware, next ->
            Handler { request -> middleware.handle(request, next) }
        }
    }
}
