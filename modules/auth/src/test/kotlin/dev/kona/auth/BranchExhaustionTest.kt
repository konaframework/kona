package dev.kona.auth

import dev.kona.auth.authorization.Gate
import dev.kona.auth.hashing.BcryptHasher
import dev.kona.auth.passwords.HasPasswordEmail
import dev.kona.auth.passwords.PasswordBroker
import dev.kona.auth.passwords.PasswordResetCallback
import dev.kona.auth.passwords.PasswordResetResult
import dev.kona.auth.session.SessionAttributes
import dev.kona.auth.tokens.AuthenticateWithToken
import dev.kona.auth.tokens.PersonalAccessTokenManager
import dev.kona.http.Request
import dev.kona.http.Response
import dev.kona.security.CorsConfig
import dev.kona.security.CorsMiddleware
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class BranchExhaustionTest {
    private class MailUser(
        private val id: Any,
        private val password: String,
        private val email: String,
    ) : Authenticatable, HasEmail, HasPasswordEmail {
        override fun getAuthIdentifier(): Any = id
        override fun getAuthPassword(): String = password
        override fun getEmail(): String = email
    }

    @Test
    fun tokenGuardCorsPasswordBrokerSessionEdges() {
        val hasher = BcryptHasher(4)
        val users = InMemoryUserProvider(hasher)
        val user = SimpleUser(1, hasher.make("secret"), "a@b.c")
        users.add(user)
        val tokens = PersonalAccessTokenManager(users = users)
        val created = tokens.create(user, "t")
        val guard = TokenGuard("api", tokens, users)
        assertEquals("api", guard.getName())
        assertFalse(guard.attempt(emptyMap(), false))
        assertFalse(guard.attempt(mapOf("token" to "nope"), false))
        assertTrue(guard.attempt(mapOf("token" to created.plainTextToken), false))
        assertEquals(1, guard.id())
        guard.logout()

        val orphan = tokens.create(user, "o")
        tokens.deleteAllFor(user)
        // recreate user missing for tokenable
        val tokens2 = PersonalAccessTokenManager(users = InMemoryUserProvider(hasher))
        val plain = run {
            val u = SimpleUser(99, hasher.make("x"))
            InMemoryUserProvider(hasher).add(u)
            val tm = PersonalAccessTokenManager(users = InMemoryUserProvider(hasher).add(u))
            tm.create(u, "z").plainTextToken
        }
        assertFalse(TokenGuard("api", tokens2, InMemoryUserProvider(hasher)).attempt(mapOf("token" to plain), false))

        val req = Request("GET", "/", headers = mapOf("Authorization" to "Bearer ${created.plainTextToken}"))
        // token was deleted via logout — recreate
        val created2 = tokens.create(user, "t2")
        val reqOk = Request("GET", "/", headers = mapOf("Authorization" to "Bearer ${created2.plainTextToken}"))
        val g2 = TokenGuard("api", tokens, users).setRequest(reqOk) as TokenGuard
        assertEquals(1, g2.user()?.getAuthIdentifier())
        assertNotNull(g2.currentAccessToken())
        g2.logout()
        assertNull(TokenGuard.bearerToken(null))
        assertNull(TokenGuard.bearerToken(Request.get("/")))
        assertNull(TokenGuard.bearerToken(Request("GET", "/", headers = mapOf("Authorization" to "Basic x"))))
        assertNull(TokenGuard.bearerToken(Request("GET", "/", headers = mapOf("Authorization" to "Bearer "))))

        val badBearer = Request("GET", "/", headers = mapOf("Authorization" to "Bearer missing|token"))
        assertNull(TokenGuard("api", tokens, users).setRequest(badBearer).user())

        // AuthenticateWithToken
        val mw = AuthenticateWithToken(tokens, users)
        val authed = mw.handle(
            Request("GET", "/", headers = mapOf("Authorization" to "Bearer ${tokens.create(user, "m").plainTextToken}")),
        ) { Response.text("ok") }
        assertEquals("ok", authed.body)
        org.junit.jupiter.api.Assertions.assertThrows(AuthenticationException::class.java) {
            mw.handle(Request.get("/")) { Response.text("n") }
        }
        val abilityMw = AuthenticateWithToken(tokens, users, ability = "admin")
        org.junit.jupiter.api.Assertions.assertThrows(AuthenticationException::class.java) {
            abilityMw.handle(
                Request(
                    "GET",
                    "/",
                    headers = mapOf(
                        "Authorization" to "Bearer ${tokens.create(user, "m2", listOf("read")).plainTextToken}",
                    ),
                ),
            ) { Response.text("n") }
        }

        // SessionGuard without request / null session
        val sg = SessionGuard("web", users)
        assertNull(sg.user())
        assertNull(sg.id())
        sg.logout()
        val bare = Request.get("/")
        sg.setRequest(bare)
        assertTrue(sg.attempt(mapOf("email" to "a@b.c", "password" to "secret"), true))
        assertTrue(sg.check())
        sg.logout()

        // recover via session id only
        val bagReq = Request.get("/")
        SessionAttributes.ensure(bagReq).put("login_web_id", 1)
        val sg2 = SessionGuard("web", users).setRequest(bagReq) as SessionGuard
        assertEquals(1, sg2.user()?.getAuthIdentifier())
        // cached user attribute
        assertEquals(1, sg2.user()?.getAuthIdentifier())
        // missing user id
        SessionAttributes.ensure(Request.get("/")).put("login_web_id", 404)
        assertNull(SessionGuard("web", users).setRequest(Request.get("/").also {
            SessionAttributes.ensure(it).put("login_web_id", 404)
        }).user())

        // Cors branches
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("*"), allowCredentials = false))
            .handle(Request.get("/")) { Response.text("a") }
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("*"), allowCredentials = true))
            .handle(Request("GET", "/", headers = mapOf("Origin" to "http://x"))) { Response.text("b") }
        val cors = CorsMiddleware(
            CorsConfig(
                allowedOrigins = listOf("http://ok"),
                allowedHeaders = listOf("X-Custom"),
                exposedHeaders = listOf("X-Out"),
                allowCredentials = true,
            ),
        )
        cors.handle(
            Request(
                "OPTIONS",
                "/",
                headers = mapOf(
                    "Origin" to "http://ok",
                    "Access-Control-Request-Method" to "POST",
                    "Access-Control-Request-Headers" to "X-Custom",
                ),
            ),
        ) { Response.text("no") }
        val varyResp = Response.text("c").header("Vary", "Accept")
        cors.applyHeaders(Request("GET", "/", headers = mapOf("Origin" to "http://ok")), varyResp)
        cors.applyHeaders(Request("GET", "/", headers = mapOf("Origin" to "http://nope")), Response.text("d"))
        CorsMiddleware(CorsConfig(allowedHeaders = listOf("*")))
            .applyHeaders(Request("GET", "/", headers = mapOf("Access-Control-Request-Headers" to "A, B")), Response.text("e"))
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("*"), allowCredentials = false))
            .applyHeaders(Request.get("/"), Response.text("f"))

        // Password broker
        val mailUser = MailUser(7, hasher.make("old"), "m@e.c")
        val mailUsers = InMemoryUserProvider(hasher).add(mailUser)
        val clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        val broker = PasswordBroker(mailUsers, hasher, expire = Duration.ofMinutes(1), clock = clock)
        assertNull(broker.createToken(mapOf("email" to "missing@e.c")))
        val token = broker.createToken(mapOf("id" to 7))!!
        assertTrue(broker.tokenValid("m@e.c", token))
        val noop = PasswordResetCallback { _, _ -> }
        assertEquals(PasswordResetResult.INVALID_USER, broker.reset(mapOf("token" to token, "password" to "n"), noop))
        assertEquals(PasswordResetResult.INVALID_TOKEN, broker.reset(mapOf("email" to "m@e.c", "password" to "n"), noop))
        assertEquals(PasswordResetResult.INVALID_PASSWORD, broker.reset(mapOf("email" to "m@e.c", "token" to token), noop))
        assertEquals(PasswordResetResult.INVALID_TOKEN, broker.reset(mapOf("email" to "m@e.c", "token" to "bad", "password" to "n"), noop))
        assertEquals(
            PasswordResetResult.RESET,
            broker.reset(mapOf("email" to "m@e.c", "token" to token, "password" to "new"), noop),
        )
        val token2 = broker.createToken(mapOf("email" to "m@e.c"))!!
        val expiredClock = Clock.fixed(Instant.parse("2020-01-01T02:00:00Z"), ZoneOffset.UTC)
        val broker2 = PasswordBroker(mailUsers, hasher, expire = Duration.ofMinutes(1), clock = expiredClock)
        // copy token into broker2 via create on same map - use shared tokens map
        val shared = java.util.concurrent.ConcurrentHashMap<String, PasswordBroker.ResetRecord>()
        val b1 = PasswordBroker(mailUsers, hasher, Duration.ofMinutes(1), clock, shared)
        val t3 = b1.createToken(mapOf("email" to "m@e.c"))!!
        assertTrue(b1.tokenValid("m@e.c", t3))
        val bExp = PasswordBroker(mailUsers, hasher, Duration.ofMinutes(1), expiredClock, shared)
        assertFalse(bExp.tokenValid("m@e.c", t3))
        val t4 = b1.createToken(mapOf("email" to "m@e.c"))!!
        assertEquals(
            PasswordResetResult.EXPIRED_TOKEN,
            PasswordBroker(mailUsers, hasher, Duration.ofMinutes(1), expiredClock, shared)
                .reset(mapOf("email" to "m@e.c", "token" to t4, "password" to "x")) { _, _ -> },
        )
        b1.deleteToken("m@e.c")
        b1.clear()

        // Gate assignable policy
        open class Animal
        class Dog : Animal()
        val gate = Gate()
        gate.policy(Animal::class.java, object : dev.kona.auth.authorization.Policy {
            override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = true
        })
        assertTrue(gate.allows(user, "x", Dog()))

        // InMemory credential edges
        assertNull(users.retrieveByCredentials(mapOf("password" to "x")))
        assertFalse(users.validateCredentials(user, emptyMap()))
        assertNull(users.retrieveByCredentials(mapOf("email" to null)))

        // Remember token default on anonymous Authenticatable
        val anon = object : Authenticatable {
            override fun getAuthIdentifier(): Any = 0
            override fun getAuthPassword(): String = ""
        }
        assertNull(anon.getRememberToken())

        // SimpleUser credential branches
        val su = SimpleUser(3, "h", null, mutableMapOf("email" to "x@y.z"))
        assertEquals("x@y.z", su.getEmail())
        assertEquals("x@y.z", su.getCredential("email"))
        assertNull(su.getCredential("nope"))
    }
}
