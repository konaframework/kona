package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Password hashing (`config/hashing.php` shape).
 *
 * Supported drivers: `bcrypt` (default), `argon2` / `argon2id`.
 */
object Hash : ConfigFile {
    override fun key(): String = "hash"

    override fun config(): Map<String, Any?> {
        val rounds = Env.getOrDefault("BCRYPT_ROUNDS", "12").toIntOrNull() ?: 12
        val memory = Env.getOrDefault("ARGON2_MEMORY", "65536").toIntOrNull() ?: 65536
        val time = Env.getOrDefault("ARGON2_TIME", "4").toIntOrNull() ?: 4
        val threads = Env.getOrDefault("ARGON2_THREADS", "3").toIntOrNull() ?: 3
        return mapOf(
            /*
            |--------------------------------------------------------------------------
            | Default Hash Driver
            |--------------------------------------------------------------------------
            |
            | This option controls the default hash driver that will be used to hash
            | passwords for your application. The bcrypt algorithm is a great default;
            | Argon2id is available when you need a memory-hard alternative.
            |
            | Supported: "bcrypt", "argon2", "argon2id"
            |
            */
            "driver" to Env.getOrDefault("HASH_DRIVER", "bcrypt"),

            "rounds" to rounds,

            /*
            |--------------------------------------------------------------------------
            | Bcrypt Options
            |--------------------------------------------------------------------------
            |
            | Here you may specify the configuration options that should be used when
            | passwords are hashed using the Bcrypt algorithm. This will allow you
            | to control the amount of time it takes to hash the given password.
            |
            */
            "bcrypt" to mapOf(
                "rounds" to rounds,
                "verify" to true,
            ),

            /*
            |--------------------------------------------------------------------------
            | Argon Options
            |--------------------------------------------------------------------------
            |
            | Here you may specify the configuration options that should be used when
            | passwords are hashed using the Argon algorithm. These values are used
            | by HashManager (`memory`, `iterations`, `parallelism`).
            |
            */
            "argon2" to mapOf(
                "memory" to memory,
                "time" to time,
                "threads" to threads,
                "iterations" to time,
                "parallelism" to threads,
                "verify" to true,
            ),

            "rehash_on_login" to true,
        )
    }
}
