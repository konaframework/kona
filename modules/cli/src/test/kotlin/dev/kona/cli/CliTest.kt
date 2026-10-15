package dev.kona.cli

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
            "src/main/resources/config/app.json",
            "src/main/resources/config/paths.json",
            "storage/logs/.gitkeep",
        ).forEach { assertTrue(Files.isRegularFile(kt.resolve(it)), it) }
        assertTrue(Files.isExecutable(kt.resolve("gradlew")))
        assertTrue(Files.isExecutable(kt.resolve("grind")))
        assertFalse(Files.exists(kt.resolve("src/main/kotlin/routes/WebRoutes.kt.txt")))

        val versions = KonaVersions.load()
        assertTrue(Files.readString(kt.resolve(".env")).contains("APP_NAME=appkt\n"))
        val build = Files.readString(kt.resolve("build.gradle.kts"))
        assertTrue(build.contains("dev.kona:kona-http:${versions.kona}"))
        assertTrue(build.contains("dev.kona:kona-grind:${versions.kona}"))
        assertTrue(build.contains("tasks.register<JavaExec>(\"grind\")"))
        assertNoPlaceholders(kt)

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
        assertTrue(build.contains("dev.kona:kona-http:9.9.9"))
        assertTrue(build.contains("dev.kona:kona-grind:9.9.9"))
        assertFalse(build.contains("kotlin(\"jvm\")"))
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
        assertThrows(KonaCliException::class.java) { KonaVersions.load("dev/kona/cli/missing.properties") }
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
        assertTrue(Files.isRegularFile(crema.resolve("src/main/resources/views/welcome.crema.html")))
        assertTrue(Files.readString(crema.resolve("src/main/kotlin/bootstrap/App.kt")).contains("CremaServiceProvider"))
        assertTrue(Files.readString(crema.resolve("README.md")).contains("Crema"))

        val inertia = temp.resolve("inertia-app")
        assertEquals(0, NewCommand().run(listOf(inertia.toString(), "--preset=inertia", "--lang=kotlin")))
        assertTrue(Files.readString(inertia.resolve("build.gradle.kts")).contains("kona-inertia"))
        assertTrue(Files.isRegularFile(inertia.resolve("package.json")))
        assertTrue(Files.isRegularFile(inertia.resolve("vite.config.js")))
        assertTrue(Files.isRegularFile(inertia.resolve("src/main/resources/js/app.js")))
        assertTrue(Files.isRegularFile(inertia.resolve("src/main/kotlin/app/http/middleware/HandleInertia.kt")))
        assertTrue(Files.readString(inertia.resolve("src/main/kotlin/app/http/controllers/WelcomeController.kt")).contains("Inertia.render"))

        val brew = temp.resolve("brew-app")
        assertEquals(0, NewCommand().run(listOf(brew.toString(), "--kit=brew")))
        assertTrue(Files.readString(brew.resolve("build.gradle.kts")).contains("kona-brew"))
        assertTrue(Files.isRegularFile(brew.resolve("src/main/kotlin/app/brew/Counter.kt")))
        assertTrue(Files.readString(brew.resolve("src/main/kotlin/bootstrap/App.kt")).contains("BrewServiceProvider"))

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
    }

    private fun assertNoPlaceholders(root: Path) {
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && !it.toString().endsWith(".jar") }.forEach {
                assertFalse(Files.readString(it).contains("{{"), "placeholder left in $it")
            }
        }
    }
}
