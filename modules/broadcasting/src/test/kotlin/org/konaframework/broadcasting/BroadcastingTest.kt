package org.konaframework.broadcasting

import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.DB
import org.konaframework.cadence.Database
import org.konaframework.core.Application
import org.konaframework.core.Config
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Request
import org.konaframework.http.Router
import org.konaframework.redis.InMemoryRedisConnection
import org.konaframework.redis.RedisManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class OrderShipped : ShouldBroadcast {
    override fun broadcastOn(): List<Channel> =
        listOf(Channel.publicChannel("orders"), Channel.privateOf("user.1"))

    override fun broadcastAs(): String = "order.shipped"

    override fun broadcastWith(): Map<String, Any?> = mapOf("id" to 7)
}

class DeferredShipped : ShouldBroadcast, ShouldBroadcastAfterCommit {
    override fun broadcastOn(): List<Channel> = listOf(Channel.publicChannel("orders"))

    override fun broadcastAs(): String = "order.deferred"

    override fun broadcastWith(): Map<String, Any?> = mapOf("id" to 9)
}

class DefaultsEvent : ShouldBroadcast {
    override fun broadcastOn(): List<Channel> = listOf(Channel.publicChannel("defaults"))
}

class EmptyChannels : ShouldBroadcast {
    override fun broadcastOn(): List<Channel> = emptyList()
}

class BroadcastingTest {
    @AfterEach
    fun clearDb() {
        Database.clearGlobal()
        Broadcast.clearSocketId()
    }

    @Test
    fun defersShouldBroadcastAfterCommitUntilTransactionCommits(@TempDir base: Path) {
        val n = dbCounter.incrementAndGet()
        Database()
            .addConnection("default", ConnectionConfig("jdbc:h2:mem:bcast$n;DB_CLOSE_DELAY=-1", "sa", ""))
            .setAsGlobal()
        val app = Application(base)
        app.config.set("broadcasting.default", "log")
        app.config.set("broadcasting.connections.log.driver", "log")
        app.register(BroadcastServiceProvider()).boot()
        val manager = app.resolve(BroadcastManager::class.java)
        manager.log().clear()

        DB.transaction { _ ->
            manager.broadcast(DeferredShipped())
            assertEquals(0, manager.log().messages().size)
            1
        }
        assertEquals(1, manager.log().messages().size)
        assertEquals("order.deferred", manager.log().messages()[0].event)

        manager.log().clear()
        manager.broadcast(DeferredShipped())
        assertEquals(1, manager.log().messages().size)

        Database.clearGlobal()
        manager.log().clear()
        manager.broadcast(DeferredShipped())
        assertEquals(1, manager.log().messages().size)

        class DeferredEmpty : ShouldBroadcast, ShouldBroadcastAfterCommit {
            override fun broadcastOn(): List<Channel> = emptyList()
        }
        val before = manager.log().messages().size
        manager.broadcast(DeferredEmpty())
        assertEquals(before, manager.log().messages().size)
    }

