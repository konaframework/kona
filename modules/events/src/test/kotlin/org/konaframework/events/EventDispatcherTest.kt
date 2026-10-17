package org.konaframework.events

import org.konaframework.core.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EventDispatcherTest {
    data class OrderShipped(val id: String)

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
}
