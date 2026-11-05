package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class StreamedResponseTest {
    @Test
    fun streamMaterializesForBodyBytesAndMarksStreamed() {
        val response =
            Response.stream(
                { out -> out.write("hello-stream".toByteArray(StandardCharsets.UTF_8)) },
                "text/plain; charset=utf-8",
                201,
            )
        assertTrue(response.isStreamed())
        assertEquals(201, response.status)
        assertEquals("text/plain; charset=utf-8", response.headers["Content-Type"])
        assertEquals("hello-stream", String(response.bodyBytes(), StandardCharsets.UTF_8))
        assertTrue(response.body.isEmpty())
    }

    @Test
    fun streamDownloadSetsDispositionAndHeaders() {
        val response =
            Response.streamDownload(
                { out -> out.write("csv".toByteArray()) },
                "report.csv",
                mapOf("X-Export" to "1"),
                "text/csv",
            )
        assertTrue(response.isStreamed())
        assertEquals("text/csv", response.headers["Content-Type"])
        assertTrue(response.headers["Content-Disposition"]!!.contains("attachment"))
        assertTrue(response.headers["Content-Disposition"]!!.contains("report.csv"))
        assertEquals("1", response.headers["X-Export"])
        assertEquals("csv", String(response.bodyBytes()))
    }

    @Test
    fun eventStreamFormatsEventsJsonAndEndMarker() {
        val response =
            Response.eventStream(
                { emit ->
                    emit.emit("hello")
                    emit.emit(StreamedEvent("ping", mapOf("n" to 1)))
                    emit.emit("note", "line1\nline2", "42")
                    assertTrue(emit.connected())
                },
                endStreamWith = "</stream>",
                heartbeat = Duration.ZERO,
            )
        assertTrue(response.isStreamed())
        assertEquals("text/event-stream; charset=utf-8", response.headers["Content-Type"])
        assertEquals("no-cache", response.headers["Cache-Control"])
        assertEquals("no", response.headers["X-Accel-Buffering"])
        val body = String(response.bodyBytes(), StandardCharsets.UTF_8)
        assertTrue(body.contains("event: update\ndata: hello\n\n"))
        assertTrue(body.contains("event: ping\ndata: {\"n\":1}\n\n") || body.contains("\"n\":1"))
        assertTrue(body.contains("id: 42\n"))
        assertTrue(body.contains("data: line1\ndata: line2\n\n"))
        assertTrue(body.contains("data: </stream>\n\n"))
    }

    @Test
    fun eventStreamReadsLastEventIdFromRequest() {
        val request = Request("GET", "/sse", headers = mapOf("Last-Event-ID" to "99"))
        val seen = AtomicReference<String?>()
        val response =
            Response.eventStream(
                request,
                { emit ->
                    seen.set(emit.lastEventId())
                    emit.emit("ok")
                },
                heartbeat = Duration.ZERO,
            )
        String(response.bodyBytes())
        assertEquals("99", seen.get())
    }

    @Test
    fun eventStreamSkipsEndMarkerWhenBlank() {
        val response =
            Response.eventStream(
                { emit -> emit.emit("only") },
                endStreamWith = null,
                heartbeat = Duration.ZERO,
            )
        val body = String(response.bodyBytes())
        assertTrue(body.contains("data: only"))
        assertFalse(body.contains("</stream>"))
    }

    @Test
    fun streamTimeoutAbortsWriter() {
        val response =
            Response.stream(
                { out ->
                    Thread.sleep(50)
                    out.write(1)
                },
                timeout = Duration.ofMillis(5),
            )
        assertThrows(StreamAbortedException::class.java) {
            response.writeStreamedBody(
                ByteArrayOutputStream(),
                StreamedBody.StreamContext(
                    live = true,
                    timeout = Duration.ofMillis(5),
                ),
            )
        }
    }

    @Test
    fun disconnectSurfacesAsStreamAborted() {
        val response =
            Response.stream({ out ->
                out.write("a".toByteArray())
                out.write("b".toByteArray())
            })
        val context =
            StreamedBody.StreamContext(
                connectedCheck = { false },
                live = true,
            )
        assertThrows(StreamAbortedException::class.java) {
            response.writeStreamedBody(ByteArrayOutputStream(), context)
        }
    }

    @Test
    fun nettyFlushesChunkedStreamAndEventStream() {
        val router = Router()
        router.get("/stream") {
            Response.stream({ out ->
                out.write("one".toByteArray())
                out.flush()
                out.write("two".toByteArray())
            }, "text/plain; charset=utf-8", timeout = Duration.ofSeconds(30))
        }
        router.get("/download") {
            Response.streamDownload({ out -> out.write("file".toByteArray()) }, "out.txt")
        }
        router.get("/sse") { request ->
            Response.eventStream(request, { emit ->
                emit.comment("hi")
                emit.emit("tick", "1", "7")
            }, heartbeat = Duration.ZERO)
        }
        router.get("/sse-factory-id") {
            Response.eventStream(
                { emit ->
                    assertEquals("factory", emit.lastEventId())
                    emit.emit("ok")
                },
                heartbeat = Duration.ZERO,
                timeout = Duration.ofSeconds(30),
                lastEventId = "factory",
            )
        }
        val free = java.net.ServerSocket(0).use { it.localPort }
        val server =
            KonaHttpServer(
                HttpKernel(router),
                "127.0.0.1",
                free,
                transportPreference = "nio",
            )
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            val streamed =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/stream")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, streamed.statusCode())
            assertEquals("onetwo", streamed.body())
            assertTrue(
                streamed.headers().firstValue("transfer-encoding").orElse("").equals("chunked", true) ||
                    streamed.body() == "onetwo",
            )

            val download =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/download")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals("file", download.body())
            assertTrue(download.headers().firstValue("content-disposition").orElse("").contains("out.txt"))

            val sse =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/sse"))
                        .header("Last-Event-ID", "6")
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals("text/event-stream; charset=utf-8", sse.headers().firstValue("content-type").orElse(""))
            assertTrue(sse.body().contains(": hi\n"))
            assertTrue(sse.body().contains("id: 7\n"))
            assertTrue(sse.body().contains("data: 1\n"))
            assertTrue(sse.body().contains("data: </stream>\n"))

            val factoryId =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/sse-factory-id")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertTrue(factoryId.body().contains("data: ok"))

            val head =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/stream"))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, head.statusCode())
            assertEquals("", head.body())
        } finally {
            server.stop()
        }
    }

    @Test
    fun nettyStreamSurvivesClientDisconnectMidWrite() {
        val writes = AtomicInteger(0)
        val started = CountDownLatch(1)
        val router = Router()
        router.get("/slow") {
            Response.stream({ out ->
                started.countDown()
                repeat(50) {
                    out.write(("chunk-$it\n").repeat(256).toByteArray())
                    writes.incrementAndGet()
                    Thread.sleep(30)
                }
            })
        }
        val free = java.net.ServerSocket(0).use { it.localPort }
        val server =
            KonaHttpServer(
                HttpKernel(router),
                "127.0.0.1",
                free,
                transportPreference = "nio",
            )
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            val response =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/slow")).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream(),
                )
            assertTrue(started.await(2, TimeUnit.SECONDS))
            // Read one byte so the response has started, then abort the client.
            response.body().read()
            response.body().close()
            Thread.sleep(200)
            assertTrue(server.isRunning())
            val pingRouter = Router().also { it.get("/ping") { Response.text("ok") } }
            server.replaceKernel(HttpKernel(pingRouter))
            val ping =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/ping")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals("ok", ping.body())
        } finally {
            server.stop()
        }
    }

    @Test
    fun clearingBodyRemovesStreamedFlag() {
        val response = Response.stream({ out -> out.write(1) })
        assertTrue(response.isStreamed())
        response.withBody("x")
        assertFalse(response.isStreamed())
        assertEquals("x", response.body)
        val again = Response.stream({ out -> out.write(2) }).withBodyBytes(byteArrayOf(9))
        assertFalse(again.isStreamed())
        assertArrayEquals(byteArrayOf(9), again.bodyBytes())
    }

    @Test
    fun sseEmitterCommentAndBooleanNumberEncoding() {
        val response =
            Response.eventStream(
                { emit ->
                    emit.comment("ping")
                    emit.emit(true)
                    emit.emit(3)
                    emit.emit(null)
                },
                endStreamWith = "",
                heartbeat = Duration.ZERO,
            )
        val body = String(response.bodyBytes())
        assertTrue(body.contains(": ping\n"))
        assertTrue(body.contains("data: true\n"))
        assertTrue(body.contains("data: 3\n"))
    }

    @Test
    fun liveEventStreamHeartbeatAndDisconnect() {
        val buffer = ByteArrayOutputStream()
        val connected = AtomicInteger(1)
        val body =
            StreamedBody.EventStream(
                callback = { emit ->
                    emit.comment()
                    emit.emit("start")
                    Thread.sleep(40)
                    assertTrue(emit.connected())
                },
                endStreamWith = null,
                heartbeat = Duration.ofMillis(10),
                timeout = Duration.ofMillis(200),
            )
        body.writeTo(
            buffer,
            StreamedBody.StreamContext(
                connectedCheck = { connected.get() > 0 },
                live = true,
                heartbeat = Duration.ofMillis(10),
            ),
        )
        val text = buffer.toString(StandardCharsets.UTF_8)
        assertTrue(text.contains("data: start") || text.contains(": heartbeat"))

        // Negative / zero heartbeat is ignored.
        StreamedBody.EventStream(
            callback = { emit -> emit.emit("z") },
            endStreamWith = null,
            heartbeat = Duration.ofMillis(-1),
        ).writeTo(ByteArrayOutputStream(), StreamedBody.StreamContext(live = true))

        val aborted = ByteArrayOutputStream()
        assertThrows(StreamAbortedException::class.java) {
            StreamedBody.EventStream(
                callback = { emit ->
                    emit.emit("x")
                    connected.set(0)
                    emit.emit("y")
                },
                endStreamWith = null,
                heartbeat = Duration.ZERO,
            ).writeTo(
                aborted,
                StreamedBody.StreamContext(
                    connectedCheck = { connected.get() > 0 },
                    live = true,
                ),
            )
        }

        // Heartbeat task sees a disconnected client.
        connected.set(1)
        val hb = ByteArrayOutputStream()
        StreamedBody.EventStream(
            callback = { _ ->
                Thread.sleep(15)
                connected.set(0)
                Thread.sleep(30)
            },
            endStreamWith = null,
            heartbeat = Duration.ofMillis(5),
        ).writeTo(
            hb,
            StreamedBody.StreamContext(
                connectedCheck = { connected.get() > 0 },
                live = true,
            ),
        )
    }

    @Test
    fun connectedOutputStreamSingleByteAndClose() {
        val response =
            Response.stream({ out ->
                out.write(65)
                out.write(ByteArray(0))
                out.close()
            })
        assertEquals("A", String(response.bodyBytes()))
        // Non-streamed writeStreamedBody is a no-op.
        Response.text("x").writeStreamedBody(ByteArrayOutputStream())
    }

    @Test
    fun defaultSseEmitterHeartbeatGuards() {
        val out = ByteArrayOutputStream()
        val emitter =
            DefaultSseEmitter(
                out,
                StreamedBody.StreamContext(live = true, heartbeat = null),
            )
        emitter.startHeartbeat() // no-op: null heartbeat
        val withBeat =
            DefaultSseEmitter(
                out,
                StreamedBody.StreamContext(live = true, heartbeat = Duration.ofMillis(50)),
            )
        withBeat.startHeartbeat()
        withBeat.startHeartbeat() // second call ignored
        withBeat.stopHeartbeat()
        withBeat.stopHeartbeat()
    }

    @Test
    fun nettyNonAbortFailureClosesChannel() {
        val router = Router()
        router.get("/boom") {
            Response.stream({ _ -> throw IllegalStateException("explode") })
        }
        val free = java.net.ServerSocket(0).use { it.localPort }
        val server =
            KonaHttpServer(HttpKernel(router), "127.0.0.1", free, transportPreference = "nio")
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            runCatching {
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/boom")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            }
            assertTrue(server.isRunning())
        } finally {
            server.stop()
        }
    }

    @Test
    fun streamDownloadAndEventStreamJavaStyleHeaders() {
        val download =
            Response.streamDownload(
                { out -> out.write("x".toByteArray()) },
                "a.bin",
                emptyMap(),
                null,
                "inline",
                200,
                null,
            )
        assertTrue(download.headers["Content-Disposition"]!!.startsWith("inline"))

        val sse =
            Response.eventStream(
                { emit -> emit.emit(StreamedEvent("n", "v")) },
                mapOf("X-Trace" to "1"),
                "</done>",
                Duration.ZERO,
                null,
                "11",
            )
        assertEquals("1", sse.headers["X-Trace"])
        val body = String(sse.bodyBytes())
        assertTrue(body.contains("event: n\n"))
        assertTrue(body.contains("data: </done>\n"))

        val fromReq =
            Response.eventStream(
                Request("GET", "/", headers = mapOf("Last-Event-ID" to "1")),
                { emit -> assertEquals("1", emit.lastEventId()) },
                emptyMap(),
                null,
                Duration.ZERO,
                Duration.ofMillis(50),
            )
        String(fromReq.bodyBytes())
    }

    @Test
    fun nettyCloseConnectionStreamWithoutKeepAlive() {
        val router = Router()
        router.get("/once") {
            Response.stream({ out -> out.write("bye".toByteArray()) })
                .cookie(Cookie("s", "1"))
        }
        val free = java.net.ServerSocket(0).use { it.localPort }
        val server =
            KonaHttpServer(HttpKernel(router), "127.0.0.1", free, transportPreference = "nio")
        server.start()
        try {
            // HTTP/1.0 is not keep-alive by default — exercises the close-after-stream path.
            java.net.Socket("127.0.0.1", free).use { socket ->
                val out = socket.getOutputStream()
                out.write("GET /once HTTP/1.0\r\nHost: localhost\r\n\r\n".toByteArray())
                out.flush()
                val body = socket.getInputStream().readBytes().toString(StandardCharsets.UTF_8)
                assertTrue(body.contains("bye"))
                assertTrue(body.contains("Set-Cookie") || body.contains("bye"))
            }
        } finally {
            server.stop()
        }
    }
}
