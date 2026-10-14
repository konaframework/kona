package dev.kona.auth.hashing

/**
 * Hash manager with named drivers (Laravel HashManager shape).
 */
class HashManager @JvmOverloads constructor(
    defaultDriver: String = "bcrypt",
    private val defaultRounds: Int = BcryptHasher.DEFAULT_ROUNDS,
) {
    private val drivers = linkedMapOf<String, Hasher>()
    private var defaultName: String = defaultDriver

    init {
        extend("bcrypt", BcryptHasher(defaultRounds))
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
