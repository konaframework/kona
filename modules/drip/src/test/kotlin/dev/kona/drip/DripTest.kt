package dev.kona.drip

import com.sun.net.httpserver.HttpServer
import dev.kona.core.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.Executors

class DripTest {
    @Test
    fun getPostPutDeleteAndProvider(@TempDir base: Path) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { ex ->
            val body = ex.requestBody.readAllBytes()
            val path = ex.requestURI.path
            val reply = when {
                ex.requestMethod == "GET" && path == "/json" -> """{"ok":true}"""
                ex.requestMethod == "GET" -> "get:" + (ex.requestURI.rawQuery ?: "")
                ex.requestMethod == "POST" -> "post:" + String(body, StandardCharsets.UTF_8)
                ex.requestMethod == "PUT" -> "put:" + String(body, StandardCharsets.UTF_8)
                ex.requestMethod == "DELETE" -> "deleted"
                else -> "nope"
            }
            val bytes = reply.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.write(bytes)
            ex.close()
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        try {
            val drip = Drip().baseUrl("http://127.0.0.1:${server.address.port}").withHeaders(mapOf("X-Test" to "1"))
            assertTrue(drip.get("/").successful())
            assertTrue(drip.get("/q", mapOf("a" to "b")).body.contains("a=b"))
            assertTrue(drip.get("/q?x=1", mapOf("a" to "b")).body.contains("a=b"))
            assertTrue(drip.get("http://127.0.0.1:${server.address.port}/").successful())
            assertTrue(drip.post("/", mapOf("n" to 1)).body.contains("n"))
            assertTrue(drip.post("/", "plain").body.contains("plain"))
            assertTrue(drip.put("/", mapOf("k" to "v")).body.contains("k"))
            assertTrue(drip.post("/").successful())
            assertTrue(drip.put("/").successful())
            assertEquals("deleted", drip.delete("/x").body)
            assertEquals(true, drip.get("/json").json()["ok"])
            assertEquals(false, DripResponse(404, "nope", emptyMap()).successful())
            assertEquals(false, DripResponse(199, "nope", emptyMap()).successful())
            assertEquals(true, DripResponse(299, "ok", emptyMap()).successful())
            val app = Application(base)
            app.register(DripServiceProvider()).boot()
            assertTrue(app.container.bound(Drip::class.java))
        } finally {
            server.stop(0)
        }
    }
}
