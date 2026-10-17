package org.konaframework.auth

import org.konaframework.auth.hashing.Argon2Hasher
import org.konaframework.auth.hashing.BcryptHasher
import org.konaframework.auth.hashing.HashManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
}
