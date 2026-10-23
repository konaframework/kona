package org.konaframework.core

import java.nio.file.Files
import java.nio.file.Path

/***********************************************************************************************************************
 * The Env Object
 * ---------------------------------------------------------------------------------------------------------------------
 * This object provides access to environment variables and allows loading them from a `.env` file.
 * It maintains an internal map of key-value pairs representing the environment variables.
 * The `load` method reads a `.env` file and populates the internal map with the key-value pairs found in the file.
 * The `get` method retrieves the value of an environment variable, first checking the system environment and then falling back to the internal map.
 * The `set` method allows adding or updating environment variables in the internal map.
 * The `csv` method retrieves a comma-separated list of values from an environment variable, splitting it into a list of strings.
 * Real process environment variables take precedence over values loaded from `.env` files.
 * The `require` method retrieves the value of an environment variable and throws an exception if it is not found.
 * The `clear` method clears the internal map of environment variables.
 * *********************************************************************************************************************
 */
object Env {
    private val values = linkedMapOf<String, String>()

    /**
     * Loads environment variables from a `.env` file at the specified [path].
     * Each line in the file should be in the format `KEY=VALUE`, with optional comments starting with `#`.
     * Lines starting with `export` are also supported.
     * Quoted values are unquoted, and inline comments are ignored.
     *
     * @param path The path to the `.env` file to load.
     */
    @JvmStatic
    fun load(path: Path) {
        if (!Files.isRegularFile(path)) {
            return
        }
        Files.readAllLines(path).forEach { raw ->
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEach
            }
            if (line.startsWith("export ")) {
                line = line.removePrefix("export ").trim()
            }
            val idx = line.indexOf('=')
            if (idx <= 0) {
                return@forEach
            }
            val key = line.substring(0, idx).trim()
            var value = line.substring(idx + 1).trim()
            val quoted = value.startsWith("\"") || value.startsWith("'")
            if (!quoted) {
                val hash = value.indexOf(" #")
                if (hash >= 0) {
                    value = value.substring(0, hash).trim()
                }
            }
            value = value.removeSurrounding("\"").removeSurrounding("'")
            values[key] = value
        }
    }

    /**
     * Sets an environment variable in the internal map.
     * This does not affect the actual process environment variables.
     *
     * @param key The name of the environment variable to set.
     * @param value The value to assign to the environment variable.
     */
    @JvmStatic
    fun set(key: String, value: String) {
        values[key] = value
    }

    /**
     * Retrieves the value of an environment variable.
     * It first checks the system environment variables, and if not found, it checks the internal map.
     * If the variable is not found in either place, it returns the provided [default] value.
     *
     * @param key The name of the environment variable to retrieve.
     * @param default The default value to return if the variable is not found (optional).
     * @return The value of the environment variable, or [default] if not found.
     */
    @JvmStatic
    fun get(key: String, default: String? = null): String? {
        System.getenv(key)?.let { return it }
        return values[key] ?: default
    }

    /**
     * Retrieves the value of an environment variable, returning a default value if not found.
     * This method is a convenience wrapper around [get] that ensures a non-null return value.
     *
     * @param key The name of the environment variable to retrieve.
     * @param default The default value to return if the variable is not found.
     * @return The value of the environment variable, or [default] if not found.
     */
    @JvmStatic
    fun getOrDefault(key: String, default: String): String = get(key) ?: default

    /**
     * Comma-separated list from [key] (Laravel `explode(',', env(...))` shape).
     * Empty segments after trim are dropped.
     *
     * @param key The name of the environment variable to retrieve.
     * @param default The default value to return if the variable is not found (optional).
     * @return A list of strings obtained by splitting the environment variable's value by commas.
     */
    @JvmStatic
    @JvmOverloads
    fun csv(key: String, default: String = ""): List<String> =
        getOrDefault(key, default).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Retrieves the value of an environment variable, throwing an exception if it is not found.
     * This method is useful for required configuration values that must be present for the application to function correctly.
     *
     * @param key The name of the environment variable to retrieve.
     * @return The value of the environment variable.
     * @throws IllegalStateException if the environment variable is not found.
     */
    @JvmStatic
    fun require(key: String): String =
        get(key) ?: throw IllegalStateException("Missing environment variable [$key]")

    /**
     * Clears the internal map of environment variables.
     * This does not affect the actual process environment variables.
     * Use this method with caution, as it will remove all loaded environment variables from the internal map.
     * It is primarily intended for testing purposes or resetting the environment state.
     */
    @JvmStatic
    fun clear() {
        values.clear()
    }
}
