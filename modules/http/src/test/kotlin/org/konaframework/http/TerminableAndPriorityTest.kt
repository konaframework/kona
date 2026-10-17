package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class TerminableAndPriorityTest {
    private class Trace(
        private val name: String,
        private val log: MutableList<String>,
    ) : TerminableMiddleware {
        override fun handle(request: Request, next: Handler): Response {
            log += "in:$name"
            val response = next.handle(request)
            log += "out:$name"
            return response
        }

        override fun terminate(request: Request, response: Response) {
            log += "term:$name"
        }
    }

    private class Plain(private val name: String, private val log: MutableList<String>) : Middleware {
        override fun handle(request: Request, next: Handler): Response {
            log += "in:$name"
            val response = next.handle(request)
            log += "out:$name"
            return response
        }
    }

    @Test
    fun terminableRunsAfterResponseInReverse() {
        val log = CopyOnWriteArrayList<String>()
        val router = Router().apply {
            get("/x") { Response.text("ok") }
        }
        val kernel = HttpKernel(
            router,
            listOf(Trace("a", log), Trace("b", log)),
        )
        val response = kernel.handle(Request.get("/x"))
        assertEquals("ok", response.body)
        assertEquals(
            listOf("in:a", "in:b", "out:b", "out:a", "term:b", "term:a"),
            log.toList(),
        )
    }

    @Test
    fun routeTerminableCollected() {
        val log = CopyOnWriteArrayList<String>()
        val routeMw = Trace("route", log)
        val router = Router().apply {
            get("/x") { Response.text("ok") }.middleware(routeMw)
        }
        val kernel = HttpKernel(router, listOf(Trace("global", log)))
        kernel.handle(Request.get("/x"))
        assertEquals(
            listOf("in:global", "in:route", "out:route", "out:global", "term:route", "term:global"),
            log.toList(),
        )
    }

    @Test
    fun priorityReordersGlobalMiddleware() {
        val log = CopyOnWriteArrayList<String>()
        val late = Plain("late", log)
        val early = Plain("early", log)
        val router = Router().apply { get("/") { Response.text("ok") } }
        val kernel = HttpKernel(router, listOf(late, early))
            .priority(Plain::class.java) // both same class — stable by index when equal rank
        // Prefer explicit types via subclasses for ordering:
        val log2 = CopyOnWriteArrayList<String>()
        class First : Middleware {
            override fun handle(request: Request, next: Handler): Response {
                log2 += "first"
                return next.handle(request)
            }
        }
        class Second : Middleware {
            override fun handle(request: Request, next: Handler): Response {
                log2 += "second"
                return next.handle(request)
            }
        }
        val first = First()
        val second = Second()
        val kernel2 = HttpKernel(
            Router().apply { get("/") { Response.text("ok") } },
            listOf(second, first),
        ).priority(First::class.java, Second::class.java)
        kernel2.handle(Request.get("/"))
        assertEquals(listOf("first", "second"), log2.toList())

        // empty priority / single leave order
        assertEquals(listOf(late, early), MiddlewarePipeline.sortByPriority(listOf(late, early), emptyList()))
        assertEquals(listOf(late), MiddlewarePipeline.sortByPriority(listOf(late), listOf(Plain::class.java)))
        kernel.handle(Request.get("/"))
        assertEquals(listOf("in:late", "in:early", "out:early", "out:late"), log.toList())
    }
}
