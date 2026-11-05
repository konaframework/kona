package org.konaframework.auth

import org.konaframework.auth.hashing.BcryptHasher
import org.konaframework.auth.middleware.EnsureEmailIsVerified
import org.konaframework.auth.middleware.RequirePassword
import org.konaframework.auth.session.MapSessionBag
import org.konaframework.auth.twofactor.ConfirmTwoFactorAuthentication
import org.konaframework.auth.twofactor.DisableTwoFactorAuthentication
import org.konaframework.auth.twofactor.EnableTwoFactorAuthentication
import org.konaframework.auth.twofactor.GenerateNewRecoveryCodes
import org.konaframework.auth.twofactor.InMemoryUsedCodeStore
import org.konaframework.auth.twofactor.RecoveryCode
import org.konaframework.auth.twofactor.RedirectIfTwoFactorAuthenticatable
import org.konaframework.auth.twofactor.Totp
import org.konaframework.auth.twofactor.TwoFactor
import org.konaframework.auth.twofactor.TwoFactorAuthenticatable
import org.konaframework.auth.twofactor.TwoFactorAuthenticationProvider
import org.konaframework.auth.twofactor.TwoFactorLogin
import org.konaframework.core.Application
import org.konaframework.http.Handler
import org.konaframework.http.HttpException
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.security.Encrypter
import org.konaframework.security.RateLimitExceededException
import org.konaframework.security.RateLimiter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TwoFactorTest {
    private val hasher = BcryptHasher(4)
    private val key = Encrypter.generateKey()
    private val encrypter = Encrypter.fromBase64Key(key)
    private val fixed = Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        TwoFactor.setProvider(null)
        TwoFactor.setEncrypter(null)
        TwoFactor.setRequiresConfirmation(true)
        RecoveryCode.useGenerator(null)
        Auth.clearManager()
        Hash.clearManager()
    }

    @AfterEach
    fun tearDown() {
        TwoFactor.setProvider(null)
        TwoFactor.setEncrypter(null)
        RecoveryCode.useGenerator(null)
        Auth.clearManager()
        Hash.clearManager()
    }

    @Test
    fun totpGenerateVerifyAndOtpAuthUrl() {
        val secret = Totp.generateSecret()
        assertTrue(secret.length >= 16)
        assertTrue(secret.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" })
        val at = Instant.parse("2024-06-01T12:00:00Z").epochSecond
        val code = Totp.code(secret, at)
        assertEquals(6, code.length)
        assertEquals(at / 30, Totp.verify(secret, code, at))
        assertNull(Totp.verify(secret, "000000", at, window = 0))
        assertNull(Totp.verify(secret, "abcdef", at))
        assertNull(Totp.verify("!!!", code, at))
        assertThrows(IllegalArgumentException::class.java) { Totp.generateSecret(0) }
        assertThrows(IllegalArgumentException::class.java) { Totp.code("!!!") }
        val url = Totp.otpAuthUrl("Kona App", "ada@example.com", secret)
        assertTrue(url.startsWith("otpauth://totp/"))
        assertTrue(url.contains("secret=$secret"))
        assertTrue(url.contains("issuer=Kona%20App"))
    }

    @Test
    fun recoveryCodesGenerateAndCustomGenerator() {
        assertThrows(IllegalArgumentException::class.java) { RecoveryCode.generateMany(0) }
        val codes = RecoveryCode.generateMany(8)
        assertEquals(8, codes.size)
        assertEquals(8, codes.toSet().size)
        assertTrue(codes.all { it.contains('-') && it.length == 21 })
        RecoveryCode.useGenerator { "fixed-code" }
        assertEquals("fixed-code", RecoveryCode.generate())
        assertEquals(listOf("fixed-code"), RecoveryCode.generateMany(1))
        RecoveryCode.useGenerator(null)
    }

    @Test
    fun enableConfirmDisableAndRecoveryFlow() {
        val user = MemoryTwoFactorUser(1, hasher.make("secret"), "ada@example.com")
        val provider = TwoFactorAuthenticationProvider(clock = fixed)
        val enable = EnableTwoFactorAuthentication(provider, encrypter)
        enable.enable(user)
        assertNotNull(user.getTwoFactorSecret())
        assertFalse(user.hasEnabledTwoFactorAuthentication(true))
        assertTrue(user.hasEnabledTwoFactorAuthentication(false))
        val plainSecret = encrypter.decryptString(user.getTwoFactorSecret()!!)
        val codes = user.recoveryCodes(encrypter)
        assertEquals(8, codes.size)

        val confirm = ConfirmTwoFactorAuthentication(provider, encrypter, fixed)
        assertFalse(confirm.confirm(user, "000000"))
        assertFalse(confirm.confirm(MemoryTwoFactorUser(3, "x", "z@z.z"), "123456"))
        val otp = Totp.code(plainSecret, Instant.now(fixed).epochSecond)
        assertTrue(confirm.confirm(user, otp))
        assertTrue(user.hasEnabledTwoFactorAuthentication(true))
        assertNotNull(user.getTwoFactorConfirmedAt())

        assertFalse(provider.verify(plainSecret, otp))

        val qr = user.twoFactorQrCodeUrl(provider, encrypter, "Kona", "ada@example.com")
        assertTrue(qr.startsWith("otpauth://totp/"))

        val regen = GenerateNewRecoveryCodes(encrypter)
        val fresh = regen.generate(user)
        assertEquals(8, fresh.size)
        assertEquals(fresh, user.recoveryCodes(encrypter))

        val used = fresh.first()
        user.replaceRecoveryCode(used, encrypter)
        assertFalse(user.recoveryCodes(encrypter).contains(used))
        user.replaceRecoveryCode("missing", encrypter)

        DisableTwoFactorAuthentication().disable(user)
        assertNull(user.getTwoFactorSecret())
        assertNull(user.getTwoFactorRecoveryCodes())
        assertNull(user.getTwoFactorConfirmedAt())
        assertTrue(user.recoveryCodes(encrypter).isEmpty())
        assertThrows(IllegalStateException::class.java) {
            user.twoFactorQrCodeUrl(provider, encrypter, "Kona", "ada@example.com")
        }
        user.setTwoFactorRecoveryCodes("not-valid")
        assertTrue(user.recoveryCodes(encrypter).isEmpty())
        enable.enable(user, force = true)
        enable.enable(user, force = false)
        assertNotNull(user.getTwoFactorSecret())
    }

    @Test
    fun loginChallengeAndRateLimit() {
        val users = InMemoryUserProvider(hasher)
        val user = MemoryTwoFactorUser(9, hasher.make("password"), "ada@example.com")
        users.add(user)
        val auth = AuthManager()
        auth.provider("users", users)
        auth.extend("web", SessionGuard("web", users))
        Auth.setManager(auth)

        val provider = TwoFactorAuthenticationProvider(clock = fixed)
        TwoFactor.setProvider(provider)
        TwoFactor.setEncrypter(encrypter)
        EnableTwoFactorAuthentication(provider, encrypter).enable(user)
        val secret = encrypter.decryptString(user.getTwoFactorSecret()!!)
        ConfirmTwoFactorAuthentication(provider, encrypter, fixed)
            .confirm(user, Totp.code(secret, Instant.now(fixed).epochSecond))

        val session = MapSessionBag(mutableMapOf())
        val request = Request("POST", "/login").also { it.setAttribute("session", session) }

        val redirector = RedirectIfTwoFactorAuthenticatable(auth, users)
        val challenged = redirector.handle(
            request,
            mapOf("email" to "ada@example.com", "password" to "password"),
            remember = true,
        )
        assertTrue(challenged.challenged())
        assertFalse(challenged.failed())
        assertFalse(challenged.passed())
        assertFalse(auth.check())
        assertEquals("9", TwoFactor.pendingLoginId(request)?.toString())
        assertTrue(TwoFactor.pendingRemember(request))
        assertNotNull(challenged.response)
        assertEquals("/two-factor-challenge", challenged.response!!.headers["Location"])

        assertTrue(redirector.handle(request, mapOf("email" to "nope@x", "password" to "x")).failed())
        assertTrue(
            redirector.handle(
                request,
                mapOf("email" to "ada@example.com", "password" to "wrong"),
            ).failed(),
        )

        val plain = MemoryTwoFactorUser(2, hasher.make("password"), "bob@example.com")
        users.add(plain)
        val authed = redirector.handle(
            Request("POST", "/login").also { it.setAttribute("session", MapSessionBag(mutableMapOf())) },
            mapOf("email" to "bob@example.com", "password" to "password"),
        )
        assertTrue(authed.passed())
        assertEquals(2, authed.user!!.getAuthIdentifier())

        val limiter = RateLimiter()
        val login = TwoFactorLogin(auth, users, provider, encrypter, limiter, maxAttempts = 2, decaySeconds = 60)
        assertNull(login.attempt(request, code = "000000"))
        assertNull(login.attempt(request, code = null, recoveryCode = null))
        assertThrows(RateLimitExceededException::class.java) {
            login.attempt(request, code = "000000")
        }
        limiter.clear("two-factor-challenge:9")

        val later = Clock.fixed(Instant.parse("2024-06-01T12:01:00Z"), ZoneOffset.UTC)
        val laterProvider = TwoFactorAuthenticationProvider(clock = later)
        val laterLogin = TwoFactorLogin(auth, users, laterProvider, encrypter, limiter)
        val otp = Totp.code(secret, Instant.now(later).epochSecond)
        assertNotNull(laterLogin.attempt(request, code = otp))
        assertTrue(auth.check())
        assertNull(TwoFactor.pendingLoginId(request))

        auth.logout()
        EnableTwoFactorAuthentication(laterProvider, encrypter).enable(user, force = true)
        val recoverySecret = encrypter.decryptString(user.getTwoFactorSecret()!!)
        ConfirmTwoFactorAuthentication(laterProvider, encrypter, later)
            .confirm(user, Totp.code(recoverySecret, Instant.now(later).epochSecond))
        val codes = user.recoveryCodes(encrypter)
        val challengeReq = Request("POST", "/two-factor-challenge").also {
            it.setAttribute("session", MapSessionBag(mutableMapOf()))
        }
        TwoFactor.rememberLogin(challengeReq, 9, remember = false)
        assertFalse(TwoFactor.pendingRemember(challengeReq))
        assertNotNull(
            TwoFactorLogin(auth, users, laterProvider, encrypter)
                .attempt(challengeReq, recoveryCode = codes.first()),
        )

        val orphan = Request("POST", "/c").also { it.setAttribute("session", MapSessionBag(mutableMapOf())) }
        TwoFactor.rememberLogin(orphan, 404)
        assertNull(TwoFactorLogin(auth, users, laterProvider, encrypter).attempt(orphan, code = "123456"))
        TwoFactor.rememberLogin(orphan, 2)
        assertNull(TwoFactorLogin(auth, users, laterProvider, encrypter).attempt(orphan, code = "123456"))
    }

    @Test
    fun twoFactorFacadeAndProviderRequireSetup() {
        assertThrows(IllegalStateException::class.java) { TwoFactor.provider() }
        assertThrows(IllegalStateException::class.java) { TwoFactor.encrypter() }
        val provider = TwoFactorAuthenticationProvider()
        TwoFactor.setProvider(provider)
        TwoFactor.setEncrypter(encrypter)
        assertEquals(provider, TwoFactor.provider())
        assertEquals(encrypter, TwoFactor.encrypter())
        TwoFactor.setRequiresConfirmation(false)
        assertFalse(TwoFactor.requiresConfirmation())

        val session = MapSessionBag(mutableMapOf())
        val request = Request("GET", "/").also { it.setAttribute("session", session) }
        TwoFactor.rememberLogin(request, 1, true)
        session.put(TwoFactor.LOGIN_REMEMBER, 1)
        assertTrue(TwoFactor.pendingRemember(request))
        session.put(TwoFactor.LOGIN_REMEMBER, "1")
        assertTrue(TwoFactor.pendingRemember(request))
        session.put(TwoFactor.LOGIN_REMEMBER, "yes")
        assertFalse(TwoFactor.pendingRemember(request))
        assertNull(TwoFactor.pendingLoginId(Request("GET", "/")))
        assertFalse(TwoFactor.pendingRemember(Request("GET", "/")))
        TwoFactor.forgetLogin(Request("GET", "/"))
    }

    @Test
    fun usedCodeStoreExpiryAndAuthServiceProviderWiring() {
        val store = InMemoryUsedCodeStore(fixed)
        store.put("a", 10, 30)
        assertEquals(10L, store.get("a"))
        val clockBox = arrayOf<Clock>(fixed)
        val ticking = InMemoryUsedCodeStore(object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant(): Instant = clockBox[0].instant()
        })
        ticking.put("x", 7, 1)
        assertEquals(7L, ticking.get("x"))
        clockBox[0] = Clock.fixed(Instant.parse("2024-06-01T12:00:02Z"), ZoneOffset.UTC)
        assertNull(ticking.get("x"))
        store.clear()
        assertNull(store.get("a"))
        assertNull(store.get("missing"))

        val app = Application.builder(Files.createTempDirectory("kona-2fa"))
            .withProvider(AuthServiceProvider())
            .configure {
                it.set("hash.rounds", 4)
                it.set("app.key", key)
                it.set("auth.two_factor.confirm", "false")
                it.set("auth.two_factor.window", "2")
            }
            .boot()
        assertFalse(TwoFactor.requiresConfirmation())
        assertNotNull(app.make(TwoFactorAuthenticationProvider::class.java))
        assertNotNull(app.make(EnableTwoFactorAuthentication::class.java))
        assertNotNull(app.make(ConfirmTwoFactorAuthentication::class.java))
        assertNotNull(app.make(DisableTwoFactorAuthentication::class.java))
        assertNotNull(app.make(GenerateNewRecoveryCodes::class.java))

        val noKey = Application.builder(Files.createTempDirectory("kona-2fa-nokey"))
            .withProvider(AuthServiceProvider())
            .configure { it.set("hash.rounds", 4) }
            .boot()
        assertTrue(runCatching { noKey.make(EnableTwoFactorAuthentication::class.java) }.isFailure)

        // confirm=true / "1"
        Application.builder(Files.createTempDirectory("kona-2fa-true"))
            .withProvider(AuthServiceProvider())
            .configure {
                it.set("hash.rounds", 4)
                it.set("app.key", Encrypter.generateKey())
                it.set("auth.two_factor.confirm", "1")
            }
            .boot()
        assertTrue(TwoFactor.requiresConfirmation())
    }

    @Test
    fun emailVerificationAndPasswordConfirmationMiddleware() {
        val users = InMemoryUserProvider(hasher)
        val unverified = MemoryTwoFactorUser(1, hasher.make("x"), "a@b.c", verified = false)
        val verified = MemoryTwoFactorUser(2, hasher.make("x"), "c@d.e", verified = true)
        users.add(unverified)
        users.add(verified)
        val auth = AuthManager()
        auth.provider("users", users)
        auth.extend("web", SessionGuard("web", users))
        Auth.setManager(auth)

        val ok = Handler { Response.text("ok") }
        val session = MapSessionBag(mutableMapOf())
        val request = Request("GET", "/dashboard").also { it.setAttribute("session", session) }
        auth.setRequest(request)
        auth.login(unverified)
        val ensure = EnsureEmailIsVerified(auth)
        val redirected = ensure.handle(request, ok)
        assertEquals(302, redirected.status)
        assertEquals("/email/verify", redirected.headers["Location"])

        val jsonReq = Request(
            "GET",
            "/dashboard",
            headers = mapOf("Accept" to "application/json"),
        ).also { it.setAttribute("session", session) }
        assertThrows(HttpException::class.java) { ensure.handle(jsonReq, ok) }

        val inertiaReq = Request(
            "GET",
            "/dashboard",
            headers = mapOf("X-Inertia" to "true"),
        ).also { it.setAttribute("session", session) }
        assertThrows(HttpException::class.java) { ensure.handle(inertiaReq, ok) }

        fun authed(user: Authenticatable): Request =
            Request("GET", "/dashboard").also {
                it.setAttribute("session", session)
                auth.setRequest(it)
                auth.login(user)
            }

        assertEquals("ok", ensure.handle(authed(verified), ok).body)

        // Non-MustVerifyEmail users pass through
        val basic = SimpleUser(5, hasher.make("x"), "e@f.g")
        users.add(basic)
        assertEquals("ok", ensure.handle(authed(basic), ok).body)

        val require = RequirePassword(auth, timeoutSeconds = 60, clock = fixed)
        val settingsReq = authed(verified)
        val confirmRedirect = require.handle(settingsReq, ok)
        assertEquals("/user/confirm-password", confirmRedirect.headers["Location"])
        RequirePassword.confirm(settingsReq, fixed)
        assertEquals("ok", require.handle(settingsReq, ok).body)

        // Number / Instant forms of confirmed_at
        session.put(RequirePassword.CONFIRMED_AT_KEY, Instant.now(fixed))
        assertEquals("ok", require.handle(settingsReq, ok).body)
        session.put(RequirePassword.CONFIRMED_AT_KEY, "not-a-number")
        assertEquals("/user/confirm-password", require.handle(settingsReq, ok).headers["Location"])
        RequirePassword.confirm(settingsReq, fixed)

        val guest = Request("GET", "/settings").also { it.setAttribute("session", MapSessionBag(mutableMapOf())) }
        assertEquals("/login", require.handle(guest, ok).headers["Location"])

        assertFalse(unverified.hasVerifiedEmail())
        unverified.markEmailAsVerified()
        assertTrue(unverified.hasVerifiedEmail())
        assertEquals("a@b.c", unverified.getEmailForVerification())
    }

    private class MemoryTwoFactorUser(
        private val id: Any,
        private var password: String,
        private val email: String,
        verified: Boolean = true,
    ) : Authenticatable, HasCredentials, HasEmail, MustVerifyEmail, TwoFactorAuthenticatable {
        private var secret: String? = null
        private var recovery: String? = null
        private var confirmedAt: Instant? = null
        private var emailVerifiedAt: Instant? = if (verified) Instant.parse("2024-01-01T00:00:00Z") else null
        private var remember: String? = null

        override fun getAuthIdentifier(): Any = id

        override fun getAuthPassword(): String = password

        override fun getRememberToken(): String? = remember

        override fun setRememberToken(token: String?) {
            remember = token
        }

        override fun getCredential(key: String): Any? = if (key == "email") email else null

        override fun getEmail(): String? = email

        override fun emailVerifiedAt(): Instant? = emailVerifiedAt

        override fun markEmailAsVerified() {
            emailVerifiedAt = Instant.parse("2024-01-02T00:00:00Z")
        }

        override fun getEmailForVerification(): String = email

        override fun getTwoFactorSecret(): String? = secret

        override fun setTwoFactorSecret(secret: String?) {
            this.secret = secret
        }

        override fun getTwoFactorRecoveryCodes(): String? = recovery

        override fun setTwoFactorRecoveryCodes(codes: String?) {
            recovery = codes
        }

        override fun getTwoFactorConfirmedAt(): Instant? = confirmedAt

        override fun setTwoFactorConfirmedAt(confirmedAt: Instant?) {
            this.confirmedAt = confirmedAt
        }

        override fun saveTwoFactorState() {
            // in-memory
        }
    }
}
