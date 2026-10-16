package io.konaframework.session

import io.konaframework.core.Application
import io.konaframework.core.Config
import io.konaframework.http.Request
import io.konaframework.http.Response
import io.konaframework.redis.InMemoryRedisConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RedisSessionStoreTest {
    @Test
    fun readWriteDestroy() {
        val redis = InMemoryRedisConnection()
        val store = RedisSessionStore(redis, "sess:")
        val id = SessionId.generate()
        assertTrue(store.read(id).isEmpty())
        store.write(id, mapOf("user" to "ada", "n" to 1), 0)
        assertEquals("ada", store.read(id)["user"])
        assertEquals(1, store.read(id)["n"])
        assertTrue(redis.ttl("sess:$id") >= 60)
        store.destroy(id)
        assertTrue(store.read(id).isEmpty())
    }

    @Test
    fun sessionManagerRedisDriver(@TempDir base: Path) {
        val redis = InMemoryRedisConnection()
        val config = Config().apply {
            set("session.driver", "redis")
            set("session.connection_prefix", "s:")
            set("session.lifetime", 30)
        }
        val manager = SessionManager(config, base, redis)
        assertTrue(manager.driver() is RedisSessionStore)
        val session = manager.start(Request.get("/"))
        session.put("k", "v")
        val response = Response.text("ok")
        manager.save(session, response)
        val again = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "kona_session=${session.id}")),
        )
        assertEquals("v", again.get("k"))
    }

    @Test
    fun sessionServiceProviderWiresRedisManager(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("session.driver", "redis")
        app.config.set("redis.client", "memory")
        app.register(io.konaframework.redis.RedisServiceProvider())
        app.register(SessionServiceProvider())
        app.boot()
        val manager = app.resolve(SessionManager::class.java)
        val store = manager.driver()
        assertTrue(store is RedisSessionStore)
        val session = manager.start(Request.get("/"))
        session.put("x", "1")
        val response = Response.text("ok")
        manager.save(session, response)
        assertTrue(response.cookies.isNotEmpty())
    }

    @Test
    fun sessionServiceProviderWiresRedisConnection(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("session.driver", "redis")
        app.container.instance(io.konaframework.redis.RedisConnection::class.java, InMemoryRedisConnection())
        app.register(SessionServiceProvider())
        app.boot()
        assertTrue(app.resolve(SessionManager::class.java).driver() is RedisSessionStore)
    }

    @Test
    fun redisDriverRequiresConnection(@TempDir base: Path) {
        val manager = SessionManager(Config().apply { set("session.driver", "redis") }, base)
        assertThrows(IllegalStateException::class.java) { manager.driver() }
    }
}
