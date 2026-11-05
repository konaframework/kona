package org.konaframework.broadcasting

import org.konaframework.auth.Authenticatable
import org.konaframework.broadcasting.reverb.LocalScaleOutBus
import org.konaframework.broadcasting.reverb.PusherProtocol
import org.konaframework.broadcasting.reverb.RedisScaleOutBus
import org.konaframework.broadcasting.reverb.ReverbBroadcaster
import org.konaframework.broadcasting.reverb.ReverbHub
import org.konaframework.broadcasting.reverb.ScaleOutMessage
import org.konaframework.core.Application
import org.konaframework.core.Config
import org.konaframework.http.Request
import org.konaframework.redis.InMemoryRedisConnection
import org.konaframework.redis.RedisManager
import org.konaframework.support.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class BroadcastCoverageTest {
    @Test
    fun broadcastAuthPresenceAndUserFallbacks() {
        val channels = ChannelManager()
        channels.channel("room") { _, _ -> true }
        channels.channel("team.{id}") { user, _ -> user as Authenticatable }

        val user =
            object : Authenticatable {
                override fun getAuthIdentifier(): Any = 55
                override fun getAuthPassword(): String = "x"
            }

        val auth =
            BroadcastAuth(
                channels = channels,
                key = "k",
                secret = "s",
                userResolver = { user },
            )

        val presenceTrue =
            auth.handle(
                form("socket_id=1.1&channel_name=presence-room"),
            )
        assertEquals(200, presenceTrue.status)
        assertTrue(presenceTrue.body.contains("channel_data"))

        val presenceUser =
            auth.handle(
                form("socket_id=1.1&channel_name=presence-team.7"),
            )
        assertEquals(200, presenceUser.status)

        val publicOk =
            auth.handle(
                form("socket_id=1.1&channel_name=orders"),
            )
        assertEquals(200, publicOk.status)

        val authNullUser =
            BroadcastAuth(channels, "k", "s", userResolver = { null })
        val forbidden =
            authNullUser.handle(
                form("socket_id=1.1&channel_name=private-missing"),
            )
        assertEquals(403, forbidden.status)

        val mapResult =
            BroadcastAuth(
                ChannelManager().also {
                    it.channel("x") { _, _ -> mapOf("user_id" to "z") }
                },
                "k",
                "s",
                userResolver = { "anon" },
            ).handle(form("socket_id=1.1&channel_name=presence-x"))
        assertTrue(mapResult.body.contains("user_id"))

        val stringResult =
            BroadcastAuth(
                ChannelManager().also { it.channel("y") { _, _ -> 99 } },
                "k",
                "s",
            ).handle(form("socket_id=1.1&channel_name=presence-y"))
        assertTrue(stringResult.body.contains("99"))
    }

    @Test
    fun channelManagerPatternsAndFacadeHelpers() {
        val manager = ChannelManager()
        assertThrows(IllegalArgumentException::class.java) {
            manager.channel("bad.{id") { _, _ -> true }
        }
        manager.channel("orders.{id}") { _, params -> params[0] == "1" }
        assertTrue(manager.has("orders.{id}"))
        assertEquals(true, manager.authorize("orders.1", null))
        assertEquals(false, manager.authorize("private-unknown", null))
        assertEquals(true, manager.authorize("public-open", null))
        assertEquals("orders.1", ChannelManager.stripPrefix("private-orders.1"))
        assertEquals("orders.1", ChannelManager.stripPrefix("presence-orders.1"))
        assertEquals("orders.1", ChannelManager.stripPrefix("orders.1"))
        manager.clear()
        assertFalse(manager.has("orders.{id}"))

        Broadcast.setChannels(manager)
        Broadcast.channel("a.{b}") { _, _ -> true }
        Broadcast.toOthers()
        Broadcast.socket("1.2")
        assertEquals("1.2", Broadcast.currentSocketId())
        Broadcast.clearSocketId()
        assertEquals(null, Broadcast.currentSocketId())
    }

    @Test
    fun managerScaleOutAndDriverBranches(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("broadcasting.default", "websocket")
        app.config.set("broadcasting.connections.websocket.driver", "websocket")
        app.config.set("broadcasting.connections.websocket.key", "k")
        app.config.set("broadcasting.connections.websocket.secret", "s")
        app.config.set("broadcasting.reverb.scaling", "true")
        app.config.set("redis.client", "memory")
        app.container.singleton(RedisManager::class.java) { RedisManager(app.config) }
        app.register(BroadcastServiceProvider()).boot()
        val manager = app.resolve(BroadcastManager::class.java)
        val reverb = manager.reverb()
        reverb.stopScaleOutListener()
        assertTrue(manager.hub().connectionCount() >= 0)

        val bare = BroadcastManager(Config())
        bare.hub()
        assertThrows(IllegalArgumentException::class.java) {
            bare.connection("nope")
        }

        // Broadcaster default 4-arg delegates to 3-arg
        val calls = mutableListOf<String>()
        val simple =
            Broadcaster { channels, event, _ ->
                calls += event + channels.joinToString { it.name }
            }
        simple.broadcast(listOf(Channel.publicChannel("c")), "e", emptyMap(), "sock")
        assertEquals(1, calls.size)

        NullBroadcaster().broadcast(listOf(Channel.publicChannel("c")), "e", emptyMap(), null)
        LogBroadcaster().broadcast(listOf(Channel.publicChannel("c")), "e", emptyMap())
    }

    @Test
    fun pusherAblyAndProtocolBranches() {
        val requests = mutableListOf<java.net.http.HttpRequest>()
        PusherBroadcaster("1", "k", "s", port = null, sender = { requests += it })
            .broadcast(listOf(Channel.publicChannel("a")), "e", emptyMap())
        assertEquals(1, requests.size)
        assertFalse(requests[0].uri().toString().contains(":null"))

        AblyBroadcaster("key:secret") { requests += it }
            .broadcast(listOf(Channel.publicChannel("space name")), "e", emptyMap(), null)

        assertTrue(PusherProtocol.parse("{").isEmpty())
        assertTrue(PusherProtocol.dataObject(mapOf("data" to mapOf("a" to 1)))["a"] == 1)
        assertTrue(PusherProtocol.dataObject(mapOf("data" to "{")).isEmpty())
        assertTrue(PusherProtocol.dataObject(emptyMap()).isEmpty())
        assertTrue(PusherProtocol.event("c", "e", null).contains("data"))
        assertTrue(PusherProtocol.memberAdded("c", "1").contains("member_added"))
        assertTrue(PusherProtocol.memberRemoved("c", "1").contains("member_removed"))
    }

    @Test
    fun redisScaleOutMalformedAndHubEdges() {
        val redis = InMemoryRedisConnection()
        val bus = RedisScaleOutBus(redis)
        val seen = mutableListOf<ScaleOutMessage>()
        val sub = bus.subscribe { seen += it }
        redis.publish(RedisScaleOutBus.DEFAULT_CHANNEL, "not-json")
        redis.publish(RedisScaleOutBus.DEFAULT_CHANNEL, Json.stringify(mapOf("event" to "x")))
        redis.publish(
            RedisScaleOutBus.DEFAULT_CHANNEL,
            Json.stringify(mapOf("channels" to listOf("a"), "event" to "e", "payload" to mapOf("z" to 1))),
        )
        assertEquals(1, seen.size)
        sub.close()
        sub.close()

        val hub = ReverbHub("k", "s")
        assertTrue(hub.handleClientMessage("missing", "{}")!!.contains("Event required"))
        assertTrue(
            hub.handleClientMessage("missing", """{"event":"pusher:subscribe","data":{}}""")!!
                .contains("Channel required"),
        )
        assertTrue(
            hub.handleClientMessage("missing", """{"event":"pusher:unsubscribe","data":{}}""")!!
                .contains("Channel required"),
        )
        assertTrue(
            hub.handleClientMessage("missing", """{"event":"wat"}""")!!.contains("Unknown event"),
        )
        hub.subscribe("1.1", "public", null, null)
        hub.unsubscribe("1.1", "public")
        hub.unsubscribe("1.1", "missing")
        hub.publish("nobody", "e", emptyMap())
        hub.forEachClient { }
        assertEquals(0, hub.listenerCount("public"))

        val inbox = mutableListOf<String>()
        val client =
            object : ReverbHub.Client {
                override fun send(message: String) {
                    inbox += message
                }

                override fun socketId(): String = "2.2"
            }
        hub.register(client)
        val channelData = """{"user_id":"u"}"""
        val auth = PusherSigner.authString("k", "s", "2.2", "presence-r", channelData)
        hub.subscribe("2.2", "presence-r", auth, channelData)
        hub.subscribe("2.2", "presence-r", auth, "{")
        hub.unsubscribe("2.2", "presence-r")
        hub.unregister("2.2")

        val local = LocalScaleOutBus()
        val broadcaster = ReverbBroadcaster(hub, local, "oid")
        broadcaster.startScaleOutListener()
        broadcaster.broadcast(listOf(Channel.privateOf("u.1")), "e", mapOf("a" to 1), "x")
        broadcaster.stopScaleOutListener()
        redis.close()
    }

    @Test
    fun pendingBroadcastWithoutToOthers() {
        Broadcast.setManager(BroadcastManager(Config().also {
            it.set("broadcasting.default", "log")
            it.set("broadcasting.connections.log.driver", "log")
        }))
        Broadcast.to(Channel.publicChannel("x")).event("ping")
        assertEquals(1, Broadcast.manager().log().messages().size)
        Broadcast.manager().log().clear()
        Broadcast.socket("s")
        Broadcast.to(Channel.publicChannel("x")).toOthers().event("ping2")
        assertEquals("s", Broadcast.manager().log().messages()[0].excludeSocketId)
        Broadcast.clearSocketId()
    }

    private fun form(body: String): Request =
        Request(
            "POST",
            "/broadcasting/auth",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = body,
        )
}
