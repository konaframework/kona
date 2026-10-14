package dev.kona.filesystem

import com.sun.net.httpserver.HttpServer
import dev.kona.core.Application
import dev.kona.core.Config
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class FilesystemTest {
    @Test
    fun localAdapterBasics(@TempDir base: Path) {
        val disk = LocalAdapter(base.resolve("app"), "/storage")
        disk.put("a.txt", "hello")
        assertEquals("hello", disk.getString("a.txt"))
        assertTrue(disk.exists("a.txt"))
        assertEquals(5, disk.size("a.txt"))
        assertTrue(disk.lastModified("a.txt") > 0)
        assertTrue(disk.copy("a.txt", "b.txt"))
        assertTrue(disk.move("b.txt", "c.txt"))
        assertFalse(disk.exists("b.txt"))
        assertTrue(disk.exists("c.txt"))
        disk.makeDirectory("nested")
        disk.put("nested/x.txt", "x")
        assertEquals(listOf("a.txt", "c.txt"), disk.files())
        assertEquals(listOf("nested"), disk.directories())
        assertEquals(listOf("x.txt"), disk.files("nested"))
        assertEquals("/storage/a.txt", disk.url("a.txt"))
        assertTrue(disk.path("a.txt").contains("a.txt"))
        assertTrue(disk.delete("c.txt"))
        assertFalse(disk.delete("missing.txt"))
        assertFalse(disk.copy("missing.txt", "z.txt"))
        assertFalse(disk.move("missing.txt", "z.txt"))
        assertTrue(disk.deleteDirectory("nested"))
        assertFalse(disk.deleteDirectory("nope"))
        assertEquals(emptyList<String>(), disk.files("nope"))
        assertEquals(emptyList<String>(), disk.directories("nope"))
        assertThrows(IllegalArgumentException::class.java) {
            disk.put("../escape.txt", "x")
        }
        assertNull(disk.get("missing.txt"))
        assertNull(disk.getString("missing.txt"))
        assertEquals("/x", LocalAdapter(base.resolve("u")).url("x"))
        assertEquals("/x", LocalAdapter(base.resolve("u"), "").url("x"))
    }

    @Test
    fun s3MemoryAndManager(@TempDir base: Path) {
        val memory = MemoryS3ClientAdapter()
        val s3 = S3Adapter(memory, "bucket", "https://cdn.example")
        s3.put("a.txt", "hi")
        assertEquals("hi", s3.getString("a.txt"))
        assertTrue(s3.exists("a.txt"))
        assertEquals(2, s3.size("a.txt"))
        assertTrue(s3.lastModified("a.txt") > 0)
        assertTrue(s3.copy("a.txt", "dir/b.txt"))
        assertTrue(s3.move("dir/b.txt", "dir/c.txt"))
        assertEquals(listOf("a.txt"), s3.files())
        assertEquals(listOf("dir"), s3.directories())
        assertEquals(listOf("c.txt"), s3.files("dir"))
        s3.makeDirectory("other")
        assertTrue(s3.exists("other"))
        assertEquals("https://cdn.example/a.txt", s3.url("a.txt"))
        assertEquals("s3://bucket/a.txt", S3Adapter(memory, "bucket").url("a.txt"))
        assertEquals("bucket/a.txt", s3.path("a.txt"))
        assertTrue(s3.delete("a.txt"))
        assertTrue(s3.deleteDirectory("dir"))
        assertFalse(s3.copy("missing", "x"))
        assertFalse(s3.move("missing", "x"))
        assertThrows(IllegalStateException::class.java) { s3.size("missing") }
        assertThrows(IllegalStateException::class.java) { s3.lastModified("missing") }

        val manager = FilesystemManager(
            Config().also {
                it.set("filesystems.default", "local")
                it.set("filesystems.disks.local.driver", "local")
                it.set("filesystems.disks.local.root", base.resolve("local").toString())
                it.set("filesystems.disks.mem.driver", "memory")
                it.set("filesystems.disks.mem.bucket", "m")
            },
            base,
        )
        assertEquals("local", manager.defaultDisk())
        manager.disk().put("f.txt", "1")
        assertEquals("1", manager.disk().getString("f.txt"))
        manager.disk("mem").put("m.txt", "2")
        assertEquals("2", manager.disk("mem").getString("m.txt"))
        assertTrue(FilesystemManager(Config().also { it.set("filesystems.default", "s3-memory") }, base).disk() is S3Adapter)
        assertTrue(FilesystemManager(Config().also { it.set("filesystems.default", "memory") }, base).disk() is S3Adapter)
        assertTrue(FilesystemManager(Config().also { it.set("filesystems.default", "local") }, base).disk() is LocalAdapter)
        assertTrue(FilesystemManager(Config().also { it.set("filesystems.default", "unknown") }, base).disk() is LocalAdapter)
        assertThrows(IllegalStateException::class.java) {
            FilesystemManager(
                Config().also {
                    it.set("filesystems.default", "s3")
                    it.set("filesystems.disks.s3.driver", "s3")
                },
                base,
            ).disk()
        }
        assertThrows(IllegalStateException::class.java) {
            FilesystemManager(Config().also { it.set("filesystems.default", "s3") }, base).disk()
        }
        assertThrows(IllegalArgumentException::class.java) {
            FilesystemManager(
                Config().also {
                    it.set("filesystems.default", "bad")
                    it.set("filesystems.disks.bad.driver", "nope")
                },
                base,
            ).disk()
        }
        val asInterface: FilesystemAdapter = LocalAdapter(base.resolve("iface"))
        asInterface.put("via-default.txt", "bytes-as-string")
        assertEquals("bytes-as-string", asInterface.getString("via-default.txt"))
        val mem = MemoryS3Client()
        mem.put("a", byteArrayOf(1))
        assertEquals(listOf("a"), mem.keys("a"))
        assertEquals(emptyList<String>(), mem.keys("z"))
        assertNull(mem.get("missing"))
        assertFalse(mem.delete("missing"))
        val s3blank = S3Adapter(MemoryS3ClientAdapter(mem), "b")
        s3blank.makeDirectory("dir")
        assertTrue(s3blank.exists("dir"))
        assertEquals(emptyList<String>(), s3blank.directories("missing"))
        assertEquals(emptyList<String>(), s3blank.files("missing"))
        s3blank.put("nested/file.txt", "x")
        assertEquals(listOf("file.txt"), s3blank.files("nested"))
        assertEquals(listOf("nested"), s3blank.directories(""))
        s3blank.put("top.txt", "t")
        assertTrue(s3blank.files("").contains("top.txt"))
        // LocalAdapter url + root file without nested parent edge
        val flat = LocalAdapter(base.resolve("flat"), "")
        flat.put("root.txt", "r")
        assertEquals("/root.txt", flat.url("root.txt"))
        flat.put("only", byteArrayOf(9))
        assertArrayEquals(byteArrayOf(9), flat.get("only"))
        // manager s3 disk with full config pointing at memory is not available; hit url on memory disk
        FilesystemManager(
            Config().also {
                it.set("filesystems.default", "mem")
                it.set("filesystems.disks.mem.driver", "s3-memory")
                it.set("filesystems.disks.mem.url", "https://cdn.test")
                it.set("filesystems.disks.mem.bucket", "b")
            },
            base,
        ).disk().put("u.txt", "1")

        val app = Application(base.resolve("app"))
        app.config.set("filesystems.default", "local")
        app.config.set("filesystems.disks.local.root", base.resolve("app-store").toString())
        app.register(FilesystemServiceProvider()).boot()
        app.resolve(FilesystemAdapter::class.java).put("p.txt", "v")
        assertEquals("v", app.resolve(FilesystemManager::class.java).disk().getString("p.txt"))
    }

    @Test
    fun httpS3ClientAgainstLocalServer() {
        val store = ConcurrentHashMap<String, ByteArray>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val key = path.removePrefix("/bucket/").trimStart('/')
            when (exchange.requestMethod) {
                "PUT" -> {
                    val body = exchange.requestBody.readAllBytes()
                    store[key] = body
                    exchange.sendResponseHeaders(200, -1)
                }
                "GET" -> {
                    if (exchange.requestURI.query?.contains("list-type=2") == true) {
                        val prefix = Regex("prefix=([^&]*)").find(exchange.requestURI.rawQuery ?: "")?.groupValues?.get(1)?.let {
                            java.net.URLDecoder.decode(it, StandardCharsets.UTF_8)
                        } ?: ""
                        val xml = buildString {
                            append("<ListBucketResult>")
                            store.keys.filter { it.startsWith(prefix) }.forEach { append("<Key>$it</Key>") }
                            append("</ListBucketResult>")
                        }
                        val bytes = xml.toByteArray(StandardCharsets.UTF_8)
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.write(bytes)
                    } else {
                        val body = store[key]
                        if (body == null) {
                            exchange.sendResponseHeaders(404, -1)
                        } else {
                            exchange.sendResponseHeaders(200, body.size.toLong())
                            exchange.responseBody.write(body)
                        }
                    }
                }
                "DELETE" -> {
                    store.remove(key)
                    exchange.sendResponseHeaders(204, -1)
                }
                else -> exchange.sendResponseHeaders(405, -1)
            }
            exchange.close()
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        try {
            val endpoint = "http://127.0.0.1:${server.address.port}"
            val client = HttpS3Client(endpoint, "bucket", "AKIA", "secret", "us-east-1")
            client.putObject("a.txt", "hello".toByteArray())
            assertArrayEquals("hello".toByteArray(), client.getObject("a.txt"))
            assertNull(client.getObject("missing.txt"))
            assertTrue(client.listKeys("").contains("a.txt"))
            assertTrue(client.deleteObject("a.txt"))
            assertTrue(client.deleteObject("missing.txt"))

            val adapter = S3Adapter(client, "bucket")
            adapter.put("z.txt", "z")
            assertEquals("z", adapter.getString("z.txt"))

            FilesystemManager(
                Config().also {
                    it.set("filesystems.default", "s3")
                    it.set("filesystems.disks.s3.driver", "s3")
                    it.set("filesystems.disks.s3.endpoint", endpoint)
                    it.set("filesystems.disks.s3.bucket", "bucket")
                    it.set("filesystems.disks.s3.key", "AKIA")
                    it.set("filesystems.disks.s3.secret", "secret")
                },
                Path.of("."),
            ).disk().put("m.txt", "1")

            // error statuses
            server.stop(0)
            assertThrows(IllegalStateException::class.java) {
                client.putObject("x", byteArrayOf(1))
            }
        } finally {
            runCatching { server.stop(0) }
        }
    }

    @Test
    fun httpS3ErrorBranches() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            when {
                exchange.requestMethod == "GET" && exchange.requestURI.query?.contains("list-type") == true ->
                    exchange.sendResponseHeaders(500, -1)
                exchange.requestMethod == "GET" -> exchange.sendResponseHeaders(500, -1)
                else -> exchange.sendResponseHeaders(500, -1)
            }
            exchange.close()
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        try {
            val client = HttpS3Client("http://127.0.0.1:${server.address.port}", "bucket", "k", "s")
            assertThrows(IllegalStateException::class.java) { client.putObject("a", byteArrayOf(1)) }
            assertThrows(IllegalStateException::class.java) { client.getObject("a") }
            assertThrows(IllegalStateException::class.java) { client.listKeys() }
            assertFalse(client.deleteObject("a"))
        } finally {
            server.stop(0)
        }
        assertNotNull(MemoryS3Client().also {
            it.put("k", byteArrayOf(1))
            assertArrayEquals(byteArrayOf(1), it.get("k"))
            assertTrue(it.delete("k"))
        })
    }
}
