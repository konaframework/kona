package dev.kona.session

import dev.kona.core.Application
import dev.kona.core.Config
import dev.kona.http.Request
import dev.kona.http.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Base64

class CookieSigningWiringTest {
    @TempDir
    lateinit var base: Path

    @Test
    fun providerSignsCookieWhenAppKeyWithoutEncrypter() {
        val key = "base64:" + Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
        val app = Application.builder(base)
            .configure {
                it.set("app.key", key)
                it.set("session.driver", "cookie")
            }
            .withProvider(SessionServiceProvider())
            .boot()

        val manager = app.resolve(SessionManager::class.java)
        val session = manager.start(Request.get("/"))
        session.put("uid", 7)
        val response = Response.text("ok")
        manager.save(session, response)
        val cookie = response.cookies.first().value
        assertTrue(cookie.contains('.'), cookie)
        val looksPlain = try {
            String(Base64.getUrlDecoder().decode(cookie), Charsets.UTF_8).contains("\"data\"")
        } catch (_: Exception) {
            false
        }
        assertFalse(looksPlain)

        val again = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "kona_session=$cookie")),
        )
        assertEquals(7, again.get("uid"))
    }

    @Test
    fun resolveCookieSignKeyRequiresAppKey() {
        val empty = Application.builder(base).boot()
        assertNull(SessionServiceProvider.resolveCookieSignKey(empty))

        val withKey = Application.builder(base)
            .configure { it.set("app.key", "base64:" + Base64.getEncoder().encodeToString(ByteArray(16))) }
            .boot()
        assertNotNull(SessionServiceProvider.resolveCookieSignKey(withKey))
    }

    @Test
    fun managerAcceptsExplicitSignKey() {
        val key = ByteArray(16) { 3 }
        val config = Config().apply { set("session.driver", "cookie") }
        val manager = SessionManager(config, base, null, null, null, key)
        val session = manager.start(Request.get("/"))
        session.put("n", 1)
        val response = Response.text("ok")
        manager.save(session, response)
        val cookie = response.cookies.first().value
        assertTrue(cookie.contains('.'))
        val again = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "kona_session=$cookie")),
        )
        assertEquals(1, again.get("n"))
    }

    @Test
    fun cookieStoreSignsAndRejectsUnsigned() {
        val key = ByteArray(16) { 4 }
        val store = CookieSessionStore(signKey = key)
        val id = SessionId.generate()
        store.write(id, mapOf("a" to 1), 10)
        assertTrue(store.outboundPayload.contains('.'))
        store.inboundPayload = store.outboundPayload
        assertEquals(1, store.read(id)["a"])

        val plain = CookieSessionStore()
        plain.write(id, mapOf("a" to 1), 10)
        store.inboundPayload = plain.outboundPayload
        assertTrue(store.read(id).isEmpty())

        val javaSigned = CookieSessionStore(
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper(),
            null as java.util.function.Function<String, String>?,
            null as java.util.function.Function<String, String>?,
            key,
        )
        javaSigned.write(id, mapOf("b" to 2), 1)
        javaSigned.inboundPayload = javaSigned.outboundPayload
        assertEquals(2, javaSigned.read(id)["b"])

        val javaEncrypted = CookieSessionStore(
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper(),
            java.util.function.Function { json: String -> "E:$json" },
            java.util.function.Function { payload: String -> payload.removePrefix("E:") },
            key,
        )
        javaEncrypted.write(id, mapOf("c" to 3), 1)
        assertTrue(javaEncrypted.outboundPayload.startsWith("E:"))
        javaEncrypted.inboundPayload = javaEncrypted.outboundPayload
        assertEquals(3, javaEncrypted.read(id)["c"])
    }
}
