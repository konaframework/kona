package hello

import dev.kona.core.Kona
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MainTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() = Kona.reset()

    @Test
    fun providerGroupsAndMiddleware() {
        val app = HelloKotlin.boot(arrayOf("--base=$temp"))
        assertTrue(app.getProvider(HelloServiceProvider::class.java)!!.booted)
        assertEquals(
            listOf(
                "200 Hello from Kotlin Kona [Kona]",
                "401 {\"error\":\"unauthorized\"}",
                "200 {\"hello\":\"Hello, kona\"}",
                "404 [Kona]",
                "route(api.hello) = /api/hello/kona",
            ),
            HelloKotlin.demo(app),
        )
    }
}
