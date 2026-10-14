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
            ".gitignore",
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradlew",
            "gradlew.bat",
            "grind",
            "grind.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            "bootstrap/App.kt",
            "app/Providers/AppServiceProvider.kt",
            "app/Http/Controllers/WelcomeController.kt",
            "routes/web.kt",
        ).forEach { assertTrue(Files.isRegularFile(kt.resolve(it)), it) }
        assertTrue(Files.isExecutable(kt.resolve("gradlew")))
        assertTrue(Files.isExecutable(kt.resolve("grind")))
        assertFalse(Files.exists(kt.resolve("routes/web.kt.txt")))

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

    private fun assertNoPlaceholders(root: Path) {
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && !it.toString().endsWith(".jar") }.forEach {
                assertFalse(Files.readString(it).contains("{{"), "placeholder left in $it")
            }
        }
    }
}
