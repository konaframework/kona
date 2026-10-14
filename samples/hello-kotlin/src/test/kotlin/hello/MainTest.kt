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
}
