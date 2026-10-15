package dev.kona.http

import dev.kona.core.Application
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ResourceAndControllerDispatchTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun resetWrap() {
        JsonResource.flushState()
    }

    @Test
    fun conditionalAttributesWhenMergeWhenAndAdditional() {
        class PostResource(
            private val title: String,
            private val admin: Boolean,
            private val secret: String?,
        ) : JsonResource(title) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf(
                    "title" to title,
                    "role" to `when`(admin, "admin"),
                    "extra" to mergeWhen(admin, mapOf("secret" to secret)),
                    "plain" to `when`(false, "nope", "fallback"),
                )

            override fun with(request: Request): Map<String, Any?> =
                mapOf("meta" to mapOf("path" to request.path))
        }

        val request = Request.get("/posts/1")
        val resolved = PostResource("Hello", admin = true, secret = "s3cr3t")
            .additional(mapOf("links" to mapOf("self" to "/posts/1")))
            .resolve(request)

        @Suppress("UNCHECKED_CAST")
        val data = resolved["data"] as Map<String, Any?>
        assertEquals("Hello", data["title"])
        assertEquals("admin", data["role"])
        assertEquals("s3cr3t", data["secret"])
        assertFalse(data.containsKey("extra"))
        assertEquals("fallback", data["plain"])
        assertEquals("/posts/1", (resolved["meta"] as Map<*, *>)["path"])
        assertEquals("/posts/1", (resolved["links"] as Map<*, *>)["self"])

        val guest = PostResource("Hello", admin = false, secret = "x").resolve(request)
        @Suppress("UNCHECKED_CAST")
        val guestData = guest["data"] as Map<String, Any?>
        assertFalse(guestData.containsKey("role"))
        assertFalse(guestData.containsKey("secret"))
    }

    @Test
    fun whenLoadedUsesRelationLoadedContract() {
        class FakeModel {
            private val relations = mutableMapOf<String, Any?>()

            fun relationLoaded(name: String): Boolean = relations.containsKey(name)

            fun getRelation(name: String): Any? = relations[name]

            fun setRelation(name: String, value: Any?) {
                relations[name] = value
            }
        }

        class ModelResource(model: FakeModel) : JsonResource(model) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf(
                    "comments" to whenLoaded("comments"),
                    "author" to whenLoaded("author", mapOf("name" to "Ada")),
                )
        }

        val unloaded = FakeModel()
        val missing = ModelResource(unloaded).resolve(Request.get("/"))
        @Suppress("UNCHECKED_CAST")
        assertFalse((missing["data"] as Map<String, Any?>).containsKey("comments"))
        assertFalse((missing["data"] as Map<String, Any?>).containsKey("author"))

        val loaded = FakeModel().also {
            it.setRelation("comments", listOf("a", "b"))
            it.setRelation("author", "x")
        }
        val present = ModelResource(loaded).resolve(Request.get("/"))
        @Suppress("UNCHECKED_CAST")
        val data = present["data"] as Map<String, Any?>
        assertEquals(listOf("a", "b"), data["comments"])
        assertEquals(mapOf("name" to "Ada"), data["author"])

        class Plain
        val noContract = object : JsonResource(Plain()) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf("rel" to whenLoaded("anything"))
        }.resolve(Request.get("/"))
        @Suppress("UNCHECKED_CAST")
        assertFalse((noContract["data"] as Map<String, Any?>).containsKey("rel"))

        val nullResource = object : JsonResource(null) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf("rel" to whenLoaded("x"))
        }.resolve(Request.get("/"))
        @Suppress("UNCHECKED_CAST")
        assertFalse((nullResource["data"] as Map<String, Any?>).containsKey("rel"))

        class LoadedWithoutGetter {
            fun relationLoaded(name: String): Boolean = true
        }
        val noGetter = object : JsonResource(LoadedWithoutGetter()) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf("rel" to whenLoaded("posts"))
        }.resolve(Request.get("/"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(null, (noGetter["data"] as Map<String, Any?>)["rel"])

        class ExplodingModel {
            fun relationLoaded(): Boolean = true
            fun relationLoaded(name: Int): Boolean = true
            fun relationLoaded(name: String): Boolean = throw IllegalStateException("boom")
            fun getRelation(): Any? = null
            fun getRelation(name: Int): Any? = null
            fun getRelation(name: String): Any? = throw IllegalStateException("boom")
        }
        val exploding = object : JsonResource(ExplodingModel()) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf("rel" to whenLoaded("posts"))
        }.resolve(Request.get("/"))
        @Suppress("UNCHECKED_CAST")
        assertFalse((exploding["data"] as Map<String, Any?>).containsKey("rel"))

        class LoadedThenExplodes {
            fun relationLoaded(name: String): Boolean = true
            fun getRelation(name: String): Any? = throw IllegalStateException("boom")
        }
        val loadedBoom = object : JsonResource(LoadedThenExplodes()) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf("rel" to whenLoaded("posts"))
        }.resolve(Request.get("/"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(null, (loadedBoom["data"] as Map<String, Any?>)["rel"])

        val javaModel = JavaRelationModel().also { it.setRelation("posts", listOf(1, 2)) }
        val javaRes = object : JsonResource(javaModel) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf("posts" to whenLoaded("posts"))
        }.resolve(Request.get("/"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(1, 2), (javaRes["data"] as Map<String, Any?>)["posts"])
    }

    @Test
    fun wrapAndWithoutWrappingAffectSinglesAndCollections() {
        class Item(val n: Int) : JsonResource(n) {
            override fun toArray(request: Request): Map<String, Any?> = mapOf("n" to n)
        }

        JsonResource.wrap("payload")
        assertEquals("payload", JsonResource.wrapping())
        assertTrue(Item(1).resolve(Request.get("/")).containsKey("payload"))
        assertTrue(
            (JsonResource.collection(listOf(Item(1))).toArray(Request.get("/")) as Map<*, *>)
                .containsKey("payload"),
        )

        JsonResource.withoutWrapping()
        assertEquals(null, JsonResource.wrapping())
        assertEquals(mapOf("n" to 2), Item(2).resolve(Request.get("/")))
        assertTrue(JsonResource.collection(listOf(Item(2))).toArray(Request.get("/")) is List<*>)
    }

    @Test
    fun apiResourceAndOnlyExcept() {
        val controller = object : BaseResourceController() {
            override fun index(request: Request) = Response.text("index")
            override fun show(request: Request) = Response.text("show")
            override fun store(request: Request) = Response.text("store")
            override fun destroy(request: Request) = Response.text("destroy")
        }
        val router = Router()
        router.apiResource("notes", controller)
        val kernel = HttpKernel(router)
        assertEquals("index", kernel.handle(Request.get("/notes")).body)
        assertEquals("show", kernel.handle(Request.get("/notes/1")).body)
        // apiResource has no create/edit routes; /notes/create matches show with id=create
        assertEquals("show", kernel.handle(Request.get("/notes/create")).body)
        assertEquals(404, kernel.handle(Request.get("/notes/1/edit")).status)

        val partial = Router()
        partial.resource("items", controller, only = listOf("index", "show"))
        val partialKernel = HttpKernel(partial)
        assertEquals("index", partialKernel.handle(Request.get("/items")).body)
        // Path exists for GET index → POST is method-not-allowed, not missing
        assertEquals(405, partialKernel.handle(Request.post("/items")).status)

        val excepted = Router()
        excepted.apiResource("things", controller, except = listOf("destroy"))
        val exceptKernel = HttpKernel(excepted)
        assertEquals(405, exceptKernel.handle(Request("DELETE", "/things/1")).status)
        assertEquals("store", exceptKernel.handle(Request.post("/things")).body)
    }

    @Test
    fun controllerClassAndStringActions() {
        val app = Application(temp)
        app.register(HttpServiceProvider())
        app.boot()
        val router = app.resolve(Router::class.java)
        val name = EchoController::class.java.name

        // Mix Class+method and Controller@method overloads (all verbs)
        router.get("/g1", EchoController::class.java, "index")
        router.get("/g2", "$name@index")
        router.post("/p1", EchoController::class.java, "store")
        router.post("/p2", "$name@store")
        router.put("/u1/{id}", EchoController::class.java, "update")
        router.put("/u2/{id}", "$name@update")
        router.patch("/a1/{id}", EchoController::class.java, "update")
        router.patch("/a2/{id}", "$name@update")
        router.delete("/d1/{id}", EchoController::class.java, "destroy")
        router.delete("/d2/{id}", "$name@destroy")
        router.options("/o1", EchoController::class.java, "options")
        router.options("/o2", "$name@options")
        router.any("/any1", EchoController::class.java, "any")
        router.any("/any2", "$name@any")
        router.match(listOf("GET", "POST"), "/m1", EchoController::class.java, "matched")
        router.match(listOf("GET", "POST"), "/m2", "$name@matched")

        val kernel = app.resolve(HttpKernel::class.java)
        assertEquals("index", kernel.handle(Request.get("/g1")).body)
        assertEquals("index", kernel.handle(Request.get("/g2")).body)
        assertEquals("store", kernel.handle(Request.post("/p1")).body)
        assertEquals("store", kernel.handle(Request.post("/p2")).body)
        assertEquals("update", kernel.handle(Request("PUT", "/u1/1")).body)
        assertEquals("update", kernel.handle(Request("PUT", "/u2/1")).body)
        assertEquals("update", kernel.handle(Request("PATCH", "/a1/1")).body)
        assertEquals("update", kernel.handle(Request("PATCH", "/a2/1")).body)
        assertEquals("destroy", kernel.handle(Request("DELETE", "/d1/1")).body)
        assertEquals("destroy", kernel.handle(Request("DELETE", "/d2/1")).body)
        assertEquals("options", kernel.handle(Request("OPTIONS", "/o1")).body)
        assertEquals("options", kernel.handle(Request("OPTIONS", "/o2")).body)
        assertEquals("any", kernel.handle(Request.get("/any1")).body)
        assertEquals("any", kernel.handle(Request.get("/any2")).body)
        assertEquals("matched", kernel.handle(Request.get("/m1")).body)
        assertEquals("matched", kernel.handle(Request.post("/m2")).body)

        assertThrows(IllegalStateException::class.java) {
            Router().get("/x", EchoController::class.java, "index")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Controllers.action(app, "NotAController")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Controllers.action(app, "@index")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Controllers.action(app, "Controller@")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Controllers.action(app, "missing.Controller@index")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Controllers.action(app, EchoController::class.java, "missing").handle(Request.get("/"))
        }
    }

    @Test
    fun controllerInstantiationViaContainerAndFallback() {
        val app = Application(temp)
        app.register(HttpServiceProvider())
        app.boot()

        val viaApp = Controllers.action(app, AppAwareController::class.java, "ping")
            .handle(Request.get("/"))
        assertTrue(viaApp.body.contains(temp.toString()))

        val viaNoArg = Controllers.action(app, NoArgController::class.java, "ping")
            .handle(Request.get("/"))
        assertEquals("no-arg", viaNoArg.body)

        // make() fails (unresolvable String), fallback uses Application constructor
        val viaFallbackApp = Controllers.action(app, FallbackAppController::class.java, "ping")
            .handle(Request.get("/"))
        assertTrue(viaFallbackApp.body.contains("fallback-app"))

        // make() fails, fallback uses no-arg constructor
        val viaFallbackNoArg = Controllers.action(app, FallbackNoArgController::class.java, "ping")
            .handle(Request.get("/"))
        assertEquals("fallback-no-arg", viaFallbackNoArg.body)

        assertThrows(Exception::class.java) {
            Controllers.action(app, UnresolvableController::class.java, "ping").handle(Request.get("/"))
        }

        assertThrows(IllegalStateException::class.java) {
            Controllers.action(app, BadReturnController::class.java, "ping").handle(Request.get("/"))
        }

        assertThrows(RuntimeException::class.java) {
            Controllers.action(app, ThrowingController::class.java, "ping").handle(Request.get("/"))
        }

        assertThrows(Error::class.java) {
            Controllers.action(app, ErrorThrowingController::class.java, "ping").handle(Request.get("/"))
        }

        assertThrows(IllegalStateException::class.java) {
            Controllers.action(app, CheckedThrowingController::class.java, "ping").handle(Request.get("/"))
        }
        val nullMsg = assertThrows(IllegalStateException::class.java) {
            Controllers.action(app, NullMessageThrowingController::class.java, "ping").handle(Request.get("/"))
        }
        assertTrue(nullMsg.message!!.contains("failed"))

        assertEquals(
            "ok",
            Controllers.action(app, OverloadedController::class.java, "ping").handle(Request.get("/")).body,
        )
        assertEquals(
            "ok",
            Controllers.action(app, JavaOverloadedController::class.java, "ping").handle(Request.get("/")).body,
        )

        val nullCause = assertThrows(IllegalStateException::class.java) {
            Controllers.unwrapInvocation(EchoController::class.java, "index", null)
        }
        assertTrue(nullCause.message!!.contains("failed"))
    }

    @Test
    fun classBasedApiResourceRegistration() {
        val app = Application(temp)
        app.register(HttpServiceProvider())
        app.boot()
        val router = app.resolve(Router::class.java)
        router.apiResource("echo", EchoResourceController::class.java)
        val kernel = app.resolve(HttpKernel::class.java)
        assertEquals("api-index", kernel.handle(Request.get("/echo")).body)
        assertEquals("api-show", kernel.handle(Request.get("/echo/9")).body)

        router.resource("full", EchoResourceController::class.java, only = listOf("create"))
        assertEquals("api-create", kernel.handle(Request.get("/full/create")).body)

        // Class-based resource with default only/except
        val router2 = app.resolve(Router::class.java)
        // already has routes; use a fresh app router for defaults
        val app2 = Application(temp.resolve("r2"))
        app2.register(HttpServiceProvider())
        app2.boot()
        val r2 = app2.resolve(Router::class.java)
        r2.resource("defaults", EchoResourceController::class.java)
        assertEquals("api-index", app2.resolve(HttpKernel::class.java).handle(Request.get("/defaults")).body)
        r2.apiResource("apiDefaults", EchoResourceController::class.java)
        assertEquals(
            "api-index",
            app2.resolve(HttpKernel::class.java).handle(Request.get("/apiDefaults")).body,
        )
    }

    class EchoController {
        fun index(request: Request): Response = Response.text("index")
        fun store(request: Request): Response = Response.text("store")
        fun update(request: Request): Response = Response.text("update")
        fun destroy(request: Request): Response = Response.text("destroy")
        fun options(request: Request): Response = Response.text("options")
        fun any(request: Request): Response = Response.text("any")
        fun matched(request: Request): Response = Response.text("matched")
    }

    /** Overloads exercise Controllers.findMethod filter branches. */
    class OverloadedController {
        fun ping(): Response = Response.text("no-request")
        fun ping(request: Request, extra: Int): Response = Response.text("extra")
        fun ping(request: Request): Response = Response.text("ok")

        companion object {
            @JvmStatic
            fun other(request: Request): Response = Response.text("static")
        }
    }

    class AppAwareController(private val app: Application) {
        fun ping(request: Request): Response = Response.text(app.basePath.toString())
    }

    class NoArgController {
        fun ping(request: Request): Response = Response.text("no-arg")
    }

    /** Higher-arity ctor makes container fail; Application ctor is used by reflective fallback. */
    class FallbackAppController {
        private val label: String

        constructor(unused: String, app: Application) {
            label = unused + app.basePath
        }

        constructor(app: Application) {
            label = "fallback-app:" + app.basePath
        }

        fun ping(request: Request): Response = Response.text(label)
    }

    /** Higher-arity ctor makes container fail; no-arg ctor is used by reflective fallback. */
    class FallbackNoArgController {
        constructor(unused: String) {
            error(unused)
        }

        constructor()

        fun ping(request: Request): Response = Response.text("fallback-no-arg")
    }

    class UnresolvableController private constructor(val x: Int) {
        fun ping(request: Request): Response = Response.text("x")
    }

    class BadReturnController {
        fun ping(request: Request): String = "not-a-response"
    }

    class ThrowingController {
        fun ping(request: Request): Response = throw RuntimeException("boom")
    }

    class ErrorThrowingController {
        fun ping(request: Request): Response = throw AssertionError("err")
    }

    class EchoResourceController : BaseResourceController() {
        override fun index(request: Request): Response = Response.text("api-index")
        override fun create(request: Request): Response = Response.text("api-create")
        override fun show(request: Request): Response = Response.text("api-show")
    }
}
