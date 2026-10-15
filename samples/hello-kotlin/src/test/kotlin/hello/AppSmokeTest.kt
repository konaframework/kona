package hello

import bootstrap.App
import dev.kona.core.Kona
import dev.kona.http.HttpKernel
import dev.kona.http.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AppSmokeTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() = Kona.reset()

    @Test
    fun welcomeAndHealth() {
        val app = App.create(arrayOf("--base=$temp"))
        val kernel = app.resolve(HttpKernel::class.java)
        val home = kernel.handle(Request.get("/"))
        val health = kernel.handle(Request.get("/api/health"))
        assertEquals(200, home.status)
        assertEquals("Welcome to Kona", home.body)
        assertEquals(200, health.status)
        assertEquals("{\"status\":\"ok\"}", health.body)
    }

    @Test
    fun sampleTreeUsesSrcMainLayout() {
        val sample = Path.of("samples/hello-kotlin").toAbsolutePath().normalize()
        // When run from Gradle, cwd is sample project dir.
        val base = if (Files.isDirectory(Path.of("src/main/kotlin/bootstrap"))) {
            Path.of("").toAbsolutePath().normalize()
        } else {
            sample
        }
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/bootstrap/Middleware.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/routes/ApiRoutes.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/resources/config/paths.json")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/resources/config/session.json")))
        assertTrue(Files.isDirectory(base.resolve("storage")))
    }
}
