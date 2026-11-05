package org.konaframework.testing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.io.TempDir
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.core.Application
import org.konaframework.http.Cookie
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Response
import org.konaframework.session.Csrf
import org.konaframework.session.SessionManager
import org.konaframework.session.SessionServiceProvider
import org.konaframework.session.StartSession
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Optional

class FeatureCoverageTest {
    @TempDir
    lateinit var base: Path

    @Test
    fun queryStringsRedirectLoopsExplicitCsrfAndDescribe() {
        val app = TestApplication.create(base, HttpServiceProvider(), SessionServiceProvider())
        app.resolve(HttpKernel::class.java).pushMiddleware(StartSession(app.resolve(SessionManager::class.java)))
        val router = app.resolve(org.konaframework.http.Router::class.java)
        router.get("/q") { r -> Response.json(mapOf("a" to r.query["a"], "b" to r.query["b"])) }
        router.get("/loop") { Response.redirect("/loop") }
        router.post("/tok") { r -> Response.json(mapOf("token" to r.input("_token"))) }
        router.get("/set") {
            Response.text("ok").cookie(Cookie("gone", "x")).cookie(Cookie("empty", "", maxAge = Duration.ZERO))
        }
        router.get("/blank-redirect") { Response.text("x", 302) }
        router.get("/session-echo") { r ->
            Response.json(mapOf("flash" to r.session()?.get("flash")))
        }

        val http = HttpTestClient(app)
        http.get("/q?a=1&b=two").assertJsonPath("a", "1").assertJsonPath("b", "two")
        http.get("/q?").assertOk()
        http.followingRedirects().get("/loop").assertRedirect()
        // Redirect status without Location must stop following (covers isRedirect's blank-Location branch).
        http.followingRedirects().get("/blank-redirect").assertStatus(302)
        http.post("/tok", mapOf(Csrf.TOKEN_KEY to "explicit", "x" to "1")).assertJsonPath("token", "explicit")
        http.get("/set").assertCookie("gone", "x")
        // withSession on a GET hits prepareSession with wantsToken=false.
        http.withSession(mapOf("flash" to "hi")).get("/session-echo").assertJsonPath("flash", "hi")
        http.withoutCsrfToken().post("/tok", mapOf("x" to "1")).assertOk()
        http.withCsrfToken()

        val database = Database().addConnection(
            "default",
            ConnectionConfig(
                "jdbc:h2:mem:cov_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                poolSize = 2,
            ),
        )
        database.connection().statement("CREATE TABLE empty_cov (id INT)")
        assertThrows(IllegalArgumentException::class.java) {
            DatabaseAssertions(database).assertDatabaseHas("empty_cov", mapOf("id" to 1))
        }
        database.connection().statement("INSERT INTO empty_cov VALUES (1)")
        assertThrows(IllegalArgumentException::class.java) {
            DatabaseAssertions(database).assertDatabaseHas("empty_cov", mapOf("id" to 99))
        }

        database.connection().statement("CREATE TABLE plain_posts (id INT, deleted_at TIMESTAMP)")
        database.connection().statement("INSERT INTO plain_posts VALUES (1, CURRENT_TIMESTAMP)")
        database.connection().statement("INSERT INTO plain_posts VALUES (2, NULL)")
        val asserts = DatabaseAssertions(database)
        asserts.assertSoftDeleted(PlainPost().also { it.setAttribute("id", 1) })
        asserts.assertNotSoftDeleted(PlainPost().also { it.setAttribute("id", 2) })
    }

