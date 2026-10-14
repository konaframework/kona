package dev.kona.auth

import dev.kona.auth.authorization.AuthorizationException
import dev.kona.auth.authorization.Gate
import dev.kona.auth.authorization.Policy
import dev.kona.auth.hashing.BcryptHasher
import dev.kona.auth.passwords.PasswordBroker
import dev.kona.auth.passwords.PasswordResetCallback
import dev.kona.auth.passwords.PasswordResetResult
import dev.kona.auth.tokens.AuthenticateWithToken
import dev.kona.auth.tokens.InMemoryPersonalAccessTokenRepository
import dev.kona.auth.tokens.PersonalAccessTokenManager
import dev.kona.http.Request
import dev.kona.http.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class PasswordsGateTokensTest {
    private val hasher = BcryptHasher(4)

    @Test
    fun passwordBrokerCreateValidateResetAndExpiry() {
        val users = InMemoryUserProvider(hasher)
        var current = SimpleUser(1, hasher.make("old"), "a@b.c")
        users.add(current)
        val fixed = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        val broker = PasswordBroker(users, hasher, Duration.ofMinutes(1), fixed)

        assertNull(broker.createToken(mapOf("email" to "missing@x")))
        val token = broker.createToken(mapOf("email" to "a@b.c"))!!
        assertTrue(broker.tokenValid("a@b.c", token))
        assertFalse(broker.tokenValid("a@b.c", "wrong"))
        assertFalse(broker.tokenValid("missing", token))

        val noop = PasswordResetCallback { _, _ -> }
        assertEquals(
            PasswordResetResult.INVALID_USER,
            broker.reset(mapOf("token" to token, "password" to "new"), noop),
        )
        assertEquals(
            PasswordResetResult.INVALID_TOKEN,
            broker.reset(mapOf("email" to "a@b.c", "password" to "new"), noop),
        )
        assertEquals(
            PasswordResetResult.INVALID_PASSWORD,
            broker.reset(mapOf("email" to "a@b.c", "token" to token), noop),
        )
        assertEquals(
            PasswordResetResult.INVALID_TOKEN,
            broker.reset(mapOf("email" to "a@b.c", "token" to "bad", "password" to "new"), noop),
        )

        val result = broker.reset(mapOf("email" to "a@b.c", "token" to token, "password" to "new")) { user, password ->
            current = SimpleUser(user.getAuthIdentifier(), hasher.make(password), "a@b.c")
            users.clear().add(current)
        }
        assertEquals(PasswordResetResult.RESET, result)
        assertFalse(broker.tokenValid("a@b.c", token))
        assertTrue(hasher.check("new", current.getAuthPassword()))

        val store = ConcurrentHashMap<String, PasswordBroker.ResetRecord>()
        val plain = "plain-token-value"
        store["a@b.c"] = PasswordBroker.ResetRecord(
            email = "a@b.c",
            tokenHash = hasher.make(plain),
            userId = 1,
            createdAt = Instant.parse("2020-01-01T00:00:00Z"),
        )
        val expired = PasswordBroker(
            users,
            hasher,
            Duration.ofMinutes(1),
            Clock.fixed(Instant.parse("2020-01-01T01:01:00Z"), ZoneOffset.UTC),
            store,
        )
        assertFalse(expired.tokenValid("a@b.c", plain))
        store["a@b.c"] = PasswordBroker.ResetRecord(
            email = "a@b.c",
            tokenHash = hasher.make(plain),
            userId = 1,
            createdAt = Instant.parse("2020-01-01T00:00:00Z"),
        )
        assertEquals(
            PasswordResetResult.EXPIRED_TOKEN,
            expired.reset(mapOf("email" to "a@b.c", "token" to plain, "password" to "x"), noop),
        )

        // createToken falls back to user email / id when credentials omit email key matching
        val idOnly = InMemoryUserProvider(hasher).add(SimpleUser(42, hasher.make("p")))
        val idBroker = PasswordBroker(idOnly, hasher)
        val idToken = idBroker.createToken(mapOf("id" to 42))
        assertNotNull(idToken)
        idBroker.deleteToken("42")
        idBroker.clear()

        // INVALID_USER when token ok but user removed
        val goneStore = ConcurrentHashMap<String, PasswordBroker.ResetRecord>()
        goneStore["gone@x"] = PasswordBroker.ResetRecord(
            email = "gone@x",
            tokenHash = hasher.make("t"),
            userId = 999,
            createdAt = Instant.now(),
        )
        val goneBroker = PasswordBroker(users, hasher, tokens = goneStore)
        assertEquals(
            PasswordResetResult.INVALID_USER,
            goneBroker.reset(mapOf("email" to "gone@x", "token" to "t", "password" to "n"), noop),
        )
    }

    @Test
    fun gateDefinePolicyAuthorize() {
        val user = SimpleUser(1, "h", "a@b.c")
        val gate = Gate()
            .define("ping") { u, _ -> u != null }
            .define("echo") { u, args -> args.firstOrNull() == "ok" && u != null }
            .setUserResolver { user }

        assertTrue(gate.has("ping"))
        assertTrue(gate.allows("ping"))
        assertFalse(gate.denies("ping"))
        assertFalse(gate.allows("missing"))
        gate.authorize("ping")

        data class Post(val ownerId: Int)
        gate.policy(
            Post::class.java,
            object : Policy {
                override fun before(user: Authenticatable?, ability: String): Boolean? =
                    if (ability == "admin") true else null

                override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean {
                    val post = model as Post
                    return ability == "update" && user?.getAuthIdentifier() == post.ownerId
                }
            },
        )
        assertTrue(gate.allows("update", Post(1)))
        assertFalse(gate.allows("update", Post(2)))
        assertTrue(gate.allows("admin", Post(2)))
        assertThrows(AuthorizationException::class.java) { gate.authorize("update", Post(9)) }

        val guestGate = gate.forUser(null)
        assertFalse(guestGate.allows("ping"))
        assertTrue(guestGate.denies("ping"))
        assertThrows(AuthorizationException::class.java) { guestGate.authorize("ping") }

        assertNotNull(gate.getPolicyFor(Post(1)))
        assertNotNull(gate.getPolicyFor(Post::class.java))
        assertNull(gate.getPolicyFor("string"))
        assertTrue(gate.denies(user, "nope"))
        assertTrue(gate.allows(user, "echo", "ok"))
        assertTrue(gate.denies("echo", "no"))
        assertThrows(AuthorizationException::class.java) { gate.authorize(user, "nope") }
    }

    @Test
    fun personalAccessTokensAndMiddleware() {
        val users = InMemoryUserProvider(hasher).add(SimpleUser(7, hasher.make("x"), "t@e"))
        val repo = InMemoryPersonalAccessTokenRepository()
        val manager = PersonalAccessTokenManager(repo, users)
        val created = manager.create(users.retrieveById(7)!!, "cli", listOf("read"))
        assertTrue(created.plainTextToken.contains("|"))
        assertNotNull(manager.findToken(created.plainTextToken))
        assertNull(manager.findToken("bad"))
        assertNull(manager.findToken("onlyid"))
        assertNull(manager.findToken("missing|secret"))
        assertEquals(7, manager.findUser(created.plainTextToken)!!.getAuthIdentifier())
        assertNull(PersonalAccessTokenManager(repo, null).findUser(created.plainTextToken))
        assertTrue(created.accessToken.can("read"))
        assertFalse(created.accessToken.can("write"))
        assertTrue(manager.create(users.retrieveById(7)!!, "star").accessToken.can("write"))

        val guard = TokenGuard("api", manager, users)
        val withBearer = Request(
            "GET",
            "/api",
            headers = mapOf("Authorization" to "Bearer ${created.plainTextToken}"),
        )
        guard.setRequest(withBearer)
        assertTrue(guard.check())
        assertEquals(7, guard.id())
        assertNotNull(guard.currentAccessToken())

        assertTrue(guard.attempt(mapOf("token" to created.plainTextToken)))
        assertFalse(guard.attempt(mapOf("token" to "nope")))
        assertFalse(guard.attempt(emptyMap()))

        val mw = AuthenticateWithToken(manager, users, "read")
        val ok = mw.handle(withBearer) { Response.text("ok") }
        assertEquals("ok", ok.body)
        assertThrows(AuthenticationException::class.java) {
            mw.handle(Request.get("/")) { Response.text("x") }
        }
        assertThrows(AuthenticationException::class.java) {
            mw.handle(Request("GET", "/", headers = mapOf("Authorization" to "Bearer nope|x"))) { Response.text("x") }
        }
        assertThrows(AuthenticationException::class.java) {
            AuthenticateWithToken(manager, users, "write").handle(withBearer) { Response.text("x") }
        }
        AuthenticateWithToken(manager).handle(withBearer) { Response.text("anon") }
        assertNull(TokenGuard.bearerToken(Request("GET", "/", headers = mapOf("Authorization" to "Token x"))))
        assertNull(TokenGuard.bearerToken(Request("GET", "/", headers = mapOf("Authorization" to "Bearer "))))
        assertNull(TokenGuard.bearerToken(null))

        guard.login(users.retrieveById(7)!!)
        guard.logout()
        assertTrue(guard.guest())

        manager.deleteAllFor(users.retrieveById(7)!!)
        repo.clear()
        assertTrue(repo.all().isEmpty())
        assertEquals(repo, manager.repository())
        assertNull(repo.findByHash("missing"))
    }
}
