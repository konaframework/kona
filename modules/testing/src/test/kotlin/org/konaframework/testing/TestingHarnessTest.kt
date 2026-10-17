package org.konaframework.testing

import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.mail.MailMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TestingHarnessTest {
    @Test
    fun httpConsoleDatabaseAndFakes(@TempDir base: Path) {
        val app = TestApplication.create(base, HttpServiceProvider())
        val router = app.resolve(Router::class.java)
        router.get("/ping") { Response.json(mapOf("ok" to true, "user" to mapOf("name" to "Ada"))) }
        router.get("/types") {
            Response.json(
                mapOf(
                    "n" to null,
                    "f" to 1.5,
                    "i" to 7,
                    "nested" to mapOf("list" to listOf(1, 2)),
                    "s" to "x",
                ),
            )
        }
        router.post("/echo") { r -> Response.json(mapOf("body" to r.body)) }
        router.put("/echo") { r -> Response.json(mapOf("method" to "PUT", "body" to r.body)) }
        router.patch("/echo") { r -> Response.json(mapOf("method" to "PATCH", "body" to r.body)) }
        router.delete("/gone") { Response.json(mapOf("deleted" to true)) }

        val http = HttpTestClient(app)
        http.get("/ping").assertOk().assertJsonContains("ok")
        http.get("/ping").assertJsonPath("user.name", "Ada").assertJson("ok", true)
        http.get("/types").assertJsonPath("n", null)
        http.get("/types").assertJsonPath("f", 1.5)
        http.get("/types").assertJsonPath("i", 7)
        http.get("/types").assertJsonPath("i", 7L)
        http.get("/types").assertJsonPath("nested.list", listOf(1, 2))
        http.get("/types").assertJsonPath("nested", mapOf("list" to listOf(1, 2)))
        http.get("/types").assertJsonPath("s", "x")
        http.get("/types").assertJsonPath("i", "7")
        http.get("/types").assertJsonPath("nested..list", listOf(1, 2))
        assertThrows(IllegalArgumentException::class.java) { http.get("/types").assertJsonPath("n", "x") }
        assertThrows(IllegalArgumentException::class.java) { http.get("/types").assertJsonPath("s", null) }
        assertThrows(IllegalArgumentException::class.java) { http.get("/types").assertJsonPath("s", "nope") }
        assertThrows(IllegalArgumentException::class.java) { http.get("/types").assertJsonPath("i", "8") }
        TestResponse(Response.json(mapOf("a" to true))).assertJson("a", true)
        http.get("/ping", mapOf("X-Test" to "1")).assertOk()
        http.postJson("/echo", mapOf("a" to 1)).assertStatus(200).assertJsonContains("a")
        http.postJson("/echo", mapOf("b" to 2), mapOf("X-Test" to "1")).assertOk()
        http.put("/echo", "plain").assertOk().assertJsonContains("PUT")
        http.putJson("/echo", mapOf("k" to "v")).assertOk().assertJsonContains("k")
        http.putJson("/echo", mapOf("k" to 1), mapOf("X-Test" to "1")).assertOk()
        http.put("/echo").assertOk()
        http.patch("/echo", "p").assertOk().assertJsonContains("PATCH")
        http.patchJson("/echo", mapOf("p" to true)).assertOk().assertJsonContains("p")
        http.patchJson("/echo", mapOf("p" to 2), mapOf("X-Test" to "1")).assertOk()
        http.patch("/echo").assertOk()
        http.delete("/gone").assertOk().assertJson("deleted", true)
        http.delete("/gone", mapOf("X-Test" to "1")).assertOk()
        assertThrows(IllegalArgumentException::class.java) { http.get("/ping").assertStatus(404) }
        assertThrows(IllegalArgumentException::class.java) { http.get("/ping").assertJsonContains("missing") }
        assertThrows(IllegalArgumentException::class.java) { http.get("/ping").assertJsonPath("user.name", "Wrong") }
        assertThrows(IllegalArgumentException::class.java) { http.get("/ping").assertJsonPath("missing.path", 1) }

        val console = ConsoleTester()
        console.run("list").assertOk().assertSee("list").assertExit(0).assertDontSee("___nope___")
        assertThrows(IllegalArgumentException::class.java) { console.run("list").assertSee("___nope___") }
        assertThrows(IllegalArgumentException::class.java) { ConsoleResult(1, "x").assertOk() }
        assertThrows(IllegalArgumentException::class.java) { ConsoleResult(1, "x").assertExit(0) }
        assertThrows(IllegalArgumentException::class.java) { ConsoleResult(0, "seen").assertDontSee("seen") }

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
        assertTrue(storage.missing("gone"))
        assertFalse(storage.missing("a.txt"))
        assertEquals("application/octet-stream", storage.mimeType("a.txt"))
        assertEquals(null, storage.mimeType("gone"))
        assertNotNull(storage.checksum("a.txt", "SHA-256"))
        assertEquals(null, storage.checksum("gone", "SHA-256"))
        assertTrue(storage.allFiles("").contains("a.txt"))
        assertTrue(storage.allDirectories("").contains("nested"))
        assertTrue(storage.allDirectories("nested").any { it.contains("deep") })
        storage.append("a.txt", "!")
        assertEquals("hello!", storage.getString("a.txt"))
        storage.append("new-append.txt", "only")
        assertEquals("only", storage.getString("new-append.txt"))
        storage.prepend("a.txt", ">")
        assertEquals(">hello!", storage.getString("a.txt"))
        storage.prepend("new-prepend.txt", "first")
        assertEquals("first", storage.getString("new-prepend.txt"))
        storage.put("nested/x.txt", "n")
        assertTrue(storage.allFiles("nested").any { it.endsWith("x.txt") })
        assertTrue(storage.files("nested").contains("x.txt"))
        assertTrue(storage.files("nested/").contains("x.txt"))
        storage.readStream("a.txt")!!.use { assertTrue(it.readAllBytes().isNotEmpty()) }
        assertEquals(null, storage.readStream("gone"))
        storage.writeStream("stream.txt", java.io.ByteArrayInputStream("s".toByteArray()))
        assertEquals("s", storage.getString("stream.txt"))
        val tmpFile = base.resolve("upload.bin")
        java.nio.file.Files.writeString(tmpFile, "blob")
        val uploaded = storage.putFile("uploads", tmpFile)
        assertTrue(storage.exists(uploaded))
        assertEquals("uploads/named.bin", storage.putFileAs("uploads", tmpFile, "named.bin"))
        assertEquals("root.bin", storage.putFileAs("", tmpFile, "root.bin"))
        storage.setVisibility("a.txt", "public")
        assertEquals("public", storage.getVisibility("a.txt"))
        assertEquals("private", storage.getVisibility("unset.txt"))
        assertFalse(storage.providesTemporaryUrls())
        assertThrows(IllegalStateException::class.java) { storage.temporaryUrl("a.txt", 60) }
        storage.copy("a.txt", "vis-copy.txt")
        assertEquals("public", storage.getVisibility("vis-copy.txt"))

        val notifications = FakeNotification()
        val user = object : org.konaframework.notifications.Notifiable {
            override fun routeNotificationFor(channel: String): String? = "user-1"
        }
        notifications.send(user, WelcomeNotice())
        assertTrue(notifications.assertSent(WelcomeNotice::class.java))
        assertFalse(notifications.assertSent(UnusedNotice::class.java))
        val logOnly = FakeNotification()
        logOnly.send(user, LogNotice())
        assertTrue(logOnly.assertSent(LogNotice::class.java))

        val events = FakeEvent()
        var heard = false
        events.dispatcher.listen(OrderPlaced::class.java) { heard = true }
        events.dispatch(OrderPlaced("o1"))
        assertTrue(heard)
        assertTrue(events.assertDispatched(OrderPlaced::class.java))
        assertEquals(1, events.events().size)
        events.clear()
        assertTrue(events.events().isEmpty())

        val cache = FakeCache()
        assertTrue(cache.put("k", "v", 60))
        assertEquals("v", cache.get("k"))
        assertTrue(cache.has("k"))
        assertTrue(cache.forever("f", 1))
        assertTrue(cache.forget("k"))
        assertTrue(cache.flush())

        val broadcast = FakeBroadcast()
        broadcast.broadcast(PingBroadcast())
        assertEquals(1, broadcast.messages().size)
        assertEquals("PingBroadcast", broadcast.messages().first().event)
        broadcast.clear()
        assertTrue(broadcast.messages().isEmpty())
    }

    data class OrderPlaced(val id: String)

    class PingBroadcast : org.konaframework.broadcasting.ShouldBroadcast {
        override fun broadcastOn() = listOf(org.konaframework.broadcasting.Channel.publicChannel("orders"))
    }

    class WelcomeNotice : org.konaframework.notifications.Notification() {
        override fun via(notifiable: org.konaframework.notifications.Notifiable) = listOf("database", "log")
        override fun toArray(notifiable: org.konaframework.notifications.Notifiable) = mapOf("hi" to true)
    }

    class LogNotice : org.konaframework.notifications.Notification() {
        override fun via(notifiable: org.konaframework.notifications.Notifiable) = listOf("log")
        override fun toArray(notifiable: org.konaframework.notifications.Notifiable) = mapOf("x" to 1)
    }

    class UnusedNotice : org.konaframework.notifications.Notification() {
        override fun via(notifiable: org.konaframework.notifications.Notifiable) = listOf("database")
    }
}
