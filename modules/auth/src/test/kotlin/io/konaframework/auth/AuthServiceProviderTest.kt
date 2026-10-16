package io.konaframework.auth

import io.konaframework.auth.authorization.Gate

import io.konaframework.auth.hashing.HashManager
import io.konaframework.auth.passwords.PasswordBroker
import io.konaframework.auth.tokens.PersonalAccessTokenManager
import io.konaframework.core.Application
import io.konaframework.security.Encrypter
import io.konaframework.security.RateLimiter
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class AuthServiceProviderTest {
    @Test
    fun registersServicesAndOptionalEncrypter() {
        val base = Files.createTempDirectory("kona-auth")
        val app = Application.builder(base)
            .withProvider(AuthServiceProvider())
            .configure {
                it.set("hash.rounds", 4)
                it.set("app.key", Encrypter.generateKey())
            }
            .boot()

        assertNotNull(app.make(HashManager::class.java))
        assertNotNull(app.make(AuthManager::class.java))
        assertNotNull(app.make(InMemoryUserProvider::class.java))
        assertNotNull(app.make(PasswordBroker::class.java))
        assertNotNull(app.make(Gate::class.java))
        assertNotNull(app.make(PersonalAccessTokenManager::class.java))
        assertNotNull(app.make(RateLimiter::class.java))
        assertNotNull(app.make(Encrypter::class.java))
        assertTrue(Hash.check("a", Hash.make("a")))
        assertNotNull(Auth.manager())
        Hash.clearManager()
        Auth.clearManager()
    }

    @Test
    fun skipsInvalidOrMissingAppKey() {
        val base = Files.createTempDirectory("kona-auth-nokey")
        val app = Application.builder(base)
            .withProvider(AuthServiceProvider())
            .configure { it.set("hash.rounds", 4) }
            .boot()
        assertTrue(runCatching { app.make(Encrypter::class.java) }.isFailure)

        val bad = Application.builder(Files.createTempDirectory("kona-auth-badkey"))
            .withProvider(AuthServiceProvider())
            .configure {
                it.set("hash.rounds", 4)
                it.set("app.key", "not-valid-base64-key!!")
            }
            .boot()
        assertTrue(runCatching { bad.make(Encrypter::class.java) }.isFailure)
        Hash.clearManager()
        Auth.clearManager()
    }
}
