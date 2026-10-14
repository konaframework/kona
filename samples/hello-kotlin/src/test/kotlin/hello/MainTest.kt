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

    @Test
    fun extractNotesDemo() {
        assertEquals(
            listOf(
                "migrated [CreateNotebooksAndNotes]",
                "Notebook 1: 1 notes",
                "Notebook 2: 2 notes",
                "first note belongs to Notebook 1",
            ),
            NotesDemo.run("jdbc:h2:mem:hello_notes_test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
        )
    }

    @Test
    fun securitySessionAndAuthDemo() {
        val lines = SecurityDemo.run(arrayOf("--base=$temp"))
        assertEquals("flash1=200:{\"prior\":\"\"}", lines[0])
        assertEquals("flash2=200:{\"prior\":\"saved\"}", lines[1])
        assertTrue(lines[2].startsWith("login=200:"))
        assertTrue(lines[2].contains("\"ok\":true"))
        assertTrue(lines[2].contains("\"id\":1"))
    }

    @Test
    fun supportCacheAndRedisDemo() {
        val result = SupportCacheDemo.run(temp)
        assertEquals("hello-kona", result["slug"])
        assertEquals(listOf("Ada", "Grace"), result["names"])
        assertEquals("PONG", result["redis"])
    }

    @Test
    fun httpPolishDemo() {
        val result = HttpPolishDemo.run(arrayOf("--base=$temp"))
        assertTrue(result["index"]!!.startsWith("200:"))
        assertTrue(result["index"]!!.contains("Ada"))
        assertTrue(result["created"]!!.startsWith("201:"))
        assertEquals("https://demo.test/notes", result["route"])
        assertEquals("https://demo.test/notes", result["signed"])
        assertEquals("pong", result["ping"])
    }

    @Test
    fun cremaDemo() {
        val result = CremaDemo.run(temp)
        assertTrue(result["file"]!!.contains("Hi Kona"))
        assertEquals("pong 1", result["memory"])
    }

    @Test
    fun brewDemo() {
        val lines = BrewDemo.run(arrayOf("--base=$temp"))
        assertEquals("mount=true", lines[0])
        assertEquals("increment=true", lines[1])
        assertEquals("crema=true", lines[2])
        assertEquals("script=200", lines[3])
    }

    @Test
    fun inertiaDemo() {
        val lines = InertiaDemo.run(arrayOf("--base=$temp"), temp.resolve("public"))
        assertEquals("html=true", lines[0])
        assertEquals("json=true", lines[1])
        assertEquals("conflict=409", lines[2])
        assertEquals("vite=true", lines[3])
        assertEquals("manifest=assets/app.js", lines[4])
    }

    @Test
    fun asyncDemo() {
        val result = AsyncDemo.run(temp)
        assertEquals("42", result["event"])
        assertEquals("1", result["jobs"])
        assertEquals("1", result["mail"])
        assertEquals("stored", result["file"])
    }
}
