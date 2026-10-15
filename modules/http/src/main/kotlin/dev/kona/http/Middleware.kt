package dev.kona.http

fun interface Middleware {
    fun handle(request: Request, next: Handler): Response
}

/**
 * Middleware that runs [terminate] after the response is produced (Laravel terminable middleware).
 * The kernel invokes [terminate] once the response leaves the pipeline.
 */
interface TerminableMiddleware : Middleware {
    fun terminate(request: Request, response: Response)
}

object MiddlewarePipeline {
    @JvmStatic
    fun pipe(middlewares: List<Middleware>, destination: Handler): Handler {
        return middlewares.foldRight(destination) { middleware, next ->
            Handler { request -> middleware.handle(request, next) }
        }
    }

    /**
     * Sort [middlewares] so types listed earlier in [priority] run first (outermost).
     * Unlisted middleware keep relative order after prioritized entries.
     */
    @JvmStatic
    fun sortByPriority(middlewares: List<Middleware>, priority: List<Class<out Middleware>>): List<Middleware> {
        if (priority.isEmpty() || middlewares.size <= 1) {
            return middlewares
        }
        val rank = priority.withIndex().associate { (index, type) -> type to index }
        return middlewares
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<Middleware>> { indexed ->
                    priorityRank(indexed.value, rank)
                }.thenBy { it.index },
            )
            .map { it.value }
    }

    private fun priorityRank(middleware: Middleware, rank: Map<Class<out Middleware>, Int>): Int {
        for ((type, index) in rank) {
            if (type.isInstance(middleware)) {
                return index
            }
        }
        return Int.MAX_VALUE
    }
}
