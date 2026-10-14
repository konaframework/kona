package dev.kona.broadcasting

import dev.kona.core.Application
import dev.kona.core.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class OrderShipped : ShouldBroadcast {
    override fun broadcastOn(): List<Channel> =
        listOf(Channel.publicChannel("orders"), Channel.privateOf("user.1"))

    override fun broadcastAs(): String = "order.shipped"

    override fun broadcastWith(): Map<String, Any?> = mapOf("id" to 7)
}

class DefaultsEvent : ShouldBroadcast {
    override fun broadcastOn(): List<Channel> = listOf(Channel.publicChannel("defaults"))
}

class EmptyChannels : ShouldBroadcast {
    override fun broadcastOn(): List<Channel> = emptyList()
}

class BroadcastingTest {
    @Test
    fun managersDriversAndFacade(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("broadcasting.default", "log")
        app.config.set("broadcasting.connections.log.driver", "log")
        app.config.set("broadcasting.connections.null.driver", "null")
        app.config.set("broadcasting.connections.none.driver", "none")
        app.register(BroadcastServiceProvider()).boot()

        val manager = app.resolve(BroadcastManager::class.java)
        assertEquals("log", manager.defaultConnection())
        assertTrue(manager.connection() is LogBroadcaster)
        assertTrue(manager.connection("null") is NullBroadcaster)
        assertTrue(manager.connection("none") is NullBroadcaster)
        manager.connection("null").broadcast(listOf(Channel.publicChannel("x")), "e", emptyMap())

        Broadcast.event(OrderShipped())
        val logs = manager.log().messages()
        assertEquals(1, logs.size)
        assertEquals(listOf("orders", "user.1"), logs[0].channels)
        assertEquals("order.shipped", logs[0].event)
        assertEquals(7, logs[0].payload["id"])
        assertTrue(!Channel.publicChannel("orders").isPrivate())
        assertTrue(Channel.privateOf("p").isPrivate())

        Broadcast.event(DefaultsEvent())
        assertTrue(manager.log().messages().any { it.event == "DefaultsEvent" })

        Broadcast.event(EmptyChannels())
        val before = manager.log().messages().size
        Broadcast.to(Channel.publicChannel("raw")).event("ping", mapOf("a" to 1))
        Broadcast.to(Channel.publicChannel("raw2")).event("pong")
        assertTrue(manager.log().messages().size > before)

        manager.log().clear()
        assertTrue(manager.log().messages().isEmpty())

        assertThrows(IllegalArgumentException::class.java) {
            app.config.set("broadcasting.connections.bad.driver", "redis")
            manager.connection("bad")
        }
    }

    @Test
    fun managerRequired() {
        val field = Broadcast::class.java.getDeclaredField("manager")
        field.isAccessible = true
        field.set(null, null)
        assertThrows(IllegalStateException::class.java) { Broadcast.manager() }
        Broadcast.setManager(BroadcastManager(Config()))
        assertTrue(Broadcast.manager() != null)
    }
}
