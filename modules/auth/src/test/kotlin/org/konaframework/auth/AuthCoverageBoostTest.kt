package org.konaframework.auth

import org.konaframework.auth.authorization.Gate
import org.konaframework.auth.authorization.Policy
import org.konaframework.auth.hashing.BcryptHasher
import org.konaframework.auth.hashing.HashManager
import org.konaframework.auth.session.SessionAttributes
import org.konaframework.auth.session.SessionBag
import org.konaframework.auth.tokens.PersonalAccessTokenManager
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.security.Encrypter
import org.konaframework.security.EncryptionException
import org.konaframework.security.RateLimit
import org.konaframework.security.RateLimiter
import org.konaframework.security.SecurityHeadersConfig
import org.konaframework.security.SecurityHeadersMiddleware
import org.konaframework.security.CorsMiddleware
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class AuthCoverageBoostTest {
    private class User(
        private val id: Any,
        private val password: String,
        private val email: String? = null,
    ) : Authenticatable, HasEmail {
        override fun getAuthIdentifier(): Any = id
        override fun getAuthPassword(): String = password
        override fun getEmail(): String? = email
    }

    private class DuckSession : SessionBag {
        private val data = linkedMapOf<String, Any?>()
        override fun get(key: String): Any? = data[key]
        override fun put(key: String, value: Any?) {
            data[key] = value
        }
        override fun forget(key: String) {
            data.remove(key)
        }
    }

    @Test
    fun coversRemainingBranches() {
        val hasher = BcryptHasher(4)
        val longPwd = "x".repeat(80)
        val hashed = hasher.make(longPwd)
        assertTrue(hasher.check(longPwd, hashed))
        assertTrue(hasher.needsRehash("not-a-hash"))
        assertTrue(hasher.needsRehash("\$2a\$"))
        assertNull(BcryptHasher.parseCost("short"))
        assertNull(BcryptHasher.parseCost("\$2a\$xx\$rest"))

        HashManager(defaultRounds = 4).also { hm ->
            assertNotNull(hm.driver())
        }

        val users = InMemoryUserProvider(hasher)
            .add(User(1, hasher.make("secret"), "a@b.c"))
            .add(User(2, hasher.make("secret")))
        assertEquals(1, users.retrieveByCredentials(mapOf("email" to "a@b.c"))?.getAuthIdentifier())
        assertEquals(1, users.retrieveByCredentials(mapOf("id" to 1))?.getAuthIdentifier())
        assertEquals(1, users.retrieveByCredentials(mapOf("identifier" to 1))?.getAuthIdentifier())
        assertNull(users.retrieveByCredentials(mapOf("nope" to "x")))

        val auth = AuthManager()
        auth.provider("users", users)
        val guard = SessionGuard("web", users)
        auth.extend("web", guard)
        assertEquals("web", guard.getName())
        assertEquals(users, guard.getProvider())

        val request = Request.get("/")
        val bag = DuckSession()
        request.setAttribute(SessionAttributes.ATTRIBUTE_KEY, bag)
        auth.setRequest(request)
        assertTrue(auth.attempt(mapOf("email" to "a@b.c", "password" to "secret")))
        auth.login(User(1, "x"), remember = true)
        assertTrue(auth.check())
        auth.logout()

        // restore via retrieveById path
        bag.put("login_web_id", 1)
        val guard2 = SessionGuard("web", users).setRequest(Request.get("/").also {
            it.setAttribute(SessionAttributes.ATTRIBUTE_KEY, bag)
        }) as SessionGuard
        assertEquals(1, guard2.user()?.getAuthIdentifier())
        assertEquals("web", guard2.getName())

        // foreign session adapt
        val foreign = object {
            private val m = linkedMapOf<String, Any?>()
            fun get(key: String): Any? = m[key]
            fun put(key: String, value: Any?) {
                m[key] = value
            }
            fun forget(key: String) {
                m.remove(key)
            }
        }
        val req2 = Request.get("/")
        req2.setAttribute("session", foreign)
        val adapted = SessionAttributes.resolve(req2)
        assertNotNull(adapted)
        adapted!!.put("k", "v")
        assertEquals("v", adapted.get("k"))
        adapted.forget("k")
        assertNull(SessionAttributes.adaptForeignSession("nope"))

        val u = User(9, "p")
        assertNull(u.getRememberToken())

        val gate = Gate()
        gate.policy(String::class.java, object : Policy {
            override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = ability == "view"
        })
        assertTrue(gate.allows(u, "view", "doc"))
        gate.policy(Int::class.javaObjectType, object : Policy {
            override fun before(user: Authenticatable?, ability: String): Boolean? = false
            override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = true
        })
        assertFalse(gate.allows(u, "edit", 1))

        val tokens = PersonalAccessTokenManager(users = users)
        val created = tokens.create(User(1, "x"), "api")
        assertNotNull(tokens.findToken(created.plainTextToken))
        assertNull(tokens.findToken("bad"))
        assertNull(tokens.findToken("onlyid|"))
        assertNull(tokens.findUser("missing|secret"))

        val api = TokenGuard("api", tokens, users)
        assertEquals("api", api.getName())

        val key = Encrypter.generateKey()
        val enc = Encrypter.fromBase64Key(key)
        val cipher = enc.encryptString("hello")
        assertEquals("hello", enc.decryptString(cipher))
        assertThrows(EncryptionException::class.java) { enc.decryptString("%%%") }
        assertThrows(EncryptionException::class.java) {
            Encrypter(ByteArray(16))
        }
        assertThrows(EncryptionException::class.java) {
            Encrypter.fromBase64Key("base64:!!!!")
        }
        val emptyField = Base64.getEncoder().encodeToString(
            """{"iv":"YQ==","value":"","tag":"YQ=="}""".toByteArray(),
        )
        assertThrows(EncryptionException::class.java) { enc.decryptString(emptyField) }
        val missingField = Base64.getEncoder().encodeToString(
            """{"iv":"YQ==","tag":"YQ=="}""".toByteArray(),
        )
        assertThrows(EncryptionException::class.java) { enc.decryptString(missingField) }
        assertNull(tokens.findToken("no-pipe"))
        assertNull(tokens.findToken(created.plainTextToken.substringBefore('|') + "|wrong-secret-value"))
        assertNull(PersonalAccessTokenManager(users = null).findUser(created.plainTextToken))
        assertNull(BcryptHasher.parseCost("\$2a\$10"))
        assertNull(BcryptHasher.parseCost("\$2a\$ab\$xxxx"))

        val limiter = RateLimiter()
        limiter.forName("api") { RateLimit(1) }
        assertNotNull(limiter.limiter("api"))
        assertTrue(limiter.attempt("k", 3, 60))
        assertEquals(2, limiter.hit("k", 60))
        assertTrue(limiter.attempt("k", 3, 60))
        assertFalse(limiter.attempt("k", 3, 60))

        SecurityHeadersConfig()
        SecurityHeadersMiddleware(SecurityHeadersConfig()).handle(Request.get("/")) { Response.text("ok") }
        CorsMiddleware().handle(Request("OPTIONS", "/", headers = mapOf("Origin" to "http://a"))) { Response.text("ok") }
    }
}
