package org.konaframework.events

import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.DB
import org.konaframework.cadence.Database
import org.konaframework.core.Application
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class EventDispatcherTest {
    data class OrderShipped(val id: String)

    data class DeferredOrder(val id: String) : ShouldDispatchAfterCommit

    @AfterEach
    fun clearDb() {
        Database.clearGlobal()
    }

    private fun openDb(): Database {
        val n = counter.incrementAndGet()
        return Database()
            .addConnection("default", ConnectionConfig("jdbc:h2:mem:events$n;DB_CLOSE_DELAY=-1", "sa", ""))
            .setAsGlobal()
    }

    companion object {
        private val counter = AtomicInteger()
    }

    @Test
    fun listensAndDispatchesInOrder() {
        val bus = EventDispatcher()
        val seen = mutableListOf<String>()
        bus.listen(OrderShipped::class.java) { seen += "a:${it.id}" }
        bus.listen(OrderShipped::class.java, EventListener { seen += "b:${it.id}" })
        bus.listen<OrderShipped>(EventListener { seen += "c:${it.id}" })
        assertTrue(bus.hasListeners(OrderShipped::class.java))
        assertEquals(3, bus.listenerCount(OrderShipped::class.java))
        bus.dispatch(OrderShipped("1"))
        assertEquals(listOf("a:1", "b:1", "c:1"), seen)
        bus.dispatch("ignored")
        assertEquals(listOf("a:1", "b:1", "c:1"), seen)
        assertEquals(3, bus.listenerCount(OrderShipped::class.java))
    }

    @Test
    fun forgetAndFlush(@TempDir base: Path) {
        val bus = EventDispatcher()
        bus.listen(OrderShipped::class.java) { }
        bus.forget(OrderShipped::class.java)
        assertFalse(bus.hasListeners(OrderShipped::class.java))
        assertEquals(0, bus.listenerCount(OrderShipped::class.java))
        bus.listen(OrderShipped::class.java) { }
        bus.flush()
        assertFalse(bus.hasListeners(OrderShipped::class.java))

        val app = Application(base)
        app.register(EventServiceProvider()).boot()
        assertTrue(app.container.bound(EventDispatcher::class.java))
        app.resolve(EventDispatcher::class.java).dispatch(OrderShipped("x"))
    }

    @Test
    fun defersShouldDispatchAfterCommitUntilTransactionCommits() {
        openDb()
        val bus = EventDispatcher()
        val seen = mutableListOf<String>()
        bus.listen(DeferredOrder::class.java) { seen += it.id }

        DB.transaction { _ ->
            bus.dispatch(DeferredOrder("deferred"))
            assertTrue(seen.isEmpty())
            1
        }
        assertEquals(listOf("deferred"), seen)

        bus.dispatch(DeferredOrder("immediate"))
        assertEquals(listOf("deferred", "immediate"), seen)
    }

    @Test
    fun afterCommitMarkerWithoutGlobalDatabaseDispatchesImmediately() {
        Database.clearGlobal()
        val bus = EventDispatcher()
        val seen = mutableListOf<String>()
        bus.listen(DeferredOrder::class.java) { seen += it.id }
        bus.dispatch(DeferredOrder("no-db"))
        assertEquals(listOf("no-db"), seen)
    }
}
