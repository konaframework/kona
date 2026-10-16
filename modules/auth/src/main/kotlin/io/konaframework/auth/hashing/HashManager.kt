package io.konaframework.auth.hashing

/**
 * Hash manager with named drivers (Laravel HashManager shape).
 */
class HashManager @JvmOverloads constructor(
    defaultDriver: String = "bcrypt",
    private val defaultRounds: Int = BcryptHasher.DEFAULT_ROUNDS,
    private val argon2Memory: Int = Argon2Hasher.DEFAULT_MEMORY,
    private val argon2Iterations: Int = Argon2Hasher.DEFAULT_ITERATIONS,
    private val argon2Parallelism: Int = Argon2Hasher.DEFAULT_PARALLELISM,
) {
    private val drivers = linkedMapOf<String, Hasher>()
    private var defaultName: String = defaultDriver

    init {
        extend("bcrypt", BcryptHasher(defaultRounds))
        val argon = Argon2Hasher(argon2Memory, argon2Iterations, argon2Parallelism)
        extend("argon2", argon)
        extend("argon2id", argon)
    }

    fun driver(): Hasher = driver(defaultName)

    fun driver(name: String): Hasher =
        drivers[name] ?: throw IllegalArgumentException("Hash driver [$name] is not defined")

    fun extend(name: String, hasher: Hasher): HashManager {
        drivers[name] = hasher
        return this
    }

    fun setDefaultDriver(name: String): HashManager {
        driver(name) // validate exists
        defaultName = name
        return this
    }

    fun getDefaultDriver(): String = defaultName

    fun make(value: String): String = driver().make(value)

    fun check(value: String, hashed: String): Boolean = driver().check(value, hashed)

    fun needsRehash(hashed: String): Boolean = driver().needsRehash(hashed)
}
