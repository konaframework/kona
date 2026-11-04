package org.konaframework.auth

import org.konaframework.auth.hashing.Argon2Hasher
import org.konaframework.auth.hashing.BcryptHasher
import org.konaframework.auth.hashing.HashManager
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CastRegistry
import org.konaframework.cadence.CastType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HashingTest {
    @Test
    fun bcryptMakeCheckAndNeedsRehash() {
        val hasher = BcryptHasher(4)
        val hash = hasher.make("secret")
        assertTrue(hasher.check("secret", hash))
        assertFalse(hasher.check("wrong", hash))
        assertFalse(hasher.check("secret", ""))
        assertFalse(hasher.check("secret", "not-a-hash"))
        assertFalse(hasher.needsRehash(hash))
        assertTrue(hasher.needsRehash(BcryptHasher(5).make("secret")))
        assertTrue(hasher.needsRehash("bogus"))
        assertTrue(hasher.needsRehash("\$2y\$"))
        assertEquals(4, BcryptHasher.parseCost(hash))
        assertEquals(null, BcryptHasher.parseCost("x"))
        assertEquals(null, BcryptHasher.parseCost("\$only"))
        assertThrows(IllegalArgumentException::class.java) { BcryptHasher(3) }
        assertThrows(IllegalArgumentException::class.java) { BcryptHasher(32) }
    }

    @Test
    fun hashManagerDriversAndFacade() {
        val manager = HashManager(defaultRounds = 4, argon2Memory = 16, argon2Iterations = 1, argon2Parallelism = 1)
        assertEquals("bcrypt", manager.getDefaultDriver())
        assertTrue(manager.check("x", manager.make("x")))
        assertFalse(manager.needsRehash(manager.make("x")))
        assertTrue(manager.driver("argon2") is Argon2Hasher)
        assertTrue(manager.driver("argon2id").check("z", manager.driver("argon2id").make("z")))

        val custom = BcryptHasher(5)
        manager.extend("slow", custom)
        manager.setDefaultDriver("slow")
        assertEquals("slow", manager.getDefaultDriver())
        assertTrue(manager.driver("slow").needsRehash(BcryptHasher(4).make("a")))
        assertThrows(IllegalArgumentException::class.java) { manager.driver("missing") }
        assertThrows(IllegalArgumentException::class.java) { manager.setDefaultDriver("missing") }

        Hash.clearManager()
        assertThrows(IllegalStateException::class.java) { Hash.make("a") }
        Hash.setManager(manager)
        assertTrue(Hash.check("pw", Hash.make("pw")))
        assertFalse(Hash.needsRehash(Hash.make("pw")))
        assertEquals(manager, Hash.manager())
        Hash.clearManager()
    }

    @Test
    fun isHashedRecognizesBcryptAndArgon() {
        val manager = HashManager(defaultRounds = 4, argon2Memory = 16, argon2Iterations = 1, argon2Parallelism = 1)
        Hash.setManager(manager)
        val bcrypt = Hash.make("secret")
        val argon = manager.driver("argon2id").make("secret")
        assertTrue(Hash.isHashed(bcrypt))
        assertTrue(Hash.isHashed(argon))
        assertFalse(Hash.isHashed("secret"))
        assertFalse(Hash.isHashed(""))
        assertFalse(Hash.isHashed("\$2a\$10\$short"))
        assertFalse(Hash.isHashed("\$plain"))
        assertFalse(Hash.isHashed("\$argon2id\$v=19\$bogus"))
        // Long enough to pass the length gate but not a real bcrypt cost field.
        assertFalse(Hash.isHashed("\$2x\$" + "a".repeat(60)))
        assertFalse(Hash.isHashed("\$2a\$xx\$" + "a".repeat(53)))
        assertTrue(Hash.isHashed(bcrypt.replace("\$2a\$", "\$2y\$")))
        assertTrue(Hash.isHashed(manager.driver("bcrypt").make("z").replace("\$2a\$", "\$2b\$")))
        Hash.clearManager()
    }

    @Test
    fun hashedCastHashesPlainTextOnce() {
        val manager = HashManager(defaultRounds = 4)
        Hash.setManager(manager)
        CastRegistry.register("hashed") { HashedCast() }
        try {
            class UserStub : Cadence<UserStub>() {
                override val table = "users"
                override val timestamps = false
            }

            val model = UserStub()
            val cast = HashedCast()
            val hashed = cast.set(model, "password", "secret", emptyMap()) as String
            assertTrue(Hash.isHashed(hashed))
            assertTrue(manager.isHashed(hashed))
            assertTrue(Hash.check("secret", hashed))
            assertEquals(hashed, cast.set(model, "password", hashed, emptyMap()))
            assertNull(cast.set(model, "password", null, emptyMap()))
            assertEquals("", cast.set(model, "password", "", emptyMap()))
            assertEquals(hashed, cast.get(model, "password", hashed, emptyMap()))
            assertTrue(CastRegistry.isRegistered("hashed"))

            // Exercises CastHasher wired by Hash.setManager (CastType.HASHED).
            val viaType = CastType.HASHED.set("secret") as String
            assertTrue(Hash.isHashed(viaType))
            assertEquals(viaType, CastType.HASHED.set(viaType))
            assertNull(CastType.HASHED.set(null))
            assertEquals(viaType, CastType.HASHED.get(viaType))
        } finally {
            CastRegistry.unregister("hashed")
            Hash.clearManager()
        }
    }
}
