package io.konaframework.http

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResourceAndControllerDispatchTest {
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
}
