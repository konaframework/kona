package io.konaframework.auth

import io.konaframework.auth.hashing.BcryptHasher
import io.konaframework.auth.session.MapSessionBag
import io.konaframework.auth.session.SessionAttributes
import io.konaframework.auth.session.SessionBag
import io.konaframework.http.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthSessionTest {
    private val hasher = BcryptHasher(4)

    private fun user(email: String = "a@b.c", password: String = "secret"): SimpleUser =
        SimpleUser(1, hasher.make(password), email)

    @Test
    fun sessionGuardAttemptLoginLogoutAndRestore() {
        val provider = InMemoryUserProvider(hasher).add(user())
        val guard = SessionGuard("web", provider)
        val request = Request.get("/").also { SessionAttributes.ensure(it) }
        guard.setRequest(request)

        assertTrue(guard.guest())
        assertFalse(guard.attempt(mapOf("email" to "nope", "password" to "secret")))
        assertFalse(guard.attempt(mapOf("email" to "a@b.c", "password" to "wrong")))
        assertFalse(guard.attempt(mapOf("password" to "secret")))
        assertTrue(guard.attempt(mapOf("email" to "a@b.c", "password" to "secret"), remember = true))
        assertTrue(guard.check())
        assertEquals(1, guard.id())
        assertEquals("a@b.c", (guard.user() as SimpleUser).getEmail())
        assertTrue(SessionAttributes.resolve(request)!!.has("login_web_remember"))

        val restored = SessionGuard("web", provider).setRequest(request) as SessionGuard
        assertEquals(1, restored.id())
        assertTrue(restored.check())

        restored.logout()
        assertTrue(restored.guest())
        assertNull(restored.user())
        assertNull(request.getAttribute(SessionGuard.USER_ATTRIBUTE))
    }

    @Test
    fun sessionBagFromMapAndCustomBag() {
        val map = linkedMapOf<String, Any?>("k" to "v")
        val bag = MapSessionBag(map)
        assertEquals("v", bag.get("k"))
        assertTrue(bag.has("k"))
        bag.put("k2", 2)
        bag.forget("k")
        assertFalse(bag.has("k"))

        val custom = object : SessionBag {
            private val data = mutableMapOf<String, Any?>()
            override fun get(key: String) = data[key]
            override fun put(key: String, value: Any?) {
                data[key] = value
            }
            override fun forget(key: String) {
                data.remove(key)
            }
        }
        val request = Request.get("/").setAttribute(SessionAttributes.ATTRIBUTE_KEY, custom)
        assertEquals(custom, SessionAttributes.resolve(request))

        val mapRequest = Request.get("/").setAttribute(SessionAttributes.ATTRIBUTE_KEY, map)
        assertTrue(SessionAttributes.resolve(mapRequest) is MapSessionBag)
        assertNull(SessionAttributes.resolve(Request.get("/").setAttribute(SessionAttributes.ATTRIBUTE_KEY, "nope")))
        assertNull(SessionAttributes.resolve(Request.get("/")))

        val guard = SessionGuard("web", InMemoryUserProvider(hasher))
        assertThrows(IllegalStateException::class.java) {
            guard.login(user())
        }
    }

    @Test
    fun authManagerAndProviderHelpers() {
        val provider = InMemoryUserProvider(hasher).add(user())
        val auth = AuthManager()
            .provider("users", provider)
            .extend("web", SessionGuard("web", provider))
            .setDefaultDriver("web")
        val request = Request.post("/")
        auth.setRequest(request)
        assertTrue(auth.attempt(mapOf("email" to "a@b.c", "password" to "secret")))
        assertTrue(auth.check())
        assertFalse(auth.guest())
        assertEquals(1, auth.id())
        assertEquals(1, auth.user()!!.getAuthIdentifier())
        auth.logout()
        assertTrue(auth.guest())

        val other = user("b@c.d")
        auth.login(other, remember = false)
        assertEquals("b@c.d", (auth.user() as SimpleUser).getEmail())

        assertEquals(provider, auth.getProvider("users"))
        assertEquals("web", auth.getDefaultDriver())
        assertThrows(IllegalArgumentException::class.java) { auth.guard("missing") }
        assertThrows(IllegalArgumentException::class.java) { auth.getProvider("missing") }

        provider.clear()
        assertTrue(provider.all().isEmpty())
        assertNull(provider.retrieveById(1))
    }

    @Test
    fun simpleUserRememberAndCredentials() {
        val u = SimpleUser(9, "hash", "u@e").setAttribute("username", "neo")
        assertEquals("neo", u.getCredential("username"))
        assertEquals(9, u.getCredential("id"))
        assertEquals("u@e", u.getCredential("email"))
        assertNull(u.getCredential("missing"))
        u.setRememberToken("r1")
        assertEquals("r1", u.getRememberToken())
        val rotated = u.withPasswordHash("hash2")
        assertEquals("hash2", rotated.getAuthPassword())
        assertEquals("r1", rotated.getRememberToken())
    }
}
