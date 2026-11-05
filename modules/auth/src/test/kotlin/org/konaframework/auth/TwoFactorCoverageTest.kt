package org.konaframework.auth

import org.konaframework.auth.hashing.BcryptHasher
import org.konaframework.auth.middleware.RequirePassword
import org.konaframework.auth.session.MapSessionBag
import org.konaframework.auth.twofactor.ConfirmTwoFactorAuthentication
import org.konaframework.auth.twofactor.EnableTwoFactorAuthentication
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
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.security.Encrypter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Branch / default-parameter coverage for two-factor helpers. */
class TwoFactorCoverageTest {
    private val hasher = BcryptHasher(4)
    private val encrypter = Encrypter.fromBase64Key(Encrypter.generateKey())

    @AfterEach
    fun tearDown() {
        TwoFactor.setProvider(null)
        TwoFactor.setEncrypter(null)
        TwoFactor.setRequiresConfirmation(true)
        RecoveryCode.useGenerator(null)
        Auth.clearManager()
        Hash.clearManager()
    }

    @Test
    fun totpDefaultsPaddingAndEncodingBranches() {
        val secret = Totp.generateSecret(1) // leaves Base32 remainder bits
        assertTrue(secret.isNotEmpty())
        assertNotNull(Totp.verify(secret, Totp.code(secret))) // default atEpochSeconds
        assertNull(Totp.verify(secret, "12")) // wrong length
        assertNull(Totp.verify(secret, "12ab56")) // non-digit
        assertNull(Totp.verify("", "123456"))
        assertNull(Totp.decodeBase32(""))
        assertNull(Totp.decodeBase32("A!"))
        // Over-allocated decode buffer → copyOf branch
        val decoded = Totp.decodeBase32("MFRGG")
        assertNotNull(decoded)
        // Negative counter skipped when window spans below zero
        assertNull(Totp.verify(secret, "000000", atEpochSeconds = 0, window = 4))
        assertNull(Totp.verify(secret, "000000", atEpochSeconds = 100, window = 0))
        assertNull(Totp.verify(secret, "000000", atEpochSeconds = 100, window = -1))
        val url = Totp.otpAuthUrl("Acme_Inc-1.", "user~name@x", secret)
        assertTrue(url.contains("Acme_Inc-1."))
        assertTrue(url.contains("%40") || url.contains("user"))
    }