    @Test
    fun inertiaPropsJsonPathsAndHtmlDataPage() {
        val mapper = jacksonObjectMapper()
        val page = mapper.readTree(
            """{"component":"X","props":{"list":[1],"title":"t"},"url":"/","version":"1"}""",
        )
        val assert = InertiaAssert(page, mapper)
        assert.has("list", 1)
        assertEquals(mapOf("list" to listOf(1), "title" to "t"), assert.props())
        assertThrows(IllegalArgumentException::class.java) { assert.has("title", 3) }
        assertThrows(IllegalArgumentException::class.java) { assert.has("missing", 1) }
        assertThrows(IllegalArgumentException::class.java) { assert.has("list", 9) }
        assertEquals(null, JsonPaths.find(mapper.readTree("""{"a":[]}"""), "a.0.x"))
        assertEquals(null, JsonPaths.find(mapper.readTree("""{"a":["x"]}"""), "a.nope"))

        val mapper2 = jacksonObjectMapper()
        TestResponse(Response.json(mapOf("ok" to true), 201)).assertSuccessful()
        TestResponse(Response.json(mapOf("ok" to true)), mapper2).assertOk()
        TestResponse(Response.json(mapOf("ok" to true)), mapper2, null).assertOk()
        TestResponse(Response.json(mapOf("ok" to true)), mapper2, null) { _, _ -> "/" }.assertOk()
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.text("x", 302).header("Location", "")).assertRedirect()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.text("x", 302)).assertRedirect()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.text("x", 500)).assertSuccessful()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.text("x", 199)).assertSuccessful()
        }
        val html =
            """<div data-page="{&quot;component&quot;:&quot;A&quot;,&quot;props&quot;:{},&quot;url&quot;:&quot;/&quot;,&quot;version&quot;:&quot;1&quot;}"></div>"""
        TestResponse(Response.html(html), mapper).assertInertia { component("A") }

        val arrayProps = mapper.readTree("""{"component":"X","props":[],"url":"/","version":"1"}""")
        assertEquals(emptyMap<String, Any?>(), InertiaAssert(arrayProps, mapper).props())

        // Session errors stored as plain strings (not lists).
        val session = org.konaframework.session.StoreSession(
            org.konaframework.session.SessionId.generate(),
            org.konaframework.session.ArraySessionStore(),
        ).start(false)
        session.put("errors", mapOf("email" to "Required"))
        TestResponse(Response.redirect("/x"), mapper, session, null)
            .assertSessionHasErrors()
            .assertSessionHasErrors("email")
            .assertSessionHasErrors(mapOf("email" to "Required"))
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.redirect("/x"), mapper, session, null)
                .assertSessionHasErrors(mapOf("email" to "Nope"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.redirect("/x"), mapper, session, null)
                .assertSessionHasErrors(mapOf("missing" to "Required"))
        }
        val clean = org.konaframework.session.StoreSession(
            org.konaframework.session.SessionId.generate(),
            org.konaframework.session.ArraySessionStore(),
        ).start(false)
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.redirect("/x"), mapper, clean, null).assertSessionHasErrors()
        }

        TestResponse(Response.text("ok").cookie(Cookie("a", ""))).assertCookieExpired("a")
        TestResponse(Response.text("ok").cookie(Cookie("b", "x", maxAge = Duration.ZERO))).assertCookieExpired("b")
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.text("ok").cookie(Cookie("c", "x", maxAge = Duration.ofSeconds(10))))
                .assertCookieExpired("c")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.text("ok")).assertCookieExpired("missing")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.text("ok")).assertCookie("missing", "x")
        }
    }

    @Test
    fun testDatabaseRollbackAndExtensionBootErrors(@TempDir dir: Path) {
        val app = TestApplication.create(dir, HttpServiceProvider())
        val database = Database().addConnection(
            "default",
            ConnectionConfig(
                "jdbc:h2:mem:cov_tx_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                poolSize = 2,
            ),
        )
        app.container.instance(Database::class.java, database)
        TestDatabase(app)
        TestDatabase(app, false)
        val td = TestDatabase(app, migrate = false, lazily = false)
        td.begin()
        td.rollback()
        td.rollback()
        DatabaseAssertions(database)
        DatabaseAssertions(database, null)

        val ext = KonaTestExtension()
        val boot = KonaTestExtension::class.java.getDeclaredMethod("boot", KonaTest::class.java)
        boot.isAccessible = true
        val missing = assertThrows(InvocationTargetException::class.java) {
            boot.invoke(ext, proxyKonaTest("java.lang.String"))
        }
        assertTrue(missing.targetException is IllegalStateException)

        val failed = assertThrows(InvocationTargetException::class.java) {
            boot.invoke(ext, proxyKonaTest(ThrowingBootstrap::class.java.name))
        }
        assertTrue(failed.targetException is IllegalStateException)
        assertEquals("boom", failed.targetException.message)

        val instanceDir = dir.resolve("instance")
        Files.createDirectories(instanceDir)
        Files.writeString(instanceDir.resolve(".env"), "APP_NAME=Inst\n")
        Files.writeString(instanceDir.resolve(".env.testing"), "APP_NAME=InstTest\n")
        val booted = boot.invoke(
            ext,
            proxyKonaTest(InstanceBootstrap::class.java.name, base = instanceDir.toString()),
        ) as Application
        assertEquals("InstTest", booted.config.getString("app.name"))

        val shutdown = KonaTestExtension::class.java.getDeclaredMethod("shutdown", Application::class.java)
        shutdown.isAccessible = true
        shutdown.invoke(ext, TestApplication.create(dir.resolve("bare-shutdown"), HttpServiceProvider()))

        val dbApp = TestApplication.create(dir.resolve("rb"), HttpServiceProvider())
        val rbDb = Database().addConnection(
            "default",
            ConnectionConfig(
                "jdbc:h2:mem:cov_rb_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                poolSize = 2,
            ),
        )
        dbApp.container.instance(Database::class.java, rbDb)
        val started = TestDatabase(dbApp, migrate = false, lazily = false)
        started.begin()
        rbDb.connection().rollBack()
        started.rollback()

        val resolve = KonaTestExtension::class.java.getDeclaredMethod(
            "resolveParameter",
            ParameterContext::class.java,
            ExtensionContext::class.java,
        )
        resolve.isAccessible = true
        val emptyStoreContext = emptyExtensionContext()
        val appFailure = assertThrows(InvocationTargetException::class.java) {
            resolve.invoke(ext, parameterContext(Application::class.java), emptyStoreContext)
        }
        assertTrue(appFailure.targetException is ParameterResolutionException)
        val ctxFailure = assertThrows(InvocationTargetException::class.java) {
            resolve.invoke(ext, parameterContext(KonaTestContext::class.java), emptyStoreContext)
        }
        assertTrue(ctxFailure.targetException is ParameterResolutionException)

        // afterEach with an empty store covers the null CONTEXT / null APP branches.
        val afterEach = KonaTestExtension::class.java.getDeclaredMethod("afterEach", ExtensionContext::class.java)
        afterEach.isAccessible = true
        afterEach.invoke(ext, freshExtensionContext())
    }

    private fun freshExtensionContext(): ExtensionContext {
        val store = Proxy.newProxyInstance(
            ExtensionContext.Store::class.java.classLoader,
            arrayOf(ExtensionContext.Store::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "get", "remove" -> null
                "put" -> null
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            ExtensionContext::class.java.classLoader,
            arrayOf(ExtensionContext::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getStore" -> store
                "getRequiredTestClass" -> FreshAnno::class.java
                else -> null
            }
        } as ExtensionContext
    }

    private fun proxyKonaTest(bootstrap: String, base: String = ""): KonaTest =
        Proxy.newProxyInstance(
            KonaTest::class.java.classLoader,
            arrayOf(KonaTest::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "bootstrap" -> bootstrap
                "base" -> base
                "environment" -> "testing"
                "freshApplication" -> false
                "annotationType" -> KonaTest::class.java
                "toString" -> "@KonaTest($bootstrap)"
                "hashCode" -> bootstrap.hashCode()
                "equals" -> false
                else -> null
            }
        } as KonaTest

    private fun parameterContext(type: Class<*>): ParameterContext {
        class Holder {
            @Suppress("UNUSED_PARAMETER")
            fun method(value: Any?) {}
        }
        val parameter = Holder::class.java.methods.first { it.name == "method" }.parameters[0]
        // Replace erased Object with the requested type via a thin wrapper ParameterContext.
        return Proxy.newProxyInstance(
            ParameterContext::class.java.classLoader,
            arrayOf(ParameterContext::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getParameter" -> object {
                    @JvmField
                    val type: Class<*> = type
                }.let {
                    // Use a real Parameter from a typed method when possible.
                    when (type) {
                        Application::class.java -> AppParam::class.java.methods.first().parameters[0]
                        KonaTestContext::class.java -> CtxParam::class.java.methods.first().parameters[0]
                        else -> parameter
                    }
                }
                "getIndex" -> 0
                "getTarget" -> Optional.empty<Any>()
                "isAnnotated" -> false
                "findAnnotation" -> Optional.empty<Any>()
                "findRepeatableAnnotations" -> emptyList<Any>()
                else -> null
            }
        } as ParameterContext
    }

    private class AppParam {
        @Suppress("UNUSED_PARAMETER")
        fun method(app: Application) {}
    }

    private class CtxParam {
        @Suppress("UNUSED_PARAMETER")
        fun method(ctx: KonaTestContext) {}
    }

    private fun emptyExtensionContext(): ExtensionContext {
        val store = Proxy.newProxyInstance(
            ExtensionContext.Store::class.java.classLoader,
            arrayOf(ExtensionContext.Store::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "get", "remove" -> null
                "put" -> null
                "getOrDefault" -> method.parameters.lastOrNull()?.let { null }
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            ExtensionContext::class.java.classLoader,
            arrayOf(ExtensionContext::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getStore" -> store
                "getRequiredTestClass" -> FeatureCoverageTest::class.java
                else -> null
            }
        } as ExtensionContext
    }
}

object ThrowingBootstrap {
    @JvmStatic
    fun create(args: Array<String>): Application {
        throw IllegalStateException("boom")
    }
}

/** Non-static `create` on a Kotlin object (INSTANCE) for extension boot coverage. */
object InstanceBootstrap {
    fun create(args: Array<String>): Application = FeatureTestBootstrap.create(args)
}

class PlainPost : Cadence<PlainPost>() {
    override val table = "plain_posts"
    override val primaryKey = "id"
    override val timestamps = false
}

@KonaTest(bootstrap = "org.konaframework.testing.FeatureTestBootstrap", base = "build/kona-test-nodb")
class KonaTestNoDatabaseAnnotationTest {
    @Test
    fun httpWorksWithoutDatabaseLifecycle(ctx: KonaTestContext) {
        assertEquals(null, ctx.testDatabase())
        ctx.http().get("/ping").assertOk()
        ctx.ensureDatabase()
    }
}

@KonaTest(
    bootstrap = "org.konaframework.testing.FeatureTestBootstrap",
    base = "build/kona-test-fresh-anno",
    freshApplication = true,
)
private class FreshAnno