    @Test
    fun managersDriversAndFacade(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("broadcasting.default", "log")
        app.config.set("broadcasting.connections.log.driver", "log")
        app.config.set("broadcasting.connections.null.driver", "null")
        app.config.set("broadcasting.connections.none.driver", "none")
        app.config.set("broadcasting.connections.websocket.driver", "websocket")
        app.config.set("broadcasting.connections.ws.driver", "ws")
        app.register(HttpServiceProvider())
        app.register(BroadcastServiceProvider()).boot()

        val manager = app.resolve(BroadcastManager::class.java)
        assertEquals("log", manager.defaultConnection())
        assertTrue(manager.connection() is LogBroadcaster)
        assertTrue(manager.connection("null") is NullBroadcaster)
        assertTrue(manager.connection("none") is NullBroadcaster)
        assertTrue(manager.connection("websocket") is WebsocketBroadcaster)
        assertTrue(manager.connection("ws") === manager.websocket())
        manager.connection("null").broadcast(listOf(Channel.publicChannel("x")), "e", emptyMap())

        Broadcast.event(OrderShipped())
        val logs = manager.log().messages()
        assertEquals(1, logs.size)
        assertEquals(listOf("orders", "user.1"), logs[0].channels)
        assertEquals("order.shipped", logs[0].event)
        assertEquals(7, logs[0].payload["id"])
        assertTrue(!Channel.publicChannel("orders").isPrivate())
        assertTrue(Channel.privateOf("p").isPrivate())
        assertTrue(Channel.presence("room").isPresence())
        assertEquals("presence-room", Channel.presence("room").pusherName())
        assertEquals("private-p", Channel.privateOf("p").pusherName())

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

        assertTrue(app.resolve(Router::class.java).all().any { it.uri.contains("broadcasting/auth") })
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

    @Test
    fun websocketSubscribersReceivePayloads(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("broadcasting.default", "websocket")
        app.config.set("broadcasting.connections.websocket.driver", "websocket")
        app.register(BroadcastServiceProvider()).boot()

        val ws = Broadcast.manager().websocket()
        val received = mutableListOf<Triple<List<String>, String, Map<String, Any?>>>()
        val connection = WebsocketBroadcaster.Connection { channels, event, payload ->
            received += Triple(channels, event, payload)
        }
        val other = WebsocketBroadcaster.Connection { _, _, _ -> }
        val sub = ws.subscribe("orders", connection, "sock.1")
        ws.subscribe("orders", other, "sock.2")
        ws.subscribe("user.1", connection, "sock.1")
        assertEquals(2, ws.listenerCount("orders"))
        assertEquals(0, ws.listenerCount("missing"))

        Broadcast.event(OrderShipped())
        assertEquals(1, received.size)
        assertEquals(listOf("orders", "user.1"), received[0].first)
        assertEquals("order.shipped", received[0].second)
        assertEquals(7, received[0].third["id"])
        assertEquals(1, ws.messages().size)

        received.clear()
        Broadcast.socket("sock.1")
        Broadcast.to(Channel.publicChannel("orders")).toOthers().event("secret", mapOf("x" to 1))
        assertEquals(0, received.size)
        assertEquals(2, ws.messages().size)

        ws.broadcast(listOf(Channel.publicChannel("ghost")), "noop", emptyMap())
        assertEquals(3, ws.messages().size)

        ws.unsubscribe("orders", other)
        assertEquals(1, ws.listenerCount("orders"))
        sub.close()
        assertEquals(0, ws.listenerCount("orders"))
        ws.unsubscribe("user.1", connection)
        ws.unsubscribe("nobody", connection)
        assertEquals(0, ws.listenerCount("user.1"))
        ws.clear()
        assertTrue(ws.messages().isEmpty())
    }

    @Test
    fun channelAuthorizationAndAuthEndpoint(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("broadcasting.default", "websocket")
        app.config.set("broadcasting.connections.websocket.driver", "websocket")
        app.config.set("broadcasting.connections.websocket.key", "app-key")
        app.config.set("broadcasting.connections.websocket.secret", "app-secret")
        app.register(HttpServiceProvider())
        app.register(BroadcastServiceProvider()).boot()

        Broadcast.channel("orders.{orderId}") { user, params ->
            user == "alice" && params == listOf("42")
        }
        Broadcast.channel("chat.{room}") { _, _ ->
            mapOf("user_id" to "9", "user_info" to mapOf("name" to "Ada"))
        }

        val auth =
            BroadcastAuth(
                channels = Broadcast.channels(),
                key = "app-key",
                secret = "app-secret",
                userResolver = { "alice" },
            )
        val ok =
            auth.handle(
                Request.post(
                    "/broadcasting/auth",
                    "socket_id=1.2&channel_name=private-orders.42",
                ).let {
                    Request(
                        "POST",
                        "/broadcasting/auth",
                        headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                        body = "socket_id=1.2&channel_name=private-orders.42",
                    )
                },
            )
        assertEquals(200, ok.status)
        assertTrue(ok.body.contains("auth"))

        val denied =
            auth.handle(
                Request(
                    "POST",
                    "/broadcasting/auth",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    body = "socket_id=1.2&channel_name=private-orders.99",
                ),
            )
        assertEquals(403, denied.status)

        val presence =
            auth.handle(
                Request(
                    "POST",
                    "/broadcasting/auth",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    body = "socket_id=1.2&channel_name=presence-chat.lobby",
                ),
            )
        assertEquals(200, presence.status)
        assertTrue(presence.body.contains("channel_data"))

        val missing =
            auth.handle(
                Request(
                    "POST",
                    "/broadcasting/auth",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    body = "socket_id=&channel_name=",
                ),
            )
        assertEquals(422, missing.status)

        assertTrue(PusherSigner.verify("app-key", "app-secret", "1.2", "private-orders.42", null).not())
        val signed = PusherSigner.authString("app-key", "app-secret", "1.2", "private-orders.42")
        assertTrue(PusherSigner.verify("app-key", "app-secret", "1.2", "private-orders.42", signed))
    }

    @Test
    fun redisScaleOutAndHttpDrivers(@TempDir base: Path) {
        val redis = InMemoryRedisConnection()
        val app = Application(base)
        app.config.set("broadcasting.default", "reverb")
        app.config.set("broadcasting.connections.reverb.driver", "reverb")
        app.config.set("broadcasting.connections.reverb.key", "k")
        app.config.set("broadcasting.connections.reverb.secret", "s")
        app.config.set("broadcasting.reverb.scaling", "redis")
        app.config.set("broadcasting.connections.pusher.driver", "pusher")
        app.config.set("broadcasting.connections.pusher.app_id", "1")
        app.config.set("broadcasting.connections.pusher.key", "k")
        app.config.set("broadcasting.connections.pusher.secret", "s")
        app.config.set("broadcasting.connections.pusher.port", 443)
        app.config.set("broadcasting.connections.ably.driver", "ably")
        app.config.set("broadcasting.connections.ably.key", "ably:key")
        app.container.singleton(RedisManager::class.java) {
            RedisManager(app.config).also {
                // force memory client
            }
        }
        app.config.set("redis.client", "memory")
        app.register(BroadcastServiceProvider()).boot()

        val manager = app.resolve(BroadcastManager::class.java)
        val reverb = manager.reverb()
        reverb.startScaleOutListener()
        reverb.startScaleOutListener()

        val hub = manager.hub()
        val received = mutableListOf<String>()
        val client =
            object : org.konaframework.broadcasting.reverb.ReverbHub.Client {
                override fun send(message: String) {
                    received += message
                }

                override fun socketId(): String = "9.9"
            }
        hub.register(client)
        hub.subscribe("9.9", "orders", null, null)
        reverb.broadcast(listOf(Channel.publicChannel("orders")), "ping", mapOf("ok" to true))
        assertTrue(received.any { it.contains("ping") })

        val pusherRequests = mutableListOf<java.net.http.HttpRequest>()
        val pusher =
            PusherBroadcaster("1", "k", "s", host = "example.test", scheme = "http", port = 80) {
                pusherRequests += it
            }
        pusher.broadcast(listOf(Channel.publicChannel("a")), "e", mapOf("x" to 1), "sock")
        assertEquals(1, pusher.messages().size)
        assertEquals(1, pusherRequests.size)
        pusher.clear()
        assertTrue(pusher.messages().isEmpty())

        val ablyRequests = mutableListOf<java.net.http.HttpRequest>()
        val ably = AblyBroadcaster("key") { ablyRequests += it }
        ably.broadcast(listOf(Channel.publicChannel("b")), "e2", mapOf("y" to 2))
        assertEquals(1, ably.messages().size)
        assertEquals(1, ablyRequests.size)
        ably.clear()

        // Resolve optional drivers through the manager (live senders are not invoked here).
        assertTrue(manager.connection("pusher") is PusherBroadcaster)
        assertTrue(manager.connection("ably") is AblyBroadcaster)

        reverb.stopScaleOutListener()
        redis.close()
    }

    companion object {
        private val dbCounter = AtomicInteger()
    }
}
