package org.konaframework.cli

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CliTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun helpVersionUnknown() {
        val app = KonaApplication()
        assertEquals(0, app.run(emptyArray()))
        assertEquals(0, app.run(arrayOf("help")))
        assertEquals(0, app.run(arrayOf("--help")))
        assertEquals(0, app.run(arrayOf("-h")))
        assertEquals(0, app.run(arrayOf("version")))
        assertEquals(0, app.run(arrayOf("--version")))
        assertEquals(0, app.run(arrayOf("-V")))
        assertEquals(1, app.run(arrayOf("nope")))
    }

    @Test
    fun resolveAndWriteKonaHomeProperty() {
        val fake = temp.resolve("kona-src")
        Files.createDirectories(fake.resolve("modules/cli"))
        Files.writeString(fake.resolve("settings.gradle.kts"), "rootProject.name = \"kona\"\n")
        Files.writeString(fake.resolve("gradlew"), "#!/bin/sh\n")
        fake.resolve("gradlew").toFile().setExecutable(true)

        assertEquals(fake, NewCommand.resolveKonaHome(env = mapOf("KONA_HOME" to fake.toString()), userHome = temp))
        assertTrue(NewCommand.isKonaRoot(fake))
        assertFalse(NewCommand.isKonaRoot(temp.resolve("missing")))
        assertFalse(
            NewCommand.isKonaRoot(
                temp.resolve("no-cli").also {
                    Files.createDirectories(it)
                    Files.writeString(it.resolve("settings.gradle.kts"), "x\n")
                    Files.writeString(it.resolve("gradlew"), "#!/bin/sh\n")
                    it.resolve("gradlew").toFile().setExecutable(true)
                },
            ),
        )
        assertFalse(
            NewCommand.isKonaRoot(
                temp.resolve("no-exec").also {
                    Files.createDirectories(it.resolve("modules/cli"))
                    Files.writeString(it.resolve("settings.gradle.kts"), "x\n")
                    Files.writeString(it.resolve("gradlew"), "#!/bin/sh\n")
                    it.resolve("gradlew").toFile().setExecutable(false)
                },
            ),
        )

        // Blank / invalid KONA_HOME falls through
        assertEquals(
            null,
            NewCommand.resolveKonaHome(env = mapOf("KONA_HOME" to ""), userHome = temp.resolve("empty-home").also { Files.createDirectories(it) }),
        )
        assertEquals(
            null,
            NewCommand.resolveKonaHome(
                env = mapOf("XDG_CONFIG_HOME" to ""),
                userHome = temp.resolve("empty-home-xdg").also { Files.createDirectories(it) },
            ),
        )
        assertEquals(
            null,
            NewCommand.resolveKonaHome(
                env = mapOf("KONA_HOME" to temp.resolve("not-a-kona").toString()),
                userHome = temp.resolve("empty-home-2").also { Files.createDirectories(it) },
            ),
        )

        val config = temp.resolve("xdg")
        Files.createDirectories(config.resolve("kona"))
        Files.writeString(config.resolve("kona/home"), fake.toString() + "\n")
        assertEquals(
            fake,
            NewCommand.resolveKonaHome(
                env = mapOf("XDG_CONFIG_HOME" to config.toString()),
                userHome = temp,
            ),
        )
        // Invalid recorded path ignored
        Files.writeString(config.resolve("kona/home"), temp.resolve("nope").toString())
        assertEquals(
            null,
            NewCommand.resolveKonaHome(
                env = mapOf("XDG_CONFIG_HOME" to config.toString()),
                userHome = temp.resolve("empty-home-3").also { Files.createDirectories(it) },
            ),
        )

        val defaultHome = temp.resolve("home")
        val defaultKona = defaultHome.resolve(".kona")
        Files.createDirectories(defaultKona.resolve("modules/cli"))
        Files.writeString(defaultKona.resolve("settings.gradle.kts"), "rootProject.name = \"kona\"\n")
        Files.writeString(defaultKona.resolve("gradlew"), "#!/bin/sh\n")
        defaultKona.resolve("gradlew").toFile().setExecutable(true)
        assertEquals(defaultKona, NewCommand.resolveKonaHome(env = emptyMap(), userHome = defaultHome))
        assertTrue(NewCommand.isUsableKonaHome(defaultKona))

        // Vendored Brew without template() is rejected (includeBuild would poison kona-brew).
        val staleBrew = temp.resolve("stale-brew-home")
        Files.createDirectories(staleBrew.resolve("modules/cli"))
        Files.createDirectories(staleBrew.resolve("modules/brew/src/main/kotlin/dev/kona/brew"))
        Files.writeString(staleBrew.resolve("settings.gradle.kts"), "rootProject.name = \"kona\"\n")
        Files.writeString(staleBrew.resolve("gradlew"), "#!/bin/sh\n")
        staleBrew.resolve("gradlew").toFile().setExecutable(true)
        Files.writeString(
            staleBrew.resolve("modules/brew/src/main/kotlin/dev/kona/brew/Component.kt"),
            "abstract class Component {\n    abstract fun render(): String\n}\n",
        )
        assertTrue(NewCommand.isKonaRoot(staleBrew))
        assertFalse(NewCommand.isUsableKonaHome(staleBrew))
        assertEquals(
            null,
            NewCommand.resolveKonaHome(env = mapOf("KONA_HOME" to staleBrew.toString()), userHome = temp),
        )

        val app = temp.resolve("wired-app")
        Files.createDirectories(app)
        NewCommand().writeKonaHomeProperty(app, null) // no-op
        assertFalse(Files.exists(app.resolve("gradle.properties")))

        NewCommand().writeKonaHomeProperty(app, fake)
        assertTrue(Files.readString(app.resolve("gradle.properties")).contains("kona.home=$fake"))

        // Replace existing kona.home line without trailing newline
        Files.writeString(app.resolve("gradle.properties"), "foo=1\nkona.home=/old")
        NewCommand().writeKonaHomeProperty(app, fake)
        val replaced = Files.readString(app.resolve("gradle.properties"))
        assertTrue(replaced.contains("kona.home=$fake"))
        assertEquals(1, replaced.lines().count { it.startsWith("kona.home=") })

        // Append onto empty file
        val app2 = temp.resolve("wired-app-2")
        Files.createDirectories(app2)
        Files.writeString(app2.resolve("gradle.properties"), "")
        NewCommand().writeKonaHomeProperty(app2, fake)
        assertTrue(Files.readString(app2.resolve("gradle.properties")).startsWith("# Local Kona"))
    }

    @Test
    fun newKotlinEmitsGradleApp() {
        val kt = temp.resolve("appkt")
        assertEquals(0, NewCommand().run(listOf(kt.toString())))
        listOf(
            "README.md",
            ".env",
            ".env.example",
            ".gitignore",
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradlew",
            "gradlew.bat",
            "grind",
            "grind.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            "src/main/kotlin/bootstrap/App.kt",
            "src/main/kotlin/bootstrap/Middleware.kt",
            "src/main/kotlin/app/providers/AppServiceProvider.kt",
            "src/main/kotlin/app/http/controllers/WelcomeController.kt",
            "src/main/kotlin/routes/WebRoutes.kt",
            "src/main/kotlin/routes/ApiRoutes.kt",
            "src/main/kotlin/config/App.kt",
            "src/main/kotlin/config/Paths.kt",
            "storage/logs/.gitkeep",
            "storage/database/.gitkeep",
            "database/.gitkeep",
        ).forEach { assertTrue(Files.isRegularFile(kt.resolve(it)), it) }
        assertTrue(Files.isExecutable(kt.resolve("gradlew")))
        assertTrue(Files.isExecutable(kt.resolve("grind")))
        assertFalse(Files.exists(kt.resolve("src/main/kotlin/routes/WebRoutes.kt.txt")))

        val versions = KonaVersions.load()
        val env = Files.readString(kt.resolve(".env"))
        assertTrue(env.contains("APP_NAME=appkt\n"))
        assertTrue(env.lineSequence().any { it.startsWith("APP_KEY=base64:") && it.length > "APP_KEY=base64:".length })
        assertTrue(Files.isRegularFile(kt.resolve("src/main/kotlin/config/Session.kt")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/kotlin/config/Filesystems.kt")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/kotlin/config/Database.kt")))
        assertTrue(
            Files.readString(kt.resolve("src/main/kotlin/config/Database.kt"))
                .contains("jdbc:sqlite:database/database.sqlite"),
        )
        assertTrue(Files.readString(kt.resolve(".gitignore")).contains("/database/*.sqlite"))
        assertTrue(Files.readString(kt.resolve("src/main/kotlin/bootstrap/App.kt")).contains(".configs("))
        assertTrue(Files.isRegularFile(kt.resolve("storage/app/public/.gitkeep")))
        val build = Files.readString(kt.resolve("build.gradle.kts"))
        assertTrue(build.contains("org.konaframework:kona-http:${versions.kona}"))
        assertTrue(build.contains("org.konaframework:kona-grind:${versions.kona}"))
        assertTrue(build.contains("org.konaframework:kona-session:${versions.kona}"))
        assertTrue(build.contains("org.konaframework:kona-auth:${versions.kona}"))
        assertTrue(build.contains("org.konaframework:kona-queue:${versions.kona}"))
        assertTrue(build.contains("org.xerial:sqlite-jdbc"))
        assertTrue(build.contains("tasks.register<JavaExec>(\"grind\")"))
        assertTrue(Files.readString(kt.resolve(".env")).contains("DB_CONNECTION=sqlite"))
        val appKt = Files.readString(kt.resolve("src/main/kotlin/bootstrap/App.kt"))
        assertTrue(appKt.contains("SessionServiceProvider"))
        assertTrue(appKt.contains("FilesystemServiceProvider"))
        assertTrue(appKt.contains("AuthServiceProvider"))
        assertTrue(appKt.contains("QueueServiceProvider"))
        assertTrue(appKt.contains("CremaServiceProvider"))
        val middleware = Files.readString(kt.resolve("src/main/kotlin/bootstrap/Middleware.kt"))
        assertTrue(middleware.contains("StartSession"))
        assertTrue(middleware.contains("VerifyCsrfToken"))
        assertTrue(middleware.contains("ServePublicStorage"))
        assertTrue(middleware.contains("ServePublicDirectory"))
        assertTrue(middleware.contains("alias(\"auth\""))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/resources/views/welcome.crema.html")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/resources/views/login.crema.html")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/resources/views/dashboard.crema.html")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/resources/public/branding/kona-logo.svg")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/kotlin/app/models/User.kt")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/kotlin/database/migrations/CreateUsersTable.kt")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/kotlin/database/seeders/DatabaseSeeder.kt")))
        assertTrue(Files.isRegularFile(kt.resolve("src/main/kotlin/app/http/controllers/api/UserController.kt")))
        assertTrue(
            Files.readString(kt.resolve("src/main/resources/META-INF/kona/migrations"))
                .contains("database.migrations.CreateUsersTable"),
        )
        assertTrue(
            Files.readString(kt.resolve("src/main/resources/views/welcome.crema.html"))
                .contains("docs.konaframework.org"),
        )
        assertTrue(
            Files.readString(kt.resolve("src/main/kotlin/app/http/controllers/WelcomeController.kt"))
                .contains("\"welcome\""),
        )
        assertTrue(
            Files.readString(kt.resolve("src/main/kotlin/app/providers/AppServiceProvider.kt"))
                .contains("CadenceUserProvider"),
        )
        assertTrue(Files.readString(kt.resolve("build.gradle.kts")).contains("kona-crema"))
        assertNoPlaceholders(kt)
        assertTrue(ScaffoldKeys.generateAppKey().startsWith("base64:"))

        val repoRoot = System.getProperty("kona.repoRoot")
        if (repoRoot != null) {
            assertArrayEquals(
                Files.readAllBytes(Path.of(repoRoot, "gradle/wrapper/gradle-wrapper.jar")),
                Files.readAllBytes(kt.resolve("gradle/wrapper/gradle-wrapper.jar")),
            )
        }

        assertEquals(1, KonaApplication().run(arrayOf("new", kt.toString(), "--lang=kotlin")))
        assertEquals(1, KonaApplication().run(arrayOf("new", temp.resolve("x2").toString(), "--lang=ruby")))
        assertEquals(1, KonaApplication().run(arrayOf("new")))
    }

    @Test
    fun newJavaEmitsGradleApp() {
        val j = temp.resolve("appjava")
        assertEquals(0, NewCommand().run(listOf(j.toString(), "--lang=java", "--kona-version=9.9.9")))
        assertTrue(Files.isRegularFile(j.resolve("grind")))
        val build = Files.readString(j.resolve("build.gradle.kts"))
        assertTrue(build.contains("org.konaframework:kona-http:9.9.9"))
        assertTrue(build.contains("org.konaframework:kona-grind:9.9.9"))
        assertTrue(build.contains("org.konaframework:kona-crema:9.9.9"))
        assertTrue(build.contains("org.xerial:sqlite-jdbc"))
        assertFalse(build.contains("kotlin(\"jvm\")"))
        assertTrue(Files.isRegularFile(j.resolve("src/main/resources/views/welcome.crema.html")))
        assertTrue(Files.isRegularFile(j.resolve("src/main/resources/views/login.crema.html")))
        assertTrue(Files.isRegularFile(j.resolve("src/main/java/app/models/User.java")))
        assertTrue(Files.isRegularFile(j.resolve("src/main/resources/public/branding/kona-logo.svg")))
        assertTrue(
            Files.readString(j.resolve("src/main/java/app/http/controllers/WelcomeController.java"))
                .contains("\"welcome\""),
        )
        assertTrue(
            Files.readString(j.resolve("src/main/java/app/providers/AppServiceProvider.java"))
                .contains("CadenceUserProvider"),
        )
        assertTrue(Files.readString(j.resolve("src/main/java/bootstrap/App.java")).contains("CremaServiceProvider"))
        assertNoPlaceholders(j)
    }

    @Test
    fun skeletonAndVersionErrors() {
        assertThrows(KonaCliException::class.java) {
            NewCommand().run(listOf(temp.resolve("x").toString(), "--lang=ruby"))
        }
        val target = temp.resolve("broken")
        Files.createDirectories(target)
        assertThrows(KonaCliException::class.java) {
            NewCommand().copySkeleton("broken", target, emptyMap())
        }
        assertThrows(KonaCliException::class.java) {
            NewCommand().copyResource("skeletons/nope/missing.txt", target.resolve("x"), emptyMap())
        }
        assertThrows(KonaCliException::class.java) { KonaVersions.load("org/konaframework/cli/missing.properties") }
        val custom = NewCommand(KonaVersions("1.2.3", "2.0.0"))
        val app = temp.resolve("custom")
        assertEquals(0, custom.run(listOf(app.toString())))
        assertTrue(Files.readString(app.resolve("build.gradle.kts")).contains("kona-http:1.2.3"))
    }

    @Test
    fun parseOptionsShape() {
        val (pos, opt) = parseOptions(listOf("a", "--flag", "--k=v"))
        assertEquals(listOf("a"), pos)
        assertEquals("true", opt["flag"])
        assertEquals("v", opt["k"])
    }

    @Test
    fun starterKitsOverlayFiles() {
        val crema = temp.resolve("crema-app")
        assertEquals(0, NewCommand().run(listOf(crema.toString(), "--kit=crema")))
        assertTrue(Files.readString(crema.resolve("build.gradle.kts")).contains("kona-crema"))
        assertTrue(Files.readString(crema.resolve("build.gradle.kts")).contains("org.xerial:sqlite-jdbc"))
        assertTrue(Files.isRegularFile(crema.resolve("src/main/resources/views/welcome.crema.html")))
        assertTrue(Files.readString(crema.resolve("src/main/kotlin/bootstrap/App.kt")).contains("CremaServiceProvider"))
        assertTrue(Files.readString(crema.resolve("README.md")).contains("Crema"))

        val inertia = temp.resolve("inertia-app")
        assertEquals(0, NewCommand().run(listOf(inertia.toString(), "--preset=inertia", "--lang=kotlin")))
        assertTrue(Files.readString(inertia.resolve("build.gradle.kts")).contains("kona-inertia"))
        assertTrue(Files.readString(inertia.resolve("build.gradle.kts")).contains("org.xerial:sqlite-jdbc"))
        assertTrue(Files.isRegularFile(inertia.resolve("package.json")))
        assertTrue(Files.isRegularFile(inertia.resolve("vite.config.js")))
        assertTrue(Files.isRegularFile(inertia.resolve("src/main/resources/js/app.js")))
        assertTrue(Files.isRegularFile(inertia.resolve("src/main/kotlin/app/http/middleware/HandleInertia.kt")))
        assertTrue(
            Files.readString(inertia.resolve("src/main/kotlin/app/http/controllers/WelcomeController.kt"))
                .contains("\"welcome\""),
        )
        assertTrue(Files.isRegularFile(inertia.resolve("src/main/resources/views/login.crema.html")))
        assertTrue(
            Files.readString(inertia.resolve("src/main/kotlin/routes/WebRoutes.kt")).contains("/dashboard"),
        )

        val brew = temp.resolve("brew-app")
        assertEquals(0, NewCommand().run(listOf(brew.toString(), "--kit=brew")))
        assertTrue(Files.readString(brew.resolve("build.gradle.kts")).contains("kona-brew"))
        assertTrue(Files.readString(brew.resolve("build.gradle.kts")).contains("org.xerial:sqlite-jdbc"))
        assertTrue(Files.isRegularFile(brew.resolve("src/main/kotlin/app/brew/Counter.kt")))
        assertFalse(Files.exists(brew.resolve("src/main/kotlin/app/brew/Todos.kt")))
        assertFalse(Files.exists(brew.resolve("src/main/resources/views/brew/todos.crema.html")))
        val brewWelcome = Files.readString(brew.resolve("src/main/resources/views/welcome.crema.html"))
        assertTrue(brewWelcome.contains("<brew:counter"), brewWelcome)
        assertTrue(brewWelcome.contains("docs.konaframework.org"), brewWelcome)
        // Tip text must not be the only mention — require a live tag, not an HTML-escaped example.
        assertFalse(brewWelcome.contains("&lt;brew:counter"), brewWelcome)
        assertTrue(Files.readString(brew.resolve("src/main/kotlin/bootstrap/App.kt")).contains("BrewServiceProvider"))
        assertFalse(Files.readString(brew.resolve("src/main/kotlin/bootstrap/App.kt")).contains("Todos::class.java"))
        assertTrue(
            Files.readString(brew.resolve("src/main/kotlin/app/http/controllers/WelcomeController.kt"))
                .contains("\"welcome\""),
        )
        assertTrue(Files.isRegularFile(brew.resolve("src/main/kotlin/app/models/User.kt")))
        assertTrue(Files.isRegularFile(brew.resolve("src/main/resources/views/login.crema.html")))

        val brewJava = temp.resolve("brew-java-app")
        assertEquals(0, NewCommand().run(listOf(brewJava.toString(), "--kit=brew", "--lang=java")))
        assertTrue(Files.isRegularFile(brewJava.resolve("src/main/java/app/brew/Counter.java")))
        assertTrue(
            Files.readString(brewJava.resolve("src/main/resources/views/welcome.crema.html"))
                .contains("<brew:counter"),
        )

        val javaInertia = temp.resolve("java-inertia")
        assertEquals(0, NewCommand().run(listOf(javaInertia.toString(), "--lang=java", "--kit=inertia")))
        assertTrue(Files.readString(javaInertia.resolve("build.gradle.kts")).contains("kona-inertia"))
        assertTrue(Files.isRegularFile(javaInertia.resolve("src/main/java/app/http/middleware/HandleInertia.java")))

        assertEquals(1, KonaApplication().run(arrayOf("new", temp.resolve("badkit").toString(), "--kit=nope")))
        assertThrows(KonaCliException::class.java) {
            NewCommand().run(listOf(temp.resolve("x").toString(), "--preset=rails"))
        }
        assertThrows(KonaCliException::class.java) {
            NewCommand().applyKit("kotlin", "nope", temp.resolve("z"), emptyMap())
        }
        // Empty overlays: unknown kit keys / unknown lang hit orEmpty branches.
        val emptyOverlay = temp.resolve("empty-overlay")
        Files.createDirectories(emptyOverlay)
        NewCommand().overlayKit("missing-kit", "kotlin", emptyOverlay, emptyMap())
        NewCommand().overlayKit("crema", "ruby", emptyOverlay, emptyMap())
        assertEquals(0, NewCommand().run(listOf(temp.resolve("api-explicit").toString(), "--kit=api")))
        val apiApp = temp.resolve("api-explicit")
        assertFalse(Files.readString(apiApp.resolve("build.gradle.kts")).contains("kona-crema"))
        assertFalse(Files.exists(apiApp.resolve("src/main/resources/views/welcome.crema.html")))
        assertTrue(Files.isRegularFile(apiApp.resolve("src/main/kotlin/app/models/User.kt")))
        assertTrue(Files.isRegularFile(apiApp.resolve("src/main/kotlin/app/http/controllers/api/UserController.kt")))
        assertTrue(
            Files.readString(apiApp.resolve("src/main/kotlin/routes/ApiRoutes.kt")).contains("/users"),
        )

        val webExplicit = temp.resolve("web-explicit")
        assertEquals(0, NewCommand().run(listOf(webExplicit.toString(), "--kit=web")))
        assertTrue(Files.isRegularFile(webExplicit.resolve("src/main/resources/views/welcome.crema.html")))
        assertTrue(Files.isRegularFile(webExplicit.resolve("src/main/resources/views/dashboard.crema.html")))
        assertTrue(
            Files.readString(webExplicit.resolve("src/main/kotlin/routes/WebRoutes.kt")).contains("/login"),
        )
    }

    private fun assertNoPlaceholders(root: Path) {
        val scaffoldTokens = listOf("{{appName}}", "{{konaVersion}}", "{{kotlinVersion}}", "{{kit}}", "{{appKey}}")
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && !it.toString().endsWith(".jar") }.forEach { file ->
                val text = Files.readString(file)
                scaffoldTokens.forEach { token ->
                    assertFalse(text.contains(token), "placeholder $token left in $file")
                }
            }
        }
    }
}
