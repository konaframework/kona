package io.konaframework.auth.hashing

import com.password4j.Argon2Function
import com.password4j.Password
import com.password4j.types.Argon2

/**
 * Argon2id password hasher (Laravel `argon2` / `argon2id` driver shape).
 *
 * Encoded hashes use the PHC string format (`$argon2id$v=19$m=…,t=…,p=…$…`).
 */
class Argon2Hasher @JvmOverloads constructor(
    private val memory: Int = DEFAULT_MEMORY,
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val parallelism: Int = DEFAULT_PARALLELISM,
    private val hashLength: Int = DEFAULT_HASH_LENGTH,
) : Hasher {
    init {
        require(memory >= 8) { "Argon2 memory must be at least 8 KiB" }
        require(iterations >= 1) { "Argon2 iterations must be at least 1" }
        require(parallelism >= 1) { "Argon2 parallelism must be at least 1" }
        require(hashLength >= 4) { "Argon2 hash length must be at least 4" }
    }

    private val function: Argon2Function =
        Argon2Function.getInstance(memory, iterations, parallelism, hashLength, Argon2.ID)

    override fun make(value: String): String = Password.hash(value).with(function).result

    override fun check(value: String, hashed: String): Boolean {
        if (hashed.isBlank() || !hashed.startsWith("\$argon2")) {
            return false
        }
        return try {
            val fromHash = Argon2Function.getInstanceFromHash(hashed)
            Password.check(value, hashed).with(fromHash)
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ArrayIndexOutOfBoundsException) {
            false
        }
    }

    override fun needsRehash(hashed: String): Boolean {
        val params = parseParams(hashed) ?: return true
        if (params.memory != memory) {
            return true
        }
        if (params.iterations != iterations) {
            return true
        }
        if (params.parallelism != parallelism) {
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_MEMORY: Int = 65536
        const val DEFAULT_ITERATIONS: Int = 4
        const val DEFAULT_PARALLELISM: Int = 1
        const val DEFAULT_HASH_LENGTH: Int = 32

        data class Params(val memory: Int, val iterations: Int, val parallelism: Int)

        /**
         * Parses `m`, `t`, and `p` from a PHC Argon2 hash.
         * Example: `$argon2id$v=19$m=65536,t=4,p=1$…`
         */
        @JvmStatic
        fun parseParams(hashed: String): Params? {
            if (!hashed.startsWith("\$argon2")) {
                return null
            }
            val parts = hashed.split('$')
            if (parts.size < 5) {
                return null
            }
            val paramSegment = parts[3]
            val parsed = linkedMapOf<String, Int>()
            for (token in paramSegment.split(',')) {
                val eq = token.indexOf('=')
                if (eq <= 0) {
                    continue
                }
                val key = token.substring(0, eq)
                val value = token.substring(eq + 1).toIntOrNull() ?: continue
                parsed[key] = value
            }
            val memory = parsed["m"]
            val iterations = parsed["t"]
            val parallelism = parsed["p"]
            if (memory == null || iterations == null || parallelism == null) {
                return null
            }
            return Params(memory, iterations, parallelism)
        }
    }
}
