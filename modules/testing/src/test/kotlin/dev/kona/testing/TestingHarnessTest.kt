package dev.kona.testing

import dev.kona.extract.ConnectionConfig
import dev.kona.extract.Database
import dev.kona.http.HttpServiceProvider
import dev.kona.http.Response
import dev.kona.http.Router
import dev.kona.mail.MailMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TestingHarnessTest {
    @Test
    fun httpConsoleDatabaseAndFakes(@TempDir base: Path) {
        val app = TestApplication.create(base, HttpServiceProvider())
        app.resolve(Router::class.java).get("/ping") { Response.json(mapOf("ok" to true)) }
        app.resolve(Router::class.java).post("/echo") { r -> Response.json(mapOf("body" to r.body)) }

        val http = HttpTestClient(app)
        http.get("/ping").assertOk().assertJsonContains("ok")
        http.get("/ping", mapOf("X-Test" to "1")).assertOk()
        http.postJson("/echo", mapOf("a" to 1)).assertStatus(200).assertJsonContains("a")
        http.postJson("/echo", mapOf("b" to 2), mapOf("X-Test" to "1")).assertOk()
        assertThrows(IllegalArgumentException::class.java) { http.get("/ping").assertStatus(404) }
        assertThrows(IllegalArgumentException::class.java) { http.get("/ping").assertJsonContains("missing") }

        val console = ConsoleTester()
        console.run("list").assertOk().assertSee("list")
        assertThrows(IllegalArgumentException::class.java) { console.run("list").assertSee("___nope___") }
        assertThrows(IllegalArgumentException::class.java) { ConsoleResult(1, "x").assertOk() }

        val database = Database().addConnection(
            "default",
            ConnectionConfig("jdbc:h2:mem:testing_${System.nanoTime()};DB_CLOSE_DELAY=-1", "sa", "", poolSize = 2),
        )
        database.connection().statement("CREATE TABLE items (id INT)")
        database.connection().statement("INSERT INTO items VALUES (1)")
        DatabaseTest.refresh(database, listOf("items"))
        val count = DatabaseTest.transactional(database) { db ->
            db.connection().statement("INSERT INTO items VALUES (2)")
            1
        }
        assertEquals(1, count)

        val mail = FakeMail()
        mail.mailer.send(MailMessage().also { it.subject = "hi"; it.html("body") })
        assertEquals(1, mail.messages().size)

        val queue = FakeQueue()
        queue.dispatch { }
        assertTrue(queue.size() >= 0)

        val storage = FakeStorage()
        storage.put("a.txt", "hello")
        storage.put("dir/b.txt", "x".toByteArray())
        assertEquals("hello", storage.getString("a.txt"))
        assertEquals(null, storage.get("missing"))
        assertEquals(null, storage.getString("missing"))
        assertTrue(storage.exists("a.txt"))
        assertEquals(5, storage.size("a.txt"))
        assertTrue(storage.lastModified("a.txt") > 0)
        assertEquals(0, storage.lastModified("missing"))
        assertEquals(0, storage.size("missing"))
        assertTrue(storage.copy("a.txt", "c.txt"))
        assertFalse(storage.copy("missing", "z"))
        assertTrue(storage.move("c.txt", "d.txt"))
        assertFalse(storage.move("missing", "z"))
        assertTrue(storage.delete("d.txt"))
        assertFalse(storage.delete("missing"))
        storage.makeDirectory("nested")
        storage.makeDirectory("nested/deep")
        assertTrue(storage.exists("nested"))
        assertTrue(storage.files("").contains("a.txt"))
        assertTrue(storage.files("dir").contains("b.txt"))
        assertTrue(storage.directories("").contains("nested"))
        assertTrue(storage.directories("nested").any { it.contains("deep") })
        storage.deleteDirectory("dir")
        assertFalse(storage.exists("dir/b.txt"))
        assertEquals("/a.txt", storage.url("a.txt"))
        assertEquals("a.txt", storage.path("a.txt"))

        val notifications = FakeNotification()
        val user = object : dev.kona.notifications.Notifiable {
            override fun routeNotificationFor(channel: String): String? = "user-1"
        }
        notifications.send(user, WelcomeNotice())
        assertTrue(notifications.assertSent(WelcomeNotice::class.java))
        assertFalse(notifications.assertSent(UnusedNotice::class.java))
        val logOnly = FakeNotification()
        logOnly.send(user, LogNotice())
        assertTrue(logOnly.assertSent(LogNotice::class.java))
    }

    class WelcomeNotice : dev.kona.notifications.Notification() {
        override fun via(notifiable: dev.kona.notifications.Notifiable) = listOf("database", "log")
        override fun toArray(notifiable: dev.kona.notifications.Notifiable) = mapOf("hi" to true)
    }

    class LogNotice : dev.kona.notifications.Notification() {
        override fun via(notifiable: dev.kona.notifications.Notifiable) = listOf("log")
        override fun toArray(notifiable: dev.kona.notifications.Notifiable) = mapOf("x" to 1)
    }

    class UnusedNotice : dev.kona.notifications.Notification() {
        override fun via(notifiable: dev.kona.notifications.Notifiable) = listOf("database")
    }
}
