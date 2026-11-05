package org.konaframework.testing

import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Boots the application's own bootstrap class for a JUnit 5 test class (Laravel `TestCase`).
 *
 * ```kotlin
 * @KonaTest(bootstrap = "bootstrap.App")
 * @RefreshDatabase
 * class LoginTest {
 *     @Test fun cashierCanSignIn(t: KonaTestContext) {
 *         t.http().post("/login", mapOf("email" to "c@example.com", "password" to "secret"))
 *             .assertRedirect("/pos")
 *     }
 * }
 * ```
 *
 * The extension sets `APP_ENV=[environment]` before booting, so `.env.{environment}` (default
 * `.env.testing`) layers over `.env`, and `app.env` reads `testing`. A real process environment
 * variable `APP_ENV` still wins. Test methods and lifecycle methods can take a [KonaTestContext],
 * the booted [org.konaframework.core.Application], an [HttpTestClient] or a [DatabaseAssertions].
 *
 * @property bootstrap Fully qualified class with `create(String[])` returning the booted
 *   `Application` (the `bootstrap.App` every Kona app has). Kotlin objects need `@JvmStatic`.
 * @property base Application base path; empty means the working directory (the module directory
 *   under Gradle).
 * @property environment Value of `APP_ENV` for the run.
 * @property freshApplication Boot a new application for every test instead of once per class.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@ExtendWith(KonaTestExtension::class)
annotation class KonaTest(
    val bootstrap: String,
    val base: String = "",
    val environment: String = "testing",
    val freshApplication: Boolean = false,
)

/** Migrate once per booted app, then run every test of the class in a rolled-back transaction. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class RefreshDatabase

/** Like [RefreshDatabase], but migrates and opens the transaction only when a test touches the database through the harness. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class LazilyRefreshDatabase

/** Run every test in a rolled-back transaction without migrating (the schema is already there). */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class DatabaseTransactions
