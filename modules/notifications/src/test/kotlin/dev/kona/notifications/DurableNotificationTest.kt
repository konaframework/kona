package dev.kona.notifications

import dev.kona.core.Application
import dev.kona.extract.ConnectionConfig
import dev.kona.extract.Database
import dev.kona.extract.ExtractServiceProvider
import dev.kona.queue.Bus
import dev.kona.queue.QueueServiceProvider
import dev.kona.queue.ShouldQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class DurableNotificationTest {
    class User(val email: String) : Notifiable {
        override fun routeNotificationFor(channel: String): String? =
            when (channel) {
                "mail", "log", "database" -> email
                else -> null
            }
    }

    class Alert : Notification() {
        override fun via(notifiable: Notifiable) = listOf("database")

        override fun toArray(notifiable: Notifiable) = mapOf("msg" to "hi")
    }

    class QueuedAlert : Notification(), ShouldQueue {
        override fun via(notifiable: Notifiable) = listOf("database")

        override fun toArray(notifiable: Notifiable) = mapOf("queued" to true)
    }

    @Test
    fun extractStoreRoundTripAndReadState(@TempDir base: Path) {
        val db = Database().addConnection(
            "default",
            ConnectionConfig(
                "jdbc:h2:mem:notif-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            ),
        )
        val store = DatabaseNotificationStore(db, "notifications")
        val record = store.add(
            NotificationRecord.create("u@x.com", Alert::class.java.name, mapOf("a" to 1)),
        )
        assertEquals(1, store.all().size)
        assertEquals(1, store.unread("u@x.com").size)
        assertNull(store.find("missing"))
        assertNotNull(store.find(record.id))

        val read = store.markAsRead(record.id)
        assertNotNull(read!!.readAt)
        assertTrue(store.unread("u@x.com").isEmpty())
        assertEquals(read, store.markAsRead(record.id))

        store.add(NotificationRecord.create("u@x.com", Alert::class.java.name, mapOf("b" to 2)))
        store.add(NotificationRecord.create("other@x.com", Alert::class.java.name, mapOf("c" to 3)))
        assertEquals(1, store.markAllAsRead("u@x.com"))
        assertEquals(0, store.markAllAsRead("u@x.com"))
        assertEquals(1, store.unread("other@x.com").size)

        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun providerWiresExtractStoreWhenDriverDatabase(@TempDir base: Path) {
        val app = Application.builder(base)
            .configure {
                it.set("notifications.database.driver", "database")
                it.set("database.default", "h2")
                it.set(
                    "database.connections.h2.url",
                    "jdbc:h2:mem:notif-provider-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                )
            }
            .withProviders(ExtractServiceProvider(), NotificationServiceProvider())
            .boot()

        val channel = app.resolve(DatabaseChannel::class.java)
        assertTrue(channel.store() is DatabaseNotificationStore)
        app.resolve(NotificationSender::class.java).send(User("p@x.com"), Alert())
        assertEquals(1, channel.unread("p@x.com").size)
        assertNotNull(channel.markAsRead(channel.unread("p@x.com").first().id))
        assertEquals(0, channel.unread("p@x.com").size)
    }

    @Test
    fun memoryStoreMarkReadAndFileReload(@TempDir base: Path) {
        val file = base.resolve("notes.json")
        val store = MemoryNotificationStore(file)
        val id = store.add(NotificationRecord.create("a@b.c", "T", mapOf("x" to 1))).id
        store.add(NotificationRecord.create("other@x.com", "T", mapOf("z" to 1)))
        assertEquals(1, store.markAllAsRead("a@b.c"))
        // Already-read + other notifiable rows exercise markAllAsRead filter branches.
        assertEquals(0, store.markAllAsRead("a@b.c"))
        assertNull(store.markAsRead("nope"))
        store.clear()

        val again = MemoryNotificationStore(file)
        assertTrue(again.all().isEmpty())
        again.add(NotificationRecord.create("a@b.c", "T", mapOf("y" to 2)))
        val reloaded = MemoryNotificationStore(file)
        assertEquals(1, reloaded.forNotifiable("a@b.c").size)
        assertTrue(NotificationRecord.fromMap(mapOf("id" to "1", "type" to "t")).isUnread())
        assertFalse(
            NotificationRecord.fromMap(
                mapOf("id" to "1", "type" to "t", "read_at" to "2020-01-01T00:00:00Z"),
            ).isUnread(),
        )
        assertNotNull(id)
    }

    @Test
    fun queuedNotificationUsesBus(@TempDir base: Path) {
        val app = Application.builder(base)
            .configure { it.set("queue.default", "sync") }
            .withProviders(QueueServiceProvider(), NotificationServiceProvider())
            .boot()
        assertNotNull(app.resolve(Bus::class.java))
        val sender = app.resolve(NotificationSender::class.java)
        val db = app.resolve(DatabaseChannel::class.java)
        sender.send(User("q@x.com"), QueuedAlert())
        // sync queue runs immediately
        assertEquals(1, db.records().size)
        assertEquals(true, db.records().first().data["queued"])

        // Without Bus, ShouldQueue still sends synchronously.
        val syncOnly = NotificationSender()
            .extend("database", DatabaseChannel())
        syncOnly.send(User("s@x.com"), QueuedAlert())
        assertEquals(1, syncOnly.channel("database").let { it as DatabaseChannel }.records().size)
    }

    @Test
    fun providerFallsBackWithoutDatabaseBinding(@TempDir base: Path) {
        val app = Application.builder(base)
            .configure { it.set("notifications.database.driver", "extract") }
            .withProvider(NotificationServiceProvider())
            .boot()
        assertTrue(app.resolve(NotificationStore::class.java) is MemoryNotificationStore)

        val fileDriver = Application.builder(base.resolve("file"))
            .configure {
                it.set("notifications.database.driver", "file")
                it.set("notifications.database.path", "storage/n.json")
            }
            .withProvider(NotificationServiceProvider())
            .boot()
        assertTrue(fileDriver.resolve(NotificationStore::class.java) is MemoryNotificationStore)
    }

    @Test
    fun recordParsingAndChannelHelpers() {
        val instant = java.time.Instant.parse("2020-01-01T00:00:00Z")
        val withInstant = NotificationRecord.fromMap(
            mapOf(
                "id" to "1",
                "type" to "T",
                "notifiable" to "n",
                "data" to mapOf("k" to "v"),
                "read_at" to instant,
                "created_at" to instant,
            ),
        )
        assertEquals(instant, withInstant.readAt)
        assertNull(NotificationRecord.fromMap(mapOf("read_at" to "null")).readAt)
        assertNull(NotificationRecord.fromMap(mapOf("read_at" to "   ")).readAt)
        assertNull(NotificationRecord.fromMap(mapOf("read_at" to "not-an-instant")).readAt)
        assertEquals("", NotificationRecord.fromMap(emptyMap()).type)

        val channel = DatabaseChannel()
        channel.send(User("h@x.com"), Alert())
        channel.send(User("h@x.com"), Alert())
        assertEquals(2, channel.forNotifiable("h@x.com").size)
        assertEquals(2, channel.markAllAsRead("h@x.com"))
        assertEquals(0, channel.unread("h@x.com").size)
        assertNotNull(channel.store().find(channel.records().first().id))
    }

    @Test
    fun extractStoreHandlesBadJsonAndNullReadAt(@TempDir base: Path) {
        val db = Database().addConnection(
            "default",
            ConnectionConfig(
                "jdbc:h2:mem:notif-bad-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            ),
        )
        val store = DatabaseNotificationStore(db, "notifications")
        db.table("notifications").insert(
            mapOf(
                "id" to "bad",
                "type" to "T",
                "notifiable" to "n",
                "data" to "{not-json",
                "read_at" to null,
                "created_at" to "2020-01-01T00:00:00Z",
            ),
        )
        val row = store.find("bad")
        assertNotNull(row)
        assertTrue(row!!.data.isEmpty())
        assertTrue(row.isUnread())

        val withRead = store.add(
            NotificationRecord.create("n", "T", emptyMap()).copy(readAt = java.time.Instant.now()),
        )
        assertNotNull(withRead.readAt)
        assertNull(store.markAsRead("missing-id"))

        db.table("notifications").insert(
            mapOf(
                "id" to "nulldata",
                "type" to "T",
                "notifiable" to "n",
                "data" to null,
                "read_at" to null,
                "created_at" to "2020-01-01T00:00:00Z",
            ),
        )
        assertTrue(store.find("nulldata")!!.data.isEmpty())

        // Default table name constructor + default MemoryNotificationStore()
        DatabaseNotificationStore(db)
        assertEquals(0, MemoryNotificationStore().markAllAsRead("nobody"))
        assertEquals(
            emptyMap<String, Any?>(),
            NotificationRecord.fromMap(mapOf("data" to "not-a-map")).data,
        )
        assertTrue(NotificationRecord.fromMap(mapOf("id" to null)).id.isNotBlank())
        assertEquals(
            "fixed",
            NotificationRecord.fromMap(mapOf("id" to "fixed")).id,
        )
        val now = java.time.Instant.now()
        val explicit = NotificationRecord("e", "T", mapOf("a" to 1), "n", null, now)
        assertEquals(now, explicit.createdAt)
        assertNull(explicit.readAt)
        assertTrue(NotificationRecord.create("n", "T", emptyMap()).id.isNotBlank())
        assertTrue(NotificationRecord.fromMap(mapOf("id" to "")).id.isNotBlank())
        assertTrue(NotificationRecord.fromMap(mapOf("id" to "   ")).id.isNotBlank())
    }
}
