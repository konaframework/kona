package org.konaframework.testing

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.platform.commons.support.AnnotationSupport
import org.konaframework.cadence.Database
import org.konaframework.core.Application
import org.konaframework.core.Env
import org.konaframework.core.Kona
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.nio.file.Path

private val EXTENSION_NAMESPACE: ExtensionContext.Namespace =
    ExtensionContext.Namespace.create(KonaTestExtension::class.java)
private const val APP_KEY = "app"
private const val CONTEXT_KEY = "context"
private val SUPPORTED_PARAMETERS = setOf(
    KonaTestContext::class.java,
    Application::class.java,
    HttpTestClient::class.java,
    DatabaseAssertions::class.java,
)

/**
 * JUnit 5 extension behind [KonaTest]: boots the app's bootstrap class under `APP_ENV=testing`
 * (once per class, or per test with `freshApplication`), applies [RefreshDatabase] /
 * [LazilyRefreshDatabase] / [DatabaseTransactions], and injects [KonaTestContext], the
 * [Application], an [HttpTestClient] and [DatabaseAssertions] into test and lifecycle methods.
 */
class KonaTestExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {
    override fun beforeAll(context: ExtensionContext) {
        if (!settings(context).freshApplication) {
            context.getStore(EXTENSION_NAMESPACE).put(APP_KEY, boot(settings(context)))
        }
    }

    override fun afterAll(context: ExtensionContext) {
        context.getStore(EXTENSION_NAMESPACE).remove(APP_KEY, Application::class.java)?.let { shutdown(it) }
    }

    override fun beforeEach(context: ExtensionContext) {
        val store = context.getStore(EXTENSION_NAMESPACE)
        val app = if (settings(context).freshApplication) {
            boot(settings(context)).also { store.put(APP_KEY, it) }
        } else {
            store.get(APP_KEY, Application::class.java)
        }
        val database = lifecycle(context.requiredTestClass)?.let { (migrate, lazily) ->
            TestDatabase(app, migrate, lazily).also { it.begin() }
        }
        store.put(CONTEXT_KEY, KonaTestContext(app, database))
    }

    override fun afterEach(context: ExtensionContext) {
        val store = context.getStore(EXTENSION_NAMESPACE)
        val test = store.remove(CONTEXT_KEY, KonaTestContext::class.java)
        if (test != null) {
            val database = test.testDatabase()
            if (database != null) {
                database.rollback()
            }
        }
        if (settings(context).freshApplication) {
            val app = store.remove(APP_KEY, Application::class.java)
            if (app != null) {
                shutdown(app)
            }
        }
    }

    override fun supportsParameter(parameter: ParameterContext, context: ExtensionContext): Boolean =
        parameter.parameter.type in SUPPORTED_PARAMETERS

    override fun resolveParameter(parameter: ParameterContext, context: ExtensionContext): Any {
        val store = context.getStore(EXTENSION_NAMESPACE)
        val type = parameter.parameter.type
        if (type == Application::class.java) {
            return store.get(APP_KEY, Application::class.java)
                ?: throw ParameterResolutionException("The application is not booted for this test")
        }
        val test = store.get(CONTEXT_KEY, KonaTestContext::class.java)
            ?: throw ParameterResolutionException("${type.simpleName} is available in test methods and @BeforeEach / @AfterEach only")
        return when (type) {
            HttpTestClient::class.java -> test.http()
            DatabaseAssertions::class.java -> test.assertions()
            else -> test
        }
    }

    private fun settings(context: ExtensionContext): KonaTest =
        AnnotationSupport.findAnnotation(context.requiredTestClass, KonaTest::class.java)
            .orElseThrow { IllegalStateException("${context.requiredTestClass.name} is missing @KonaTest") }

    /** `(migrate, lazily)` for the database annotation on [type], or null for none. */
    private fun lifecycle(type: Class<*>): Pair<Boolean, Boolean>? = when {
        AnnotationSupport.isAnnotated(type, RefreshDatabase::class.java) -> true to false
        AnnotationSupport.isAnnotated(type, LazilyRefreshDatabase::class.java) -> true to true
        AnnotationSupport.isAnnotated(type, DatabaseTransactions::class.java) -> false to false
        else -> null
    }

    private fun boot(settings: KonaTest): Application {
        Kona.reset()
        Env.set("APP_ENV", settings.environment)
        val base = if (settings.base.isBlank()) Path.of("") else Path.of(settings.base)
        val type = Class.forName(settings.bootstrap)
        val create = type.methods.firstOrNull {
            it.name == "create" && it.parameterTypes.contentEquals(arrayOf(Array<String>::class.java))
        } ?: throw IllegalStateException("${settings.bootstrap} has no create(String[]) method")
        val target = if (Modifier.isStatic(create.modifiers)) null else type.getField("INSTANCE").get(null)
        try {
            return create.invoke(target, arrayOf("--base=${base.toAbsolutePath().normalize()}")) as Application
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun shutdown(app: Application) {
        if (app.container.bound(Database::class.java)) app.resolve(Database::class.java).close()
        Kona.reset()
    }
}

/**
 * What a [KonaTest] test works with: the booted [app], one [HttpTestClient] for the test (its
 * cookie jar and session live as long as the test), and the database helpers. [http],
 * [database] and [assertions] start a lazy database transaction first.
 */
class KonaTestContext internal constructor(
    val app: Application,
    private val testDatabase: TestDatabase?,
) {
    private var client: HttpTestClient? = null

    /** The test's HTTP client (the same instance on every call). */
    fun http(): HttpTestClient {
        ensureDatabase()
        return client ?: HttpTestClient(app).also { client = it }
    }

    /** The app's [Database]. */
    fun database(): Database {
        ensureDatabase()
        return app.resolve(Database::class.java)
    }

    /** Database assertions on the test's connection. */
    fun assertions(): DatabaseAssertions {
        ensureDatabase()
        return DatabaseAssertions(app.resolve(Database::class.java))
    }

    /** Open the transaction now when the test is [LazilyRefreshDatabase] (no-op otherwise). */
    fun ensureDatabase() {
        testDatabase?.ensureStarted()
    }

    /** The database lifecycle, or null when the class has no database annotation. */
    fun testDatabase(): TestDatabase? = testDatabase
}
