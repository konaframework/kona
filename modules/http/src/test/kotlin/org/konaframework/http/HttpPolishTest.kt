package org.konaframework.http

import org.konaframework.core.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HttpPolishTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun urlGeneratorRoutesAndSignedUrls() {
        val router = Router()
        router.get("/users/{id}") { Response.text("u") }.name("users.show")
        router.get("/posts") { Response.text("p") }.name("posts.index")
        val fixed = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        val urls = UrlGenerator(router, clock = fixed).setRoot("https://example.com").setKey("secret")

        assertEquals("https://example.com/users/1", urls.route("users.show", mapOf("id" to "1")))
        assertEquals("https://example.com/posts", urls.route("posts.index"))
        assertEquals("https://example.com/about", urls.to("/about"))
        assertEquals("https://example.com/about", urls.to("about"))
        assertEquals("https://other.test/x", urls.to("https://other.test/x"))
        assertEquals("http://other.test/x", urls.to("http://other.test/x"))

        val signed = urls.signed("/download", expirationSeconds = 60)
        assertTrue(signed.startsWith("https://example.com/download?"))
        assertTrue(signed.contains("expires="))
        assertTrue(signed.contains("signature="))

        val signedRoute = urls.signedRoute("users.show", mapOf("id" to "9"), expirationSeconds = 30)
        assertTrue(signedRoute.contains("/users/9?"))

        val withQuery = urls.signed("/files?Name=Report&flag", expirationSeconds = null)
        assertTrue(withQuery.contains("Name="))
        assertTrue(withQuery.contains("flag="))
        assertTrue(withQuery.contains("signature="))
        assertFalse(withQuery.contains("expires="))

        assertTrue(urls.signed("/open").contains("signature="))
        assertTrue(urls.signedRoute("posts.index").contains("/posts"))
        assertTrue(urls.signedRoute("users.show", mapOf("id" to "2")).contains("/users/2"))

        val bare = UrlGenerator(router)
        assertEquals("/users/1", bare.route("users.show", mapOf("id" to "1")))
        assertEquals("/posts", bare.route("posts.index"))
        assertEquals("/x", bare.to("/x"))
    }

    @Test
    fun signedUrlValidation() {
        val router = Router()
        val fixed = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        val urls = UrlGenerator(router, clock = fixed).setKey("k")

        val absolute = urls.signed("/secure", expirationSeconds = 10)
        val query = absolute.substringAfter('?').split('&').associate {
            val eq = it.indexOf('=')
            it.substring(0, eq) to java.net.URLDecoder.decode(it.substring(eq + 1), Charsets.UTF_8)
        }
        val valid = Request("GET", "/secure", query = query)
        assertTrue(urls.hasValidSignature(valid))

        assertFalse(urls.hasValidSignature(Request("GET", "/secure", query = emptyMap())))
        assertFalse(
            urls.hasValidSignature(
                Request("GET", "/secure", query = query + ("signature" to "deadbeef")),
            ),
        )
        assertFalse(
            urls.hasValidSignature(
                Request("GET", "/secure", query = query + ("expires" to "not-a-number")),
            ),
        )

        val noExpiry = urls.signed("/forever")
        val noExpiryQuery = noExpiry.substringAfter('?').split('&').associate {
            val eq = it.indexOf('=')
            it.substring(0, eq) to java.net.URLDecoder.decode(it.substring(eq + 1), Charsets.UTF_8)
        }
        assertTrue(urls.hasValidSignature(Request("GET", "/forever", query = noExpiryQuery)))

        val expiredClock = Clock.fixed(Instant.parse("2020-01-01T00:01:00Z"), ZoneOffset.UTC)
        val expiredUrls = UrlGenerator(router, clock = expiredClock).setKey("k")
        assertFalse(expiredUrls.hasValidSignature(valid))
    }

    @Test
    fun jsonResourcesAndCollections() {
        JsonResource.flushState()
        class UserResource(private val id: Int, private val name: String) : JsonResource(name) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf("id" to id, "name" to name, "path" to request.path)
        }

        val request = Request.get("/users")
        val one = UserResource(1, "Ada")
        assertEquals(
            mapOf("data" to mapOf("id" to 1, "name" to "Ada", "path" to "/users")),
            one.resolve(request),
        )
        assertTrue(one.toResponse(request).body.contains("Ada"))

        val collection = JsonResource.collection(listOf(UserResource(1, "Ada"), UserResource(2, "Grace")))
        val wrapped = collection.toArray(request) as Map<*, *>
        assertTrue(wrapped.containsKey("data"))
        assertEquals(2, (wrapped["data"] as List<*>).size)
        assertTrue(collection.toResponse(request).body.contains("Grace"))

        val bare = ResourceCollection(listOf(UserResource(3, "Lin"))).wrap(null)
        assertTrue(bare.toArray(request) is List<*>)
        bare.wrap("users")
        assertTrue((bare.toArray(request) as Map<*, *>).containsKey("users"))

        JsonResource.withoutWrapping()
        assertEquals(mapOf("id" to 1, "name" to "Ada", "path" to "/users"), one.resolve(request))
        assertEquals("data", run {
            JsonResource.flushState()
            JsonResource.wrapping()
        })
    }

    @Test
    fun controllersFormRequestsAndInvokable() {
        class StoreUserRequest : FormRequest() {
            override fun rules(): Map<String, List<String>> =
                mapOf("name" to listOf("required", "min:2"))
        }

        class ForbiddenRequest : FormRequest() {
            override fun rules(): Map<String, List<String>> = mapOf("x" to listOf("required"))
            override fun authorize(request: Request): Boolean = false
        }

        class UsersController : Controller() {
            fun store(request: Request): Response {
                val data = validate(request, StoreUserRequest())
                return Response.json(data, 201)
            }

            fun rulesOnly(request: Request): Response =
                Response.json(validate(request, mapOf("age" to listOf("required", "numeric"))))
        }

        val controller = UsersController()
        val created = controller.store(Request.post("/users", body = "name=Ada"))
        assertEquals(201, created.status)
        assertTrue(created.body.contains("Ada"))

        assertThrows(ValidationException::class.java) {
            controller.store(Request.post("/users", body = "name=A"))
        }
        assertThrows(HttpException::class.java) {
            ForbiddenRequest().validate(Request.post("/", body = "x=1"))
        }
        assertTrue(controller.rulesOnly(Request.post("/", body = "age=3")).body.contains("3"))

        val invokable = InvokableController { Response.text("ping ${it.path}") }
        assertEquals("ping /x", Controllers.asHandler(invokable).handle(Request.get("/x")).body)
        assertEquals("ping /y", invokable.asHandler().handle(Request.get("/y")).body)

        val resource = object : BaseResourceController() {
            override fun index(request: Request): Response = Response.text("index")
        }
        assertEquals("index", resource.index(Request.get("/")).body)
        assertThrows(NotFoundException::class.java) { resource.create(Request.get("/")) }
        assertThrows(NotFoundException::class.java) { resource.show(Request.get("/")) }
        assertThrows(NotFoundException::class.java) { resource.edit(Request.get("/")) }
        assertThrows(NotFoundException::class.java) { resource.store(Request.get("/")) }
        assertThrows(NotFoundException::class.java) { resource.update(Request.get("/")) }
        assertThrows(NotFoundException::class.java) { resource.destroy(Request.get("/")) }
    }

    @Test
    fun exceptionHandlerReportRenderAndHtml() {
        val reported = mutableListOf<Throwable>()
        val handler = DefaultExceptionHandler(debug = true)
            .report { reported += it }
            .render { _, t ->
                if (t.message == "custom") {
                    Response.text("customized", 418)
                } else {
                    null
                }
            }

        assertEquals(418, handler.handle(Request.get("/"), RuntimeException("custom")).status)
        assertEquals(1, reported.size)

        val htmlReq = Request("GET", "/", headers = mapOf("Accept" to "text/html"))
        val html = DefaultExceptionHandler().handle(htmlReq, NotFoundException("missing <x>"))
        assertEquals(404, html.status)
        assertTrue(html.headers["Content-Type"]!!.contains("text/html"))
        assertTrue(html.body.contains("&lt;x&gt;"))
        assertFalse(html.body.contains("<x>"))
        assertTrue(html.body.contains("#DF8723"))
        assertTrue(html.body.contains("https://docs.konaframework.org/"))
        assertTrue(html.body.contains("Kona docs"))

        val htmlValidation = DefaultExceptionHandler().handle(
            htmlReq,
            ValidationException(mapOf("a" to listOf("b"))),
        )
        assertEquals(302, htmlValidation.status)
        assertEquals("/", htmlValidation.headers["Location"])
        assertEquals(mapOf("a" to listOf("b")), htmlValidation.sessionErrors)

        val htmlHttp = DefaultExceptionHandler().handle(htmlReq, HttpException(403, "nope"))
        assertEquals(403, htmlHttp.status)
        assertTrue(htmlHttp.body.contains("nope"))
        assertTrue(htmlHttp.body.contains("#DF8723"))

        val htmlUnknown = DefaultExceptionHandler().handle(htmlReq, HttpException(599, "weird"))
        assertEquals(599, htmlUnknown.status)
        assertTrue(htmlUnknown.body.contains("Error"))
        assertTrue(htmlUnknown.body.contains("weird"))

        val htmlServer = DefaultExceptionHandler(false).handle(htmlReq, RuntimeException("secret"))
        assertTrue(htmlServer.body.contains("Server Error"))
        assertFalse(htmlServer.body.contains("secret"))
        assertTrue(htmlServer.body.contains("docs.konaframework.org"))

        val htmlDebug = DefaultExceptionHandler(true).handle(htmlReq, RuntimeException("boom"))
        assertTrue(htmlDebug.body.contains("boom"))

        val htmlNull = DefaultExceptionHandler(true).handle(htmlReq, RuntimeException())
        assertTrue(htmlNull.body.contains("Server Error"))

        val both = Request("GET", "/", headers = mapOf("Accept" to "text/html, application/json"))
        assertTrue(
            DefaultExceptionHandler().handle(both, RuntimeException("x")).headers["Content-Type"]!!
                .contains("json"),
        )

        DefaultExceptionHandler().handle(Request.get("/"), RuntimeException("logged"))
    }

    @Test
    fun providerRegistersUrlGenerator() {
        val app = Application(temp)
        app.config.set("app.url", "https://app.test")
        app.config.set("app.key", "app-secret")
        app.register(HttpServiceProvider())
        app.boot()
        val urls = app.resolve(UrlGenerator::class.java)
        val router = app.resolve(Router::class.java)
        router.get("/home") { Response.text("h") }.name("home")
        assertEquals("https://app.test/home", urls.route("home"))
        assertEquals("https://app.test/home", Url.route("home"))
        assertEquals("https://app.test/home", route("home"))
        assertEquals("https://app.test/about", Url.url("/about"))
        assertEquals("https://app.test/about", url("/about"))
        assertEquals(urls, Url.generator())

        val plainDir = temp.resolve("plain")
        java.nio.file.Files.createDirectories(plainDir)
        val plain = Application(plainDir)
        plain.register(HttpServiceProvider())
        plain.boot()
        val defaultUrls = plain.resolve(UrlGenerator::class.java)
        plain.resolve(Router::class.java).get("/x") { Response.text("x") }.name("x")
        assertEquals("/x", defaultUrls.route("x"))
        assertEquals("/x", route("x"))
        assertEquals("/y", url("/y"))
    }

    @Test
    fun urlHelpersRequireConfiguredGenerator() {
        val previous = runCatching { Url.generator() }.getOrNull()
        try {
            Url.clearGenerator()
            assertThrows(IllegalStateException::class.java) { Url.generator() }
            assertThrows(IllegalStateException::class.java) { route("missing") }
            assertThrows(IllegalStateException::class.java) { url("/x") }
            Url.setGenerator(UrlGenerator(Router()))
            assertEquals("/p", Url.to("/p"))
            assertEquals("/p", Url.url("/p"))
        } finally {
            if (previous != null) {
                Url.setGenerator(previous)
            } else {
                Url.clearGenerator()
            }
        }
    }
}
