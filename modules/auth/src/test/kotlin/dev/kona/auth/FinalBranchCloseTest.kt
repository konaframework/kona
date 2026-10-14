package dev.kona.auth

import dev.kona.auth.hashing.BcryptHasher
import dev.kona.auth.passwords.PasswordBroker
import dev.kona.auth.passwords.PasswordResetResult
import dev.kona.auth.session.SessionAttributes
import dev.kona.auth.tokens.AuthenticateWithToken
import dev.kona.auth.tokens.PersonalAccessTokenManager
import dev.kona.http.Request
import dev.kona.http.Response
import dev.kona.security.CorsConfig
import dev.kona.security.CorsMiddleware
import dev.kona.security.Encrypter
import dev.kona.security.EncryptionException
import dev.kona.security.RateLimiter
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
import java.util.Base64

class FinalBranchCloseTest {
    @Test
    fun closesRemainingBranches() {
        val hasher = BcryptHasher(4)
        val multi = "é".repeat(50)
        assertTrue(hasher.check(multi, hasher.make(multi)))
        assertTrue(hasher.check("short", hasher.make("short")))
        assertNull(BcryptHasher.parseCost("\$2a\$10"))
        assertNull(BcryptHasher.parseCost("\$2a\$10x"))
        assertNull(BcryptHasher.parseCost("nope"))
        assertEquals(10, BcryptHasher.parseCost("\$2a\$10\$abcdefghijklmnopqrstuu"))

        val users = InMemoryUserProvider(hasher)
        val user = SimpleUser(1, hasher.make("pw"), "a@b.c")
        users.add(user)
        assertFalse(users.validateCredentials(user, mapOf("password" to null)))
        assertNull(users.retrieveByCredentials(mapOf("email" to "missing@x")))

        val noEmail = object : Authenticatable {
            override fun getAuthIdentifier(): Any = 7
            override fun getAuthPassword(): String = hasher.make("pw")
        }
        users.add(noEmail)
        assertNull(users.retrieveByCredentials(mapOf("email" to "nobody")))

        val auth = AuthManager()
        auth.setRequest(null)
        auth.provider("users", users)
        val guard = SessionGuard("web", users)
        auth.extend("web", guard)
        auth.guard("web")
        val req = Request.get("/")
        SessionAttributes.ensure(req)
        auth.setRequest(req)
        auth.extend("web2", SessionGuard("web2", users))
        auth.guard("web2")
        assertTrue(auth.attempt(mapOf("email" to "a@b.c", "password" to "pw")))
        assertFalse(auth.guest())
        auth.logout()
        auth.setRequest(null)

        val sg = SessionGuard("web", users)
        sg.logout()
        sg.setRequest(Request.get("/"))
        assertTrue(sg.attempt(mapOf("email" to "a@b.c", "password" to "pw"), false))
        assertNotNull(sg.id())
        val req2 = Request.get("/")
        req2.setAttribute(SessionGuard.USER_ATTRIBUTE, user)
        val sgCached = SessionGuard("web", users).setRequest(req2) as SessionGuard
        assertEquals(1, sgCached.user()?.getAuthIdentifier())

        val tokens = PersonalAccessTokenManager(users = users)
        val created = tokens.create(user, "t")
        val tg = TokenGuard("api", tokens, users)
        assertTrue(tg.attempt(mapOf("token" to created.plainTextToken), false))
        assertNotNull(tg.id())
        assertNotNull(tg.currentAccessToken())
        tg.logout()
        assertNull(tg.id())
        assertFalse(tg.attempt(mapOf("token" to null), false))
        assertFalse(tg.attempt(mapOf("token" to "missing|x"), false))

        val emptyUsers = InMemoryUserProvider(hasher)
        val sharedTokens = PersonalAccessTokenManager(users = users)
        val tokOnly = sharedTokens.create(user, "x")
        assertFalse(
            TokenGuard("api", sharedTokens, emptyUsers)
                .attempt(mapOf("token" to tokOnly.plainTextToken), false),
        )

        val withReq = TokenGuard("api", tokens, users)
        val livePlain = tokens.create(user, "live").plainTextToken
        val tokenReq = Request("GET", "/", headers = mapOf("Authorization" to "Bearer $livePlain"))
        withReq.setRequest(tokenReq)
        assertNotNull(withReq.user())
        withReq.logout()

        val bareTg = TokenGuard("api", tokens, users)
        bareTg.login(user, false)
        bareTg.logout()

        assertNull(TokenGuard("api", tokens, users).user())
        val noBearer = TokenGuard("api", tokens, users)
        noBearer.setRequest(Request.get("/"))
        assertNull(noBearer.user())
        assertNull(TokenGuard.bearerToken(null))
        assertNull(TokenGuard.bearerToken(Request.get("/")))
        assertNull(
            TokenGuard.bearerToken(
                Request("GET", "/", headers = mapOf("Authorization" to "Basic x")),
            ),
        )
        assertNull(
            TokenGuard.bearerToken(
                Request("GET", "/", headers = mapOf("Authorization" to "Bearer ")),
            ),
        )
        assertEquals(
            "abc",
            TokenGuard.bearerToken(
                Request("GET", "/", headers = mapOf("Authorization" to "Bearer abc")),
            ),
        )

        val ghostTok = tokens.create(user, "ghost-user")
        val ghostGuard = TokenGuard("api", tokens, emptyUsers)
        ghostGuard.setRequest(
            Request("GET", "/", headers = mapOf("Authorization" to "Bearer ${ghostTok.plainTextToken}")),
        )
        assertNull(ghostGuard.user())

        val plain = tokens.create(user, "m").plainTextToken
        val mw = AuthenticateWithToken(tokens, users = null)
        assertEquals(
            "ok",
            mw.handle(
                Request("GET", "/", headers = mapOf("Authorization" to "Bearer $plain")),
            ) { Response.text("ok") }.body,
        )

        assertNull(tokens.findToken("|secret"))
        assertNull(tokens.findToken(""))
        assertNull(tokens.findToken("nopipe"))
        assertNull(tokens.findToken("deadbeef|wrong"))
        assertNull(PersonalAccessTokenManager(users = null).findUser(plain))

        CorsMiddleware().handle(Request("OPTIONS", "/")) { Response.text("x") }
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("*"), allowCredentials = false))
            .applyHeaders(Request.get("/"), Response.text("y"))
        val r = Response.text("z").header("Vary", "Accept, Origin")
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("http://a")))
            .applyHeaders(Request("GET", "/", headers = mapOf("Origin" to "http://a")), r)
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("*"), allowCredentials = true))
            .applyHeaders(Request.get("/"), Response.text("cred"))
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("*"), allowCredentials = true))
            .applyHeaders(
                Request("GET", "/", headers = mapOf("Origin" to "http://cred")),
                Response.text("cred2"),
            )
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("http://listed"), allowCredentials = false))
            .applyHeaders(Request.get("/"), Response.text("listed-null-origin"))
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("http://z")))
            .applyHeaders(
                Request("GET", "/", headers = mapOf("Origin" to "http://z")),
                Response.text("v").header("Vary", " ,Accept, "),
            )
        CorsMiddleware(CorsConfig(allowedOrigins = listOf("http://only")))
            .applyHeaders(
                Request("GET", "/", headers = mapOf("Origin" to "http://other")),
                Response.text("no"),
            )

        val limiter = RateLimiter()
        assertEquals(5, limiter.retriesLeft("none", 5))
        assertEquals(0, limiter.availableIn("none"))
        assertTrue(limiter.attempt("z", 1, 60))
        assertFalse(limiter.tooManyAttempts("fresh", 1))
        assertTrue(limiter.attempt("keep", 10, 60))
        assertTrue(limiter.hit("keep", 60) >= 2)
        assertTrue(limiter.retriesLeft("keep", 10) < 10)
        assertTrue(limiter.availableIn("keep") >= 0)
        limiter.clear("z")
        limiter.clearAll()

        val rotatingClock = object : Clock() {
            private var instant = Instant.parse("2020-01-01T00:00:00Z")
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = instant
            fun advance(seconds: Long) {
                instant = instant.plusSeconds(seconds)
            }
        }
        val rotating = RateLimiter(rotatingClock)
        assertEquals(1, rotating.hit("expire-me", 5))
        rotatingClock.advance(6)
        assertEquals(1, rotating.hit("expire-me", 5))

        val enc = Encrypter.fromBase64Key(Encrypter.generateKey())
        val noTag = Base64.getEncoder().encodeToString("""{"iv":"YQ==","value":"YQ=="}""".toByteArray())
        assertThrows(EncryptionException::class.java) { enc.decryptString(noTag) }
        val emptyTag = Base64.getEncoder().encodeToString(
            """{"iv":"YQ==","value":"YQ==","tag":""}""".toByteArray(),
        )
        assertThrows(EncryptionException::class.java) { enc.decryptString(emptyTag) }
        val arrayPayload = Base64.getEncoder().encodeToString("""[]""".toByteArray())
        assertThrows(EncryptionException::class.java) { enc.decryptString(arrayPayload) }
        val badB64 = Base64.getEncoder().encodeToString(
            """{"iv":"!!!","value":"YQ==","tag":"YQ=="}""".toByteArray(),
        )
        assertThrows(EncryptionException::class.java) { enc.decryptString(badB64) }
        val roundTrip = enc.encryptString("hello")
        assertEquals("hello", enc.decryptString(roundTrip))
        val tampered = Base64.getEncoder().encodeToString(
            """{"iv":"AAAAAAAAAAAA","value":"AAAA","tag":"AAAAAAAAAAAAAAAAAAAAAA=="}""".toByteArray(),
        )
        assertThrows(EncryptionException::class.java) { enc.decryptString(tampered) }

        val su = SimpleUser(9, "h")
        assertEquals(9, su.getCredential("id"))
        assertEquals(9, su.getCredential("identifier"))
        su.setRememberToken("r")
        assertEquals("r", su.getRememberToken())

        val anon: Authenticatable = object : Authenticatable {
            override fun getAuthIdentifier(): Any = 0
            override fun getAuthPassword(): String = ""
        }
        assertNull(anon.getRememberToken())
        anon.setRememberToken("x")

        val sgBare = SessionGuard("web", users)
        sgBare.setRequest(Request.get("/"))
        sgBare.logout()
        assertNull(sgBare.user())

        val sgNoSession = SessionGuard("web", users).setRequest(Request.get("/")) as SessionGuard
        assertNull(sgNoSession.user())

        assertNull(
            SessionAttributes.adaptForeignSession(
                object {
                    fun get(key: Int): Any? = null
                },
            ),
        )
        assertNull(
            SessionAttributes.adaptForeignSession(
                object {
                    fun get(): Any? = null
                    fun get(a: String, b: String): Any? = null
                },
            ),
        )
        assertNull(
            SessionAttributes.adaptForeignSession(
                object {
                    fun get(key: String): Any? = null
                    fun put(key: Int, value: Any?) {}
                },
            ),
        )
        assertNull(
            SessionAttributes.adaptForeignSession(
                object {
                    fun get(key: String): Any? = null
                    fun put(key: String, value: Any?) {}
                    fun forget(key: Int) {}
                    fun forget(a: String, b: String) {}
                },
            ),
        )
        val adapted = SessionAttributes.adaptForeignSession(
            object {
                private val data = linkedMapOf<String, Any?>()
                fun get(key: String): Any? = data[key]
                fun put(key: String, value: Any?) {
                    data[key] = value
                }
                fun forget(key: String) {
                    data.remove(key)
                }
            },
        )
        assertNotNull(adapted)
        adapted!!.put("k", "v")
        assertEquals("v", adapted.get("k"))
        adapted.forget("k")
        assertNull(adapted.get("k"))

        val plain2 = tokens.create(user, "ghost").plainTextToken
        val emptyProv = InMemoryUserProvider(hasher)
        AuthenticateWithToken(tokens, emptyProv).handle(
            Request("GET", "/", headers = mapOf("Authorization" to "Bearer $plain2")),
        ) { Response.text("g") }

        val plainUser = SimpleUser(42, hasher.make("p"))
        val brokerUsers = InMemoryUserProvider(hasher).add(plainUser)
        val broker = PasswordBroker(brokerUsers, hasher)
        assertNull(broker.createToken(mapOf("id" to 999)))
        val resetTok = broker.createToken(mapOf("id" to 42, "password" to "ignore", "token" to "ignore"))
        assertNotNull(resetTok)
        assertTrue(broker.tokenValid("42", resetTok!!))
        assertFalse(broker.tokenValid("missing", resetTok))
        assertFalse(broker.tokenValid("42", "wrong"))
        assertEquals(
            PasswordResetResult.INVALID_USER,
            broker.reset(mapOf("token" to resetTok, "password" to "n")) { _, _ -> },
        )
        assertEquals(
            PasswordResetResult.INVALID_TOKEN,
            broker.reset(mapOf("email" to "42", "password" to "n")) { _, _ -> },
        )
        assertEquals(
            PasswordResetResult.INVALID_PASSWORD,
            broker.reset(mapOf("email" to "42", "token" to resetTok)) { _, _ -> },
        )
        assertEquals(
            PasswordResetResult.INVALID_TOKEN,
            broker.reset(mapOf("email" to "42", "token" to "bad", "password" to "n")) { _, _ -> },
        )
        assertEquals(
            PasswordResetResult.INVALID_TOKEN,
            broker.reset(mapOf("email" to "no-record", "token" to "x", "password" to "n")) { _, _ -> },
        )
        assertEquals(
            PasswordResetResult.RESET,
            broker.reset(mapOf("email" to "42", "token" to resetTok, "password" to "n")) { _, _ -> },
        )
        broker.deleteToken("42")
        broker.clear()

        val emailed = SimpleUser(43, hasher.make("p"), "e@x.c")
        brokerUsers.add(emailed)
        val emailTok = broker.createToken(mapOf("email" to "e@x.c"))
        assertNotNull(emailTok)
        assertTrue(broker.tokenValid("e@x.c", emailTok!!))

        val bareAuth = object : Authenticatable {
            override fun getAuthIdentifier(): Any = 77
            override fun getAuthPassword(): String = hasher.make("p")
        }
        brokerUsers.add(bareAuth)
        val bareTok = broker.createToken(mapOf("id" to 77))
        assertNotNull(bareTok)
        assertTrue(broker.tokenValid("77", bareTok!!))

        val nullEmailUser = SimpleUser(88, hasher.make("p"), null)
        brokerUsers.add(nullEmailUser)
        val nullEmailTok = broker.createToken(mapOf("id" to 88))
        assertNotNull(nullEmailTok)
        assertTrue(broker.tokenValid("88", nullEmailTok!!))

        val fixed = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        val lateClock = Clock.fixed(Instant.parse("2020-01-01T00:00:05Z"), ZoneOffset.UTC)
        val shared = java.util.concurrent.ConcurrentHashMap<String, PasswordBroker.ResetRecord>()
        val early = PasswordBroker(brokerUsers, hasher, Duration.ofSeconds(1), fixed, shared)
        val expiredTok = early.createToken(mapOf("id" to 42))!!
        val late = PasswordBroker(brokerUsers, hasher, Duration.ofSeconds(1), lateClock, shared)
        assertFalse(late.tokenValid("42", expiredTok))

        val shared3 = java.util.concurrent.ConcurrentHashMap<String, PasswordBroker.ResetRecord>()
        val early3 = PasswordBroker(brokerUsers, hasher, Duration.ofSeconds(1), fixed, shared3)
        val plainExpired = early3.createToken(mapOf("id" to 42))!!
        val late3 = PasswordBroker(brokerUsers, hasher, Duration.ofSeconds(1), lateClock, shared3)
        assertEquals(
            PasswordResetResult.EXPIRED_TOKEN,
            late3.reset(mapOf("email" to "42", "token" to plainExpired, "password" to "x")) { _, _ -> },
        )

        val orphanUsers = InMemoryUserProvider(hasher).add(SimpleUser(99, hasher.make("p")))
        val orphanBroker = PasswordBroker(orphanUsers, hasher)
        val orphanTok = orphanBroker.createToken(mapOf("id" to 99))!!
        orphanUsers.clear()
        assertEquals(
            PasswordResetResult.INVALID_USER,
            orphanBroker.reset(mapOf("email" to "99", "token" to orphanTok, "password" to "x")) { _, _ -> },
        )
    }
}
