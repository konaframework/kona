package io.konaframework.core

import java.util.function.Supplier

/**
 * Executable config module. At boot, [config] is merged under [key] into [Config]
 * (e.g. key `"app"` → `app.*`).
 *
 * Place one module per file under the language root `config/` package and register
 * them from `bootstrap.App` via [Application.Builder.configs].
 */
interface ConfigFile {
    /** Top-level config key (usually the file stem: `App` → `"app"`). */
    fun key(): String

    /** Nested values for that key. May call [Env.get] / [Env.getOrDefault]. */
    fun config(): Map<String, Any?>

    companion object {
        /** Builds a [ConfigFile] from a key and a supplier (Java-friendly). */
        @JvmStatic
        fun of(key: String, values: Supplier<out Map<String, *>>): ConfigFile =
            object : ConfigFile {
                override fun key(): String = key

                @Suppress("UNCHECKED_CAST")
                override fun config(): Map<String, Any?> = values.get() as Map<String, Any?>
            }
    }
}
