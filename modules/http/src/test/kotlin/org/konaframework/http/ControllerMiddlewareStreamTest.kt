package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class ControllerMiddlewareStreamTest {
    private class FlagMiddleware(private val flag: String) : Middleware {
        override fun handle(request: Request, next: Handler): Response {
            request.setAttribute(flag, true)
            return next.handle(request)
        }
    }

    private class SampleController : Controller() {
        init {
            middleware(FlagMiddleware("all"))
            middleware(FlagMiddleware("only-show"), only = listOf("show"))
            middleware(FlagMiddleware("except-index"), except = listOf("index"))
        }

        fun index(request: Request): Response = Response.text("index")

        fun show(request: Request): Response = Response.text("show")
    }

    @Test
    fun controllerMiddlewareOnlyExceptAndActionWrapper() {
        val controller = SampleController()
        assertEquals(1, controller.middlewareFor("index").size)
        assertEquals(3, controller.middlewareFor("show").size)

        val req = Request.get("/x")
        val response = controller.action("show") { controller.show(it) }.handle(req)
        assertEquals("show", response.body)
        assertEquals(true, req.getAttribute("all"))
        assertEquals(true, req.getAttribute("only-show"))
        assertEquals(true, req.getAttribute("except-index"))

        val indexReq = Request.get("/i")
        controller.action("index") { controller.index(it) }.handle(indexReq)
        assertEquals(true, indexReq.getAttribute("all"))
        assertNull(indexReq.getAttribute("only-show"))
        assertNull(indexReq.getAttribute("except-index"))
    }

    @Test
    fun resourceRoutesApplyControllerMiddleware() {
        val controller = object : BaseResourceController() {
            init {
                middleware(FlagMiddleware("res"))
            }

            override fun index(request: Request): Response = Response.text("ok")
        }
        val router = Router().resource("posts", controller, only = listOf("index"))
        val match = router.find("GET", "/posts")!!
        val request = Request.get("/posts")
        match.route.action.handle(request)
        assertEquals(true, request.getAttribute("res"))
    }

    @Test
    fun responseStreamBuffersWriter() {
        val response = Response.stream(
            { out -> out.write("hello-stream".toByteArray(StandardCharsets.UTF_8)) },
            "text/plain; charset=utf-8",
            201,
        )
        assertEquals(201, response.status)
        assertEquals("text/plain; charset=utf-8", response.headers["Content-Type"])
        assertEquals("hello-stream", String(response.bodyBytes(), StandardCharsets.UTF_8))
        assertTrue(response.body.isEmpty())
    }

    @Test
    fun actionWithoutMiddlewareReturnsHandlerDirectly() {
        val bare = object : Controller() {
            fun ping(request: Request): Response = Response.text("pong")
        }
        assertTrue(bare.middlewareFor("ping").isEmpty())
        val handler = bare.action("ping") { bare.ping(it) }
        assertEquals("pong", handler.handle(Request.get("/")).body)
    }
}
