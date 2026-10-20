package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Password hashing ({@code config/hashing.php} shape).
 *
 * <p>Supported drivers: {@code bcrypt} (default), {@code argon2} / {@code argon2id}.
 */
public final class Hash implements ConfigFile {
    @Override
    public String key() {
        return "hash";
    }

    @Override
    public Map<String, Object> config() {
        int rounds = envInt("BCRYPT_ROUNDS", 12);
        int memory = envInt("ARGON2_MEMORY", 65536);
        int time = envInt("ARGON2_TIME", 4);
        int threads = envInt("ARGON2_THREADS", 3);

        Map<String, Object> bcrypt = new LinkedHashMap<>();
        bcrypt.put("rounds", rounds);
        bcrypt.put("verify", true);

        Map<String, Object> argon2 = new LinkedHashMap<>();
        argon2.put("memory", memory);
        argon2.put("time", time);
        argon2.put("threads", threads);
        argon2.put("iterations", time);
        argon2.put("parallelism", threads);
        argon2.put("verify", true);

        Map<String, Object> values = new LinkedHashMap<>();
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
        values.put("driver", Env.getOrDefault("HASH_DRIVER", "bcrypt"));
        values.put("rounds", rounds);

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
        values.put("bcrypt", bcrypt);

        /*
        |--------------------------------------------------------------------------
        | Argon Options
        |--------------------------------------------------------------------------
        |
        | Here you may specify the configuration options that should be used when
        | passwords are hashed using the Argon algorithm. These values are used
        | by HashManager ({@code memory}, {@code iterations}, {@code parallelism}).
        |
        */
        values.put("argon2", argon2);
        values.put("rehash_on_login", true);
        return values;
    }

    private static int envInt(String key, int fallback) {
        try {
            return Integer.parseInt(Env.getOrDefault(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
