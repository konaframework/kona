package dev.kona.http

fun interface Middleware {
    fun handle(request: Request, next: (Request) -> Response): Response
}

object MiddlewarePipeline {
    fun pipe(middlewares: List<Middleware>, destination: (Request) -> Response): (Request) -> Response {
        return middlewares.foldRight(destination) { middleware, next ->
            { request -> middleware.handle(request, next) }
        }
    }
}