    @Test
    fun providerReplayNewerThanAndDefaults() {
        InMemoryUsedCodeStore() // default clock ctor
        val clock = Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), ZoneOffset.UTC)
        val store = InMemoryUsedCodeStore(clock)
        val provider = TwoFactorAuthenticationProvider(window = 1, usedCodes = store, clock = clock)
        val secret = Totp.generateSecret()
        val code = Totp.code(secret, Instant.now(clock).epochSecond)
        assertTrue(provider.verify(secret, code))
        assertFalse(provider.verify(secret, code)) // replay: newerThan != null && matched <= newerThan
        // Fresh store, verify with no prior entry (newerThan == null)
        assertTrue(
            TwoFactorAuthenticationProvider(clock = clock, usedCodes = InMemoryUsedCodeStore(clock))
                .verify(secret, Totp.code(secret, Instant.now(clock).epochSecond + 30)),
        )
    }

    @Test
    fun twoFactorRememberNumberZeroAndTrueString() {
        val session = MapSessionBag(mutableMapOf())
        val request = Request("GET", "/").also { it.setAttribute("session", session) }
        TwoFactor.rememberLogin(request, 1)
        session.put(TwoFactor.LOGIN_REMEMBER, 0)
        assertFalse(TwoFactor.pendingRemember(request))
        session.put(TwoFactor.LOGIN_REMEMBER, "TRUE")
        assertTrue(TwoFactor.pendingRemember(request))
    }

    @Test
    fun redirectWhenTwoFactorPresentButDisabled() {
        val users = InMemoryUserProvider(hasher)
        val user = CoverUser(1, hasher.make("password"), "a@b.c")
        users.add(user)
        val auth = AuthManager()
        auth.provider("users", users)
        auth.extend("web", SessionGuard("web", users))
        val request = Request("POST", "/login").also { it.setAttribute("session", mutableMapOf<String, Any?>()) }
        val result = RedirectIfTwoFactorAuthenticatable(auth, users).handle(
            request,
            mapOf("email" to "a@b.c", "password" to "password"),
        )
        assertTrue(result.passed())
    }

    @Test
    fun twoFactorLoginNullLimiterRecoveryMissAndMissingSecret() {
        val users = InMemoryUserProvider(hasher)
        val user = CoverUser(1, hasher.make("password"), "a@b.c")
        users.add(user)
        val auth = AuthManager()
        auth.provider("users", users)
        auth.extend("web", SessionGuard("web", users))
        val provider = TwoFactorAuthenticationProvider()
        val clock = Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), ZoneOffset.UTC)
        EnableTwoFactorAuthentication(provider, encrypter).enable(user)
        val secret = encrypter.decryptString(user.getTwoFactorSecret()!!)
        ConfirmTwoFactorAuthentication(provider, encrypter) // default clock
            .confirm(user, Totp.code(secret))

        val request = Request("POST", "/c").also { it.setAttribute("session", mutableMapOf<String, Any?>()) }
        assertNull(TwoFactorLogin(auth, users, provider, encrypter).attempt(request, code = "123456"))

        TwoFactor.rememberLogin(request, 1)
        assertNull(TwoFactorLogin(auth, users, provider, encrypter).attempt(request, recoveryCode = "nope"))

        // Enabled override with missing secret hits verifyTotp null branch
        val sneaky = object : CoverUser(2, hasher.make("password"), "b@c.d") {
            override fun hasEnabledTwoFactorAuthentication(requiresConfirmation: Boolean): Boolean = true
        }
        users.add(sneaky)
        val sneakyReq = Request("POST", "/c").also { it.setAttribute("session", mutableMapOf<String, Any?>()) }
        TwoFactor.rememberLogin(sneakyReq, 2)
        assertNull(TwoFactorLogin(auth, users, provider, encrypter).attempt(sneakyReq, code = "123456"))
    }

    @Test
    fun requirePasswordDefaultsAndNullSession() {
        val users = InMemoryUserProvider(hasher)
        val user = CoverUser(1, hasher.make("x"), "a@b.c")
        users.add(user)
        val auth = AuthManager()
        auth.provider("users", users)
        auth.extend("web", SessionGuard("web", users))
        val require = RequirePassword(auth) // default timeout + clock
        val session = MapSessionBag(mutableMapOf())
        val request = Request("GET", "/secure").also { it.setAttribute("session", session) }
        auth.setRequest(request)
        auth.login(user)
        assertEquals("/user/confirm-password", require.handle(request, Handler { Response.text("ok") }).headers["Location"])
        RequirePassword.confirm(request) // default clock
        assertEquals("ok", require.handle(request, Handler { Response.text("ok") }).body)

        // Authenticated then strip session → confirmedRecently false
        val stripped = Request("GET", "/secure").also { it.setAttribute("session", session) }
        auth.setRequest(stripped)
        auth.login(user)
        RequirePassword.confirm(stripped)
        stripped.setAttribute("session", null)
        assertEquals("/user/confirm-password", require.handle(stripped, Handler { Response.text("ok") }).headers["Location"])
    }

    @Test
    fun authProviderConfirmTrueStringAndRecoveryDefaults() {
        assertEquals(8, RecoveryCode.generateMany().size) // default count
        val app = Application.builder(Files.createTempDirectory("kona-2fa-cov"))
            .withProvider(AuthServiceProvider())
            .configure {
                it.set("hash.rounds", 4)
                it.set("app.key", Encrypter.generateKey())
                it.set("auth.two_factor.confirm", "TRUE")
            }
            .boot()
        assertTrue(TwoFactor.requiresConfirmation())
        assertNotNull(app.make(TwoFactorAuthenticationProvider::class.java))

        val user = CoverUser(1, "h", "a@b.c")
        assertFalse(user.hasEnabledTwoFactorAuthentication()) // default requiresConfirmation=true
    }

    private open class CoverUser(
        private val id: Any,
        private val password: String,
        private val email: String,
    ) : Authenticatable, HasCredentials, HasEmail, TwoFactorAuthenticatable {
        private var secret: String? = null
        private var recovery: String? = null
        private var confirmedAt: Instant? = null

        override fun getAuthIdentifier(): Any = id

        override fun getAuthPassword(): String = password

        override fun getCredential(key: String): Any? = if (key == "email") email else null

        override fun getEmail(): String? = email

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

        override fun saveTwoFactorState() {}
    }
}
