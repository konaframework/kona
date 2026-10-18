package sample

import bootstrap.App
import com.fasterxml.jackson.databind.ObjectMapper
import org.konaframework.core.Kona
import org.konaframework.http.HttpKernel
import org.konaframework.http.Request
import org.konaframework.inertia.Inertia
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InertiaSampleTest {
    private val json = ObjectMapper()

    @AfterEach
    fun reset() {
        Inertia.reset()
        Kona.reset()
    }

    private fun kernel(): HttpKernel {
        val local = Path.of("src/main/kotlin/bootstrap")
        val base = if (Files.isDirectory(local)) Path.of("") else Path.of("samples/inertia-kotlin")
        return App.create(arrayOf("--base=${base.toAbsolutePath().normalize()}")).resolve(HttpKernel::class.java)
    }

    @Test
    fun `first visit returns html shell`() {
        val response = kernel().handle(Request.get("/"))
        assertEquals(200, response.status)
        assertTrue(response.body.contains("data-page="), response.body)
        assertTrue(response.body.contains("Pages/Welcome"), response.body)
        assertTrue(response.body.contains("Welcome to Kona Inertia"), response.body)
    }

    @Test
    fun `inertia visit returns json page with shared props`() {
        val response = kernel().handle(
            Request(
                "GET",
                "/",
                headers = mapOf("X-Inertia" to "true", "X-Inertia-Version" to "1"),
            ),
        )
        assertEquals(200, response.status)
        assertEquals("true", response.headers["X-Inertia"])
        val page = json.readTree(response.body)
        assertEquals("Pages/Welcome", page["component"].asText())
        assertEquals("Welcome to Kona Inertia", page["props"]["message"].asText())
        assertEquals("Inertia sample (Kotlin)", page["props"]["appName"].asText())
        assertEquals("1", page["version"].asText())
    }
}
