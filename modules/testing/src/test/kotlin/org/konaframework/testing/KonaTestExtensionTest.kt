package org.konaframework.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.core.Application
import org.konaframework.core.Env
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.session.SessionManager
import org.konaframework.session.SessionServiceProvider
import org.konaframework.session.StartSession
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bootstrap used by [KonaTestExtension] tests. Writes `.env` / `.env.testing` under the requested
 * base and boots HTTP + session.
 */
object FeatureTestBootstrap {
    @JvmStatic
    fun create(args: Array<String>): Application {
        val baseArg = args.firstOrNull { it.startsWith("--base=") }?.removePrefix("--base=")
            ?: throw IllegalArgumentException("missing --base")
        val base = Path.of(baseArg)
        Files.createDirectories(base)
        if (!Files.exists(base.resolve(".env"))) {
            Files.writeString(base.resolve(".env"), "APP_NAME=Base\nAPP_ENV=local\n")
        }
        if (!Files.exists(base.resolve(".env.testing"))) {
            Files.writeString(base.resolve(".env.testing"), "APP_NAME=Under Test\n")
        }
        val app = TestApplication.create(base, HttpServiceProvider(), SessionServiceProvider())
        app.resolve(HttpKernel::class.java).pushMiddleware(StartSession(app.resolve(SessionManager::class.java)))
        app.resolve(Router::class.java).get("/ping") { Response.json(mapOf("ok" to true, "name" to app.config.getString("app.name"))) }
        val database = Database().addConnection(
            "default",
            ConnectionConfig(
                "jdbc:h2:mem:kona_ext_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                poolSize = 2,
            ),
        )
        database.connection().statement("CREATE TABLE IF NOT EXISTS notes (id INT PRIMARY KEY, title VARCHAR(50))")
        app.container.instance(Database::class.java, database)
        return app
    }
}

@KonaTest(bootstrap = "org.konaframework.testing.FeatureTestBootstrap", base = "build/kona-test-ext")
@RefreshDatabase
class KonaTestExtensionRefreshTest {
    @Test
    fun injectsContextHttpClientAndAssertions(ctx: KonaTestContext, http: HttpTestClient, db: DatabaseAssertions, app: Application) {
        assertEquals("testing", app.config.getString("app.env"))
        assertEquals("Under Test", app.config.getString("app.name"))
        assertNotNull(ctx.testDatabase())
        http.get("/ping").assertOk().assertJsonPath("ok", true).assertJsonPath("name", "Under Test")
        ctx.database().connection().table("notes").insert(mapOf("id" to 1, "title" to "Hi"))
        db.assertDatabaseHas("notes", mapOf("id" to 1))
        ctx.assertions().assertDatabaseCount("notes", 1)
        assertTrue(ctx.http() === http)
    }
}

@KonaTest(bootstrap = "org.konaframework.testing.FeatureTestBootstrap", base = "build/kona-test-lazy")
@LazilyRefreshDatabase
class KonaTestExtensionLazyTest {
    @Test
    fun startsDatabaseLazily(ctx: KonaTestContext) {
        assertFalse(ctx.testDatabase()!!.isStarted())
        ctx.ensureDatabase()
        assertTrue(ctx.testDatabase()!!.isStarted())
        ctx.http().get("/ping").assertOk()
    }
}

@KonaTest(bootstrap = "org.konaframework.testing.FeatureTestBootstrap", base = "build/kona-test-tx")
@DatabaseTransactions
class KonaTestExtensionTransactionsTest {
    @Test
    fun opensTransactionWithoutMigrating(ctx: KonaTestContext) {
        ctx.database().connection().table("notes").insert(mapOf("id" to 2, "title" to "Tx"))
        ctx.assertions().assertDatabaseHas("notes", mapOf("id" to 2))
    }
}

@KonaTest(
    bootstrap = "org.konaframework.testing.FeatureTestBootstrap",
    base = "build/kona-test-fresh",
    freshApplication = true,
)
class KonaTestExtensionFreshAppTest {
    @Test
    fun bootsPerTest(app: Application) {
        assertEquals("testing", Env.getOrDefault("APP_ENV", ""))
        assertNotNull(app)
    }
}

