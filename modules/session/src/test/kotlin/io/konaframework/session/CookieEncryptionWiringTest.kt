package io.konaframework.session

import io.konaframework.auth.AuthServiceProvider
import io.konaframework.auth.Hash
import io.konaframework.core.Application
import io.konaframework.core.Config
import io.konaframework.http.Request
import io.konaframework.http.Response
import io.konaframework.security.Encrypter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Base64

class CookieEncryptionWiringTest {
    @TempDir
    lateinit var base: Path

    @AfterEach
    fun clearHash() {
        Hash.clearManager()
    }

    @Test
    fun providerWiresEncrypterWhenBound() {
        val key = Encrypter.generateKey()
        val app = Application.builder(base)
            .configure {
                it.set("app.key", key)
                it.set("session.driver", "cookie")
            }
            .withProviders(AuthServiceProvider(), SessionServiceProvider())
            .boot()

        val manager = app.resolve(SessionManager::class.java)
        val session = manager.start(Request.get("/"))
        session.put("uid", 42)
        val response = Response.text("ok")
        manager.save(session, response)
        val cookie = response.cookies.first().value
        assertTrue(cookie.isNotBlank())
        val looksLikePlainBase64 = try {
            val json = String(Base64.getUrlDecoder().decode(cookie), Charsets.UTF_8)
            json.contains("\"data\"")
        } catch (_: Exception) {
            false
        }
        assertFalse(looksLikePlainBase64)

        val again = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "kona_session=$cookie")),
        )
        assertEquals(42, again.get("uid"))
    }

    @Test
    fun providerWiresWhenSessionEncryptAndAppKey() {
        val key = Encrypter.generateKey()
        val app = Application.builder(base)
            .configure {
                it.set("app.key", key)
                it.set("session.encrypt", true)
                it.set("session.driver", "cookie")
            }
            .withProvider(SessionServiceProvider())
            .boot()

        val manager = app.resolve(SessionManager::class.java)
        val session = manager.start(Request.get("/"))
        session.put("k", "v")
        val response = Response.text("ok")
        manager.save(session, response)
        val cookie = response.cookies.first().value

        val again = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "kona_session=$cookie")),
        )
        assertEquals("v", again.get("k"))
    }

    @Test
    fun resolveCookieCodecFallsBackWithoutEncrypterOrKey() {
        val app = Application.builder(base)
            .configure { it.set("session.encrypt", true) }
            .boot()
        val (encrypt, decrypt) = SessionServiceProvider.resolveCookieCodec(app)
        assertNull(encrypt)
        assertNull(decrypt)

        val noEncryptFlag = Application.builder(base).boot()
        val none = SessionServiceProvider.resolveCookieCodec(noEncryptFlag)
        assertNull(none.first)
        assertNull(none.second)
    }

    @Test
    fun managerAcceptsExplicitCodecsWithoutProvider() {
        val enc = Encrypter.fromBase64Key(Encrypter.generateKey())
        val config = Config().apply { set("session.driver", "cookie") }
        val manager = SessionManager(
            config,
            base,
            null,
            { enc.encryptString(it) },
            { enc.decryptString(it) },
        )
        val session = manager.start(Request.get("/"))
        session.put("n", 1)
        val response = Response.text("ok")
        manager.save(session, response)
        assertNotNull(response.cookies.first().value)
        val again = manager.start(
            Request(
                "GET",
                "/",
                headers = mapOf("Cookie" to "kona_session=${response.cookies.first().value}"),
            ),
        )
        assertEquals(1, again.get("n"))
    }
}
