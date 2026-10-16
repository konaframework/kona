package hello

import bootstrap.App
import io.konaframework.core.Kona
import io.konaframework.http.HttpKernel
import io.konaframework.http.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AppSmokeTest {
    @AfterEach
    fun reset() = Kona.reset()

    private fun sampleBase(): Path {
        val sample = Path.of("samples/hello-kotlin").toAbsolutePath().normalize()
        return if (Files.isDirectory(Path.of("src/main/kotlin/bootstrap"))) {
            Path.of("").toAbsolutePath().normalize()
        } else {
            sample
        }
    }

    @Test
    fun welcomeAndHealth() {
        val base = sampleBase()
        val app = App.create(arrayOf("--base=$base"))
        val kernel = app.resolve(HttpKernel::class.java)
        val home = kernel.handle(Request.get("/"))
        val health = kernel.handle(Request.get("/api/health"))
        assertEquals(200, home.status)
        assertTrue(home.body.contains("<!DOCTYPE html>", ignoreCase = true), home.body)
        assertTrue(home.body.contains("Welcome", ignoreCase = true), home.body)
        assertEquals(200, health.status)
        assertEquals("{\"status\":\"ok\"}", health.body)
    }

    @Test
    fun sampleTreeUsesSrcMainLayout() {
        val base = sampleBase()
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/bootstrap/Middleware.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/routes/ApiRoutes.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/config/Paths.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/config/Session.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/resources/views/welcome.crema.html")))
        assertTrue(Files.isDirectory(base.resolve("storage")))
    }
}
