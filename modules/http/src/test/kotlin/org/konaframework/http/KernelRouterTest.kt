package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class KernelRouterTest {
    private fun tracing(log: MutableList<String>, tag: String) = Middleware { req, next ->
        log.add("$tag:before")
        val response = next(req)
        log.add("$tag:after:${response.status}")
        response.header("X-$tag", "1")
    }

    @Test
    fun globalMiddlewareWrapsRoutingNotFoundAndHandledExceptions() {
        val log = mutableListOf<String>()
        val router = Router()
        router.get("/ok") { Response.text("ok") }
        router.get("/boom") { throw IllegalStateException("boom") }
        router.get("/teapot") { throw HttpException(418, "teapot", mapOf("X-Tea" to "earl-grey")) }
        val kernel = HttpKernel(router, listOf(tracing(log, "outer")))

        val notFound = kernel.handle(Request.get("/missing"))
        assertEquals(404, notFound.status)
        assertEquals("1", notFound.headers["X-outer"])
        assertTrue(notFound.body.contains("The route missing could not be found."))
        assertEquals(listOf("outer:before", "outer:after:404"), log)

        log.clear()
        val boom = kernel.handle(Request.get("/boom"))
        assertEquals(500, boom.status)
        assertEquals("1", boom.headers["X-outer"])
        assertEquals(listOf("outer:before", "outer:after:500"), log)

        val teapot = kernel.handle(Request.get("/teapot"))
        assertEquals(418, teapot.status)
        assertEquals("earl-grey", teapot.headers["X-Tea"])
        assertEquals("1", teapot.headers["X-outer"])

        val root = HttpKernel(Router()).handle(Request.get("/"))
        assertTrue(root.body.contains("The route / could not be found."))
    }

    @Test
    fun globalMiddlewareOrderingAndMutators() {
        val log = mutableListOf<String>()
        val router = Router()
        router.get("/r") { log.add("action"); Response.text("r") }
            .middleware(tracing(log, "route"))
        val a = tracing(log, "a")
        val b = tracing(log, "b")
        val c = tracing(log, "c")
        val kernel = HttpKernel(router)
        assertTrue(kernel.globalMiddleware.isEmpty())
        assertTrue(kernel.pushMiddleware(b) === kernel)
        kernel.pushMiddleware(c).prependMiddleware(a)
        kernel.pushMiddleware(b).prependMiddleware(a)
        assertEquals(listOf(a, b, c), kernel.globalMiddleware)
        assertTrue(kernel.hasMiddleware(a))

        kernel.handle(Request.get("/r"))
        assertEquals(
            listOf(
                "a:before", "b:before", "c:before", "route:before", "action",
                "route:after:200", "c:after:200", "b:after:200", "a:after:200",
            ),
            log,
        )

        kernel.setGlobalMiddleware(listOf(c))
        assertEquals(listOf(c), kernel.globalMiddleware)
        assertFalse(kernel.hasMiddleware(a))
    }

    @Test
    fun exceptionsFromGlobalMiddlewareGoThroughExceptionHandler() {
        val failing = Middleware { _, _ -> throw MethodNotAllowedException() }
        val kernel = HttpKernel(Router(), listOf(failing), DefaultExceptionHandler(debug = true))
        val response = kernel.handle(Request.get("/"))
        assertEquals(405, response.status)
        assertFalse(response.headers.containsKey("Allow"))
        assertTrue(response.body.contains(MethodNotAllowedException::class.java.name))
    }

    @Test
    fun headFallsBackToGetAndStripsBody() {
        val router = Router()
        router.get("/page") { Response.text("body").header("X-Page", "1") }
        router.match(listOf("head"), "/explicit") { Response.text("explicit-head") }
        val kernel = HttpKernel(router)

        val head = kernel.handle(Request("HEAD", "/page"))
        assertEquals(200, head.status)
        assertEquals("", head.body)
        assertEquals("1", head.headers["X-Page"])
        assertEquals("body", kernel.handle(Request.get("/page")).body)
        assertEquals("", kernel.handle(Request("head", "/explicit")).body)
        assertEquals("explicit-head", router.find("HEAD", "/explicit")!!.route.action.handle(Request.get("/")).body)
        assertEquals(404, kernel.handle(Request("HEAD", "/nope")).status)
        assertNull(router.find("POST", "/page"))
    }

    @Test
    fun methodNotAllowedCarriesAllowHeaderAndOptionsIsAutomatic() {
        val router = Router()
        router.get("/items") { Response.text("list") }
        router.post("/items") { Response.text("create") }
        router.delete("/items/{id}") { Response.text("delete") }
        val kernel = HttpKernel(router)

        val put = kernel.handle(Request("PUT", "/items"))
        assertEquals(405, put.status)
        assertEquals("GET, HEAD, POST", put.headers["Allow"])
        assertTrue(put.body.contains("The PUT method is not supported for route items. Supported methods: GET, HEAD, POST."))

        assertEquals("DELETE", kernel.handle(Request.get("/items/3")).headers["Allow"])
        assertEquals(listOf("GET", "HEAD", "POST"), router.allowedMethods("/items"))
        assertEquals(emptyList<String>(), router.allowedMethods("/none"))

        val options = kernel.handle(Request("OPTIONS", "/items"))
        assertEquals(200, options.status)
        assertEquals("GET,HEAD,POST", options.headers["Allow"])
        assertEquals("", options.body)
        assertEquals(404, kernel.handle(Request("OPTIONS", "/none")).status)

        router.options("/items") { Response.text("custom-options") }
        assertEquals("custom-options", kernel.handle(Request("OPTIONS", "/items")).body)

        val ex = assertThrows(MethodNotAllowedException::class.java) { router.resolve("PATCH", "/items") }
        assertEquals(listOf("GET", "HEAD", "POST", "OPTIONS"), ex.allowed)
        assertThrows(NotFoundException::class.java) { router.resolve("GET", "/zzz") }
    }

    @Test
    fun anyMatchOptionsAndRouteShape() {
        val router = Router()
        val any = router.any("/hook") { Response.text(it.method) }
        assertEquals(Router.VERBS, any.methods)
        assertEquals("GET", any.method)
        val kernel = HttpKernel(router)
        for (verb in listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {
            assertEquals(verb, kernel.handle(Request(verb, "/hook")).body)
        }
        assertEquals("", kernel.handle(Request("HEAD", "/hook")).body)

        router.put("/verb") { Response.text("put") }
        router.patch("/verb") { Response.text("patch") }
        assertEquals("put", kernel.handle(Request("PUT", "/verb")).body)
        assertEquals("patch", kernel.handle(Request("PATCH", "/verb")).body)

        val multi = router.match(listOf("put", "PUT", "patch"), "/multi") { Response.text("m") }
        assertEquals(listOf("PUT", "PATCH"), multi.methods)
        assertThrows(IllegalArgumentException::class.java) {
            Route(emptyList(), "/x", { Response.text("x") })
        }
        val direct = Route(listOf("get"), "/d/{id}", { Response.text("d") })
        assertEquals(mapOf("id" to "7"), direct.match("GET", "/d/7"))
        assertNull(direct.match("POST", "/d/7"))
    }

    @Test
    fun pathMatchingTrailingSlashAndDecoding() {
        val router = Router()
        router.get("/") { Response.text("root") }
        router.get("/users/{name}") { Response.text(it.attributes["name"].toString()) }
        val kernel = HttpKernel(router)
        assertEquals("root", kernel.handle(Request.get("/")).body)
        assertEquals("root", kernel.handle(Request.get("//")).body)
        assertEquals("ada", kernel.handle(Request.get("/users/ada/")).body)
        assertEquals("a b+c", kernel.handle(Request.get("/users/a%20b+c")).body)
        assertEquals("%zz", kernel.handle(Request.get("/users/%zz")).body)
        val request = Request.get("/users/x%2Fy")
        kernel.handle(request)
        assertEquals("x/y", request.getAttribute("route.name"))
    }

    @Test
    fun javaFriendlyGroupsAndFluentRegistrar() {
        val log = mutableListOf<String>()
        val mw = tracing(log, "g")
        val router = Router()
        router.group("/a", Consumer { it.get("/one") { Response.text("a1") } })
        router.group("/b", listOf(mw), Consumer { it.get("/two") { Response.text("b2") } })
        router.group("/c", "c.", listOf(mw), Consumer { it.get("/three") { Response.text("c3") }.name("three") })
        router.prefix("/d").name("d.").middleware(mw).middleware(listOf(mw)).group {
            it.get("/four") { Response.text("d4") }.name("four")
        }
        router.name("e.").group { it.get("/five") { Response.text("e5") }.name("five") }
        router.middleware(mw).group { it.get("/six") { Response.text("f6") } }

        val kernel = HttpKernel(router)
        assertEquals("a1", kernel.handle(Request.get("/a/one")).body)
        assertEquals("b2", kernel.handle(Request.get("/b/two")).body)
        assertEquals("/c/three", router.route("c.three"))
        assertEquals("/d/four", router.route("d.four"))
        assertEquals("/five", router.route("e.five"))
        assertEquals("f6", kernel.handle(Request.get("/six")).body)
        assertEquals(2, router.all().first { it.uri == "/d/four" }.middleware.size)

        assertThrows(IllegalStateException::class.java) {
            router.group(prefix = "/x") { error("fail inside group") }
        }
        router.get("/after") { Response.text("after") }
        assertEquals("/after", router.all().last().uri)
        assertTrue(router.all().last().middleware.isEmpty())
    }

    @Test
    fun exceptionEnvelope() {
        val request = Request.get("/")
        val handler = DefaultExceptionHandler()
        val notFound = handler.handle(request, NotFoundException())
        assertEquals(404, notFound.status)
        assertEquals("""{"message":"Not Found"}""", notFound.body)
        val allowed = handler.handle(request, MethodNotAllowedException(listOf("GET", "HEAD")))
        assertEquals("GET, HEAD", allowed.headers["Allow"])
        assertEquals("""{"message":"Method Not Allowed"}""", allowed.body)
        assertEquals(
            """{"message":"Not Found","exception":"org.konaframework.http.NotFoundException"}""",
            DefaultExceptionHandler(true).handle(request, NotFoundException()).body,
        )
        assertEquals(
            """{"message":"x","exception":"java.lang.RuntimeException"}""",
            DefaultExceptionHandler(true).handle(request, RuntimeException("x")).body,
        )
        assertEquals("""{"message":"Server Error"}""", handler.handle(request, RuntimeException("x")).body)
        val validation = handler.handle(
            Request("GET", "/", headers = mapOf("Accept" to "application/json")),
            ValidationException(mapOf("a" to listOf("A bad."))),
        )
        assertEquals(422, validation.status)
        assertEquals("""{"message":"A bad.","errors":{"a":["A bad."]}}""", validation.body)
        val redirected = handler.handle(
            Request("POST", "/form", headers = mapOf("Accept" to "text/html", "Referer" to "/form"), body = "a=1"),
            ValidationException(mapOf("a" to listOf("A bad."))),
        )
        assertEquals(302, redirected.status)
        assertEquals("/form", redirected.headers["Location"])
        assertEquals(mapOf("a" to listOf("A bad.")), redirected.sessionErrors)
        assertEquals(mapOf("a" to "1"), redirected.sessionOldInput)
        assertEquals(emptyMap<String, String>(), HttpException(400, "bad").headers)
    }
}
