package io.konaframework.queue

import io.konaframework.core.Config
import io.konaframework.cadence.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseFailedJobProviderTest {
    private fun database(): Database =
        Database.fromConfig(
            Config().also {
                it.set("database.default", "h2")
                it.set("database.connections.h2.url", "jdbc:h2:mem:failed_${System.nanoTime()};DB_CLOSE_DELAY=-1")
                it.set("database.connections.h2.username", "sa")
                it.set("database.connections.h2.password", "")
            },
        )

    private fun payload(id: String = "job-1"): QueuePayload =
        QueuePayload(
            id = id,
            displayName = "Demo",
            tries = 3,
            backoffSeconds = 0,
            attempts = 1,
            jobKey = "demo",
            jobBody = """{"token":"x"}""",
        )

    @Test
    fun persistReloadForgetAndFlush() {
        val db = database()
        val provider = DatabaseFailedJobProvider(db.connection())
        provider.log("array", "default", payload("a"), RuntimeException("boom"))
        provider.log("array", "default", payload("b"), RuntimeException())
        assertEquals(2, provider.all().size)

        val reloaded = DatabaseFailedJobProvider(db.connection())
        assertEquals(2, reloaded.all().size)
        assertEquals("boom", reloaded.find("a")!!.exception)
        assertTrue(reloaded.forget("a"))
        assertNull(reloaded.find("a"))
        assertEquals(1, DatabaseFailedJobProvider(db.connection()).all().size)

        val again = DatabaseFailedJobProvider(db.connection())
        again.flush()
        assertTrue(again.all().isEmpty())
        assertTrue(DatabaseFailedJobProvider(db.connection()).all().isEmpty())
    }

    @Test
    fun queueManagerWiresDatabaseFailedDriver() {
        val db = database()
        val mgr = QueueManager(
            Config().also {
                it.set("queue.default", "array")
                it.set("queue.failed.driver", "database")
                it.set("queue.failed.table", "failed_jobs")
            },
            null,
            db,
        )
        assertTrue(mgr.failed is DatabaseFailedJobProvider)
        mgr.failed.log("array", "default", payload("z"), RuntimeException("z"))
        assertEquals(1, DatabaseFailedJobProvider(db.connection()).all().size)
    }

    @Test
    fun databaseFailedRequiresDatabase() {
        assertThrows(IllegalStateException::class.java) {
            QueueManager(
                Config().also {
                    it.set("queue.failed.driver", "database")
                },
            )
        }
    }

    @Test
    fun skipsCorruptRowsOnLoad() {
        val db = database()
        val conn = db.connection()
        DatabaseFailedJobProvider(conn).log("c", "q", payload("ok"), RuntimeException("e"))
        conn.insert(
            "INSERT INTO failed_jobs (id, connection, queue, payload, exception, failed_at) VALUES (?, ?, ?, ?, ?, ?)",
            listOf("bad", "c", "q", "{not-json", "x", 1L),
        )
        val loaded = DatabaseFailedJobProvider(conn)
        assertEquals(1, loaded.all().size)
        assertEquals("ok", loaded.all().single().id)

        // Case-insensitive column lookup + missing-column error path (caught by load).
        conn.statement("ALTER TABLE failed_jobs ADD COLUMN extra VARCHAR(10)")
        conn.insert(
            "INSERT INTO failed_jobs (id, connection, queue, payload, exception, failed_at, extra) VALUES (?, ?, ?, ?, ?, ?, ?)",
            listOf("case", "C", "Q", JobRegistry.encode(payload("case")), "e", 2L, "x"),
        )
        assertTrue(DatabaseFailedJobProvider(conn).all().any { it.id == "case" })

        val provider = DatabaseFailedJobProvider(conn)
        val requireCell = DatabaseFailedJobProvider::class.java.getDeclaredMethod(
            "requireCell",
            Map::class.java,
            String::class.java,
        )
        requireCell.isAccessible = true
        val ex = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            requireCell.invoke(provider, emptyMap<String, Any?>(), "id")
        }
        assertTrue(ex.cause is IllegalStateException)
        // null value in matching key falls through to missing.
        val withNull = mapOf("id" to null)
        val ex2 = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            requireCell.invoke(provider, withNull, "id")
        }
        assertTrue(ex2.cause is IllegalStateException)
        assertEquals("ok", requireCell.invoke(provider, mapOf("ID" to "ok"), "id"))
    }
}
