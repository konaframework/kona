package org.konaframework.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class PathLayoutTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() = Kona.reset()

    @Test
    fun detectsKotlinAndJavaLangRoots() {
        assertEquals(PathLayout.KOTLIN_ROOT, PathLayout.detectLangRoot(temp))
        val emptyJava = temp.resolve("src/main/java/app")
        Files.createDirectories(emptyJava)
        assertEquals(PathLayout.KOTLIN_ROOT, PathLayout.detectLangRoot(temp))
        val javaFile = emptyJava.resolve("X.java")
        Files.writeString(javaFile, "class X {}")
        assertEquals(PathLayout.JAVA_ROOT, PathLayout.detectLangRoot(temp))
    }

    @Test
    fun helpersResolveUnderSrcMain() {
        val layout = PathLayout.detect(temp)
        assertEquals(temp.resolve("src/main/kotlin/app/http"), layout.app("http"))
        assertEquals(temp.resolve("src/main/kotlin/config"), layout.config())
        assertEquals(temp.resolve("src/main/resources/views/home"), layout.view("home"))
        assertEquals(temp.resolve("src/main/resources/public"), layout.publicPath())
        assertEquals(temp.resolve("storage/logs"), layout.storage("logs"))
        assertEquals(temp.resolve("src/main/resources/lang/en"), layout.lang("en"))
        assertEquals(layout.app("http"), layout.join(layout.langRoot.resolve("app"), arrayOf("http", "")))
    }

    @Test
    fun configOverridesAndApplicationHelpers() {
        Files.writeString(temp.resolve(".env"), "APP_KEY=test-key\n")
        val paths = ConfigFile.of("paths") {
            mapOf(
                "langRoot" to "src/main/java",
                "resources" to "custom/resources",
                "storage" to "var/storage",
                "public" to "custom/public",
            )
        }
        val seen = AtomicReference<Application?>(null)
        val app = Kona.create(arrayOf("--base=$temp"))
            .configs(paths)
            .afterBoot { seen.set(it) }
            .boot()
        assertSame(app, seen.get())
        assertSame(app.pathLayout, app.pathLayout)
        assertEquals(temp.resolve("src/main/java/app"), app.appPath())
        assertEquals(temp.resolve("var/storage/logs"), app.storagePath("logs"))
        assertEquals(temp.resolve("src/main/java/config"), app.configPath())
        assertEquals(temp.resolve("custom/public"), app.publicPath())
        assertEquals("test-key", app.config.getString("app.key"))

        assertEquals(app.storagePath("x"), Paths.storage("x"))
        assertEquals(app.configPath(), Paths.config())
        assertEquals(app.appPath(), Paths.app())
        assertEquals(app.viewPath(), Paths.view())
        assertEquals(app.publicPath(), Paths.publicDir())
        assertEquals(app.bootstrapPath(), Paths.bootstrap())
        assertEquals(app.routesPath(), Paths.routes())
        assertEquals(app.databasePath(), Paths.database())
        assertEquals(app.langPath(), Paths.lang())
        assertEquals(app.resourcePath(), Paths.resource())
        assertEquals(app.basePath(), Paths.base())
    }
}
