package dev.kona.http

import dev.kona.core.Application
import dev.kona.core.Kona
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files

class HttpStackTest {
    @AfterEach
    fun cleanup() {
        Kona.reset()
    }

    @Test
    fun requestResponseValidationRouterKernel() {
        val req = Request(
            "POST",
            "/users",
            headers = mapOf("Content-Type" to "application/json"),
            query = mapOf("q" to "1"),
            body = """{"name":"Ada","email":"ada@example.com","n":null,"i":1}""",
        )
        assertEquals("Ada", req.input("name"))
        assertEquals("1", req.input("q"))
        assertEquals("d", req.input("missing", "d"))
        assertEquals(null, req.input("missing"))
        assertEquals("application/json", req.header("content-type"))
        assertEquals(null, req.header("Nope"))
        assertEquals("d", req.header("Nope", "d"))
        assertTrue(req.all().containsKey("name"))

        val form = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = "a=b&lonely&c=",
        )
        assertEquals("b", form.form()["a"])
        assertEquals("", form.form()["c"])
        assertEquals("", form.form()["lonely"])
        assertEquals(emptyMap<String, String>(), Request.post("/").form())
        assertEquals("b", Request.post("/", "a=b").form()["a"])
        assertEquals("b", Request("POST", "/", body = "a=b").form()["a"])
        assertEquals(emptyMap<String, String>(), JsonSupport.parseFlat(""))
        assertEquals(emptyMap<String, String>(), JsonSupport.parseFlat("[1]"))
        assertEquals("", JsonSupport.parseFlat("""{"n":null}""")["n"])
        assertEquals("1", JsonSupport.parseFlat("""{"n":1}""")["n"])

        assertEquals("ok", Response.text("ok").body)
        assertEquals(201, Response.text("x", 201).status)
        assertEquals("hi", Response.html("hi").body)
        assertEquals(201, Response.html("hi", 201).status)
        assertTrue(Response.json(mapOf("a" to 1)).body.contains("\"a\""))
        assertEquals(201, Response.json(mapOf("a" to 1), 201).status)
        assertEquals(204, Response.noContent().status)
        assertEquals(201, Response.text("x").withStatus(201).withBody("y").header("X", "1").status)

        val router = Router()
        router.routes {
            get("/hi") { Response.text("hi") }.name("hi")
            group(prefix = "/api", name = "api.", middleware = listOf(Middleware { r, next -> next(r) })) {
                get("/ping") { Response.json(mapOf("pong" to true)) }.name("ping")
            }
            group { get("/bare") { Response.text("bare") } }
            group(prefix = "v2") { get("x") { Response.text("v2") } }
            match(listOf("PUT", "PATCH"), "/item") { Response.text(it.method) }
            resource(
                "posts",
                object : ResourceController {
                    override fun index(request: Request) = Response.text("index")
                    override fun show(request: Request) = Response.text("show:" + request.attributes["id"])
                    override fun store(request: Request) = Response.text("store")
                    override fun update(request: Request) = Response.text("update")
                    override fun destroy(request: Request) = Response.text("destroy")
                },
            )
        }
        router.get("/mw") { Response.text("mw") }
            .middleware(Middleware { req, next -> next(req).header("X-MW", "1") })

        assertEquals("/hi", router.route("hi"))
        assertEquals("/api/ping", router.route("api.ping"))
        assertEquals("/posts/5", router.route("posts.show", mapOf("id" to "5")))
        assertThrows(IllegalArgumentException::class.java) { router.route("nope") }
        assertTrue(router.all().isNotEmpty())

        val kernel = HttpKernel(router)
        assertEquals("hi", kernel.handle(Request.get("/hi")).body)
        assertEquals(404, kernel.handle(Request.get("/missing")).status)
        assertEquals("1", kernel.handle(Request.get("/mw")).headers["X-MW"])
        assertEquals("index", kernel.handle(Request.get("/posts")).body)
        assertEquals("show:9", kernel.handle(Request.get("/posts/9")).body)
        assertEquals("store", kernel.handle(Request.post("/posts")).body)
        assertEquals("update", kernel.handle(Request("PUT", "/posts/1")).body)
        assertEquals("update", kernel.handle(Request("PATCH", "/posts/1")).body)
        assertEquals("destroy", kernel.handle(Request("DELETE", "/posts/1")).body)
        assertEquals("bare", kernel.handle(Request.get("/bare")).body)
        assertEquals("v2", kernel.handle(Request.get("/v2/x")).body)
        assertEquals("PUT", kernel.handle(Request("PUT", "/item")).body)
        assertThrows(IllegalArgumentException::class.java) {
            Router().get("/bad/{id") { Response.text("x") }
        }
        router.get("/file.json") { Response.text("json-file") }
        assertEquals("json-file", kernel.handle(Request.get("/file.json")).body)
        assertEquals(null, Request.get("/").getAttribute("missing"))
        // method mismatch on parameterized route
        assertEquals(405, kernel.handle(Request.post("/posts/9")).status)
        // Laravel: non-implicit rules are skipped for missing fields
        assertTrue(Validator.make(emptyMap(), mapOf("n" to listOf("min:1"))).passes())
        assertTrue(Validator.make(mapOf("n" to "abcd"), mapOf("n" to listOf("min:1", "max:10"))).passes())
        assertTrue(Validator.make(mapOf("n" to "toolong"), mapOf("n" to listOf("max:3"))).fails())
        assertTrue(Validator.make(mapOf("n" to "ok"), mapOf("n" to listOf("max:3"))).passes())
        assertTrue(Validator.make(mapOf("n" to "a@b.co"), mapOf("n" to listOf("email"))).passes())
        assertTrue(Validator.make(mapOf("n" to "3"), mapOf("n" to listOf("numeric"))).passes())
        assertTrue(Validator.make(mapOf("n" to ""), mapOf("n" to listOf("numeric"))).passes())
        assertTrue(Validator.make(mapOf("n" to "x"), mapOf("n" to listOf("numeric"))).fails())
        assertTrue(Validator.make(mapOf("n" to "Ada"), mapOf("n" to listOf("required"))).passes())
        assertTrue(Validator.make(mapOf("n" to "   "), mapOf("n" to listOf("required"))).fails())
        assertEquals(null, Request.get("/").header("Nope"))
        assertEquals("d", Request.get("/").header("Nope", "d"))

