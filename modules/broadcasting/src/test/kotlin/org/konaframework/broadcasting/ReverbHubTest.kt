package org.konaframework.broadcasting

import org.konaframework.broadcasting.reverb.LocalScaleOutBus
import org.konaframework.broadcasting.reverb.PusherProtocol
import org.konaframework.broadcasting.reverb.RedisScaleOutBus
import org.konaframework.broadcasting.reverb.ReverbBroadcaster
import org.konaframework.broadcasting.reverb.ReverbHub
import org.konaframework.broadcasting.reverb.ReverbServer
import org.konaframework.broadcasting.reverb.ScaleOutMessage
import org.konaframework.redis.InMemoryRedisConnection
import org.konaframework.support.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReverbHubTest {
    @Test
    fun protocolSubscribePresenceAndClientEvents() {
        val hub = ReverbHub("key", "secret")
        val inbox = CopyOnWriteArrayList<String>()
        val client =
            object : ReverbHub.Client {
                override fun send(message: String) {
                    inbox += message
                }

                override fun socketId(): String = "1.1"
            }
        hub.register(client)
        assertTrue(hub.handleClientMessage("1.1", """{"event":"pusher:ping"}""")!!.contains("pong"))
        assertEquals(null, hub.handleClientMessage("1.1", """{"event":"pusher:pong"}"""))

        val bad = hub.subscribe("1.1", "private-orders", "bad", null)
        assertTrue(bad.contains("unauthorized"))

        val auth = PusherSigner.authString("key", "secret", "1.1", "private-orders")
        val ok = hub.subscribe("1.1", "private-orders", auth, null)
        assertTrue(ok.contains("subscription_succeeded"))
        assertEquals(1, hub.listenerCount("private-orders"))

        val channelData = Json.stringify(mapOf("user_id" to "u1", "user_info" to mapOf("name" to "Ada")))
        val presenceAuth = PusherSigner.authString("key", "secret", "1.1", "presence-room", channelData)
        val presence = hub.subscribe("1.1", "presence-room", presenceAuth, channelData)
        assertTrue(presence.contains("presence"))

        hub.publish("private-orders", "OrderShipped", mapOf("id" to 1))
        assertTrue(inbox.any { it.contains("OrderShipped") })

        hub.handleClientMessage(
            "1.1",
            """{"event":"client-typing","channel":"private-orders","data":"{}"}""",
        )
        hub.unsubscribe("1.1", "presence-room")
        hub.unregister("1.1")
        assertEquals(0, hub.connectionCount())

        assertTrue(PusherProtocol.parse("not-json").isEmpty())
        assertTrue(PusherProtocol.dataObject(mapOf("data" to 1)).isEmpty())
        assertTrue(PusherProtocol.error(1, "x").contains("pusher:error"))
        assertTrue(PusherProtocol.event("c", "e", "raw").contains("raw"))
    }

    @Test
    fun scaleOutBusesFanOut() {
        val local = LocalScaleOutBus()
        val seen = mutableListOf<ScaleOutMessage>()
        val sub = local.subscribe { seen += it }
        local.publish(ScaleOutMessage(listOf("a"), "e", mapOf("x" to 1), origin = "o1"))
        assertEquals(1, seen.size)
        sub.close()

        val redis = InMemoryRedisConnection()
        val bus = RedisScaleOutBus(redis)
        val remote = mutableListOf<ScaleOutMessage>()
        val rsub = bus.subscribe { remote += it }
        bus.publish(ScaleOutMessage(listOf("b"), "e2", mapOf("y" to 2), excludeSocketId = "s", origin = "o2"))
        assertEquals(1, remote.size)
        assertEquals("e2", remote[0].event)
        rsub.close()

        val hub = ReverbHub("k", "s")
        val broadcaster = ReverbBroadcaster(hub, local, originId = "origin-a")
        broadcaster.startScaleOutListener()
        local.publish(
            ScaleOutMessage(listOf("orders"), "ping", mapOf("ok" to true), origin = "origin-a"),
        )
        local.publish(
            ScaleOutMessage(listOf("orders"), "ping", mapOf("ok" to true), origin = "other"),
        )
        broadcaster.stopScaleOutListener()
        redis.close()
    }

    @Test
    fun reverbServerAcceptsWebsocketClients() {
        val hub = ReverbHub("app-key", "app-secret")
        val server = ReverbServer(hub, "app-key", "127.0.0.1", 0)
        // Bind on an ephemeral port by constructing with port 0 is not supported by our API —
        // pick a high free port.
        val port = freePort()
        val running = ReverbServer(hub, "app-key", "127.0.0.1", port, transportPreference = "nio")
        running.start()
        assertTrue(running.isRunning())
        assertEquals(port, running.port())

        val latch = CountDownLatch(1)
        val messages = CopyOnWriteArrayList<String>()
        val client = HttpClient.newHttpClient()
        val wsFuture = CompletableFuture<WebSocket>()
        client.newWebSocketBuilder()
            .buildAsync(
                URI.create("ws://127.0.0.1:$port/app/app-key"),
                object : WebSocket.Listener {
                    override fun onOpen(webSocket: WebSocket) {
                        webSocket.request(1)
                        wsFuture.complete(webSocket)
                    }

                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                        messages += data.toString()
                        latch.countDown()
                        webSocket.request(1)
                        return null
                    }
                },
            ).get(5, TimeUnit.SECONDS)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(messages.any { it.contains("pusher:connection_established") })

        val ws = wsFuture.get()
        ws.sendText("""{"event":"pusher:ping","data":{}}""", true).get(2, TimeUnit.SECONDS)
        Thread.sleep(200)
        assertTrue(messages.any { it.contains("pusher:pong") })

        running.stop()
        assertFalse(running.isRunning())
        running.stop()
        server.stop()
    }

    private fun freePort(): Int {
        java.net.ServerSocket(0).use { return it.localPort }
    }
}