        val v = Validator.make(
            mapOf("email" to "bad", "name" to "a", "age" to "x"),
            mapOf(
                "email" to listOf("required", "email"),
                "name" to listOf("required", "min:2", "max:1"),
                "age" to listOf("numeric"),
                "other" to listOf("unknown-rule"),
            ),
        )
        assertTrue(v.fails())
        assertFalse(v.errors().isEmpty())
        assertFalse(Validator.make(Request.post("/", body = ""), mapOf("x" to listOf("required"))).passes())
        val ok = Validator.make(
            mapOf("email" to "a@b.co", "name" to "Ada", "age" to "3"),
            mapOf(
                "email" to listOf("required", "email"),
                "name" to listOf("min:2", "max:10"),
                "age" to listOf("numeric"),
            ),
        )
        assertEquals("Ada", ok.validate()["name"])
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("age" to "x"), mapOf("age" to listOf("numeric"))).validate()
        }
        // empty email skips email rule failure
        assertTrue(Validator.make(mapOf("email" to ""), mapOf("email" to listOf("email"))).passes())

        val handler = DefaultExceptionHandler(debug = false)
        assertEquals(500, handler.handle(Request.get("/"), RuntimeException("secret")).status)
        assertFalse(handler.handle(Request.get("/"), RuntimeException("secret")).body.contains("secret"))
        assertEquals(405, DefaultExceptionHandler().handle(Request.get("/"), MethodNotAllowedException()).status)
        assertFalse(DefaultExceptionHandler().handle(Request.get("/"), RuntimeException("secret")).body.contains("secret"))
        assertEquals(422, kernel.handle(Request.get("/hi").also { }).let {
            HttpKernel(router, exceptionHandler = DefaultExceptionHandler()).handle(Request.get("/hi"))
            DefaultExceptionHandler().handle(
                Request("GET", "/", headers = mapOf("Accept" to "application/json")),
                ValidationException(mapOf("a" to listOf("b"))),
            ).status
        })
        assertEquals(
            302,
            DefaultExceptionHandler().handle(
                Request.get("/"),
                ValidationException(mapOf("a" to listOf("b"))),
            ).status,
        )
        assertTrue(DefaultExceptionHandler(true).handle(Request.get("/"), RuntimeException("x")).body.contains("exception"))
    }

    @Test
    fun providerAndServer() {
        val base = Files.createTempDirectory("kona-http")
        val app = Application(base)
        app.config.set("app.debug", true)
        app.register(HttpServiceProvider())
        app.boot()
        val router = app.resolve(Router::class.java)
        router.get("/") { Response.text("brew") }
        router.get("/empty") { Response.noContent() }
        router.get("/cookie") {
            Response.text("c").cookie("sid", "1").cookie(Cookie("x", "y", httpOnly = false))
        }
        router.post("/echo") { r -> Response.json(r.all()) }
        val kernel = app.resolve(HttpKernel::class.java)
        val free = java.net.ServerSocket(0).use { it.localPort }
        val running = KonaHttpServer(kernel, "127.0.0.1", free)
        assertFalse(running.isRunning())
        running.start()
        running.start()
        assertTrue(running.isRunning())
        assertEquals(free, running.port())
        val client = HttpClient.newHttpClient()
        assertEquals(
            200,
            client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            ).statusCode(),
        )
        assertEquals(
            204,
            client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/empty")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            ).statusCode(),
        )
        val cookieResp = client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/cookie")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, cookieResp.statusCode())
        assertTrue(cookieResp.headers().allValues("Set-Cookie").size >= 2)
        val head = client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/"))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, head.statusCode())
        assertEquals("", head.body())
        assertEquals(
            200,
            client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/echo?q=1"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("a=b"))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            ).statusCode(),
        )
        // query without value
        assertEquals(
            404,
            client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/missing?lonely")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            ).statusCode(),
        )
        running.stop()
        assertFalse(running.isRunning())
        running.stop() // idempotent null server

        // debug false in provider
        val app2 = Application(Files.createTempDirectory("kona-http2"))
        app2.config.set("app.debug", false)
        app2.register(HttpServiceProvider())
        app2.boot()
        assertTrue(app2.resolve(HttpKernel::class.java) is HttpKernel)

        // config debug null -> true default in provider
        val app3 = Application(Files.createTempDirectory("kona-http3"))
        app3.register(HttpServiceProvider())
        app3.boot()
        assertTrue(app3.resolve(HttpKernel::class.java) is HttpKernel)
    }
}
