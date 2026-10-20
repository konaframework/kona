package org.konaframework.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Environment access with optional `.env` file loading (Laravel shape:
 * real process environment wins over `.env`).
 */
object Env {
    private val values = linkedMapOf<String, String>()

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

    @JvmStatic
    fun set(key: String, value: String) {
        values[key] = value
    }

    @JvmStatic
    fun get(key: String, default: String? = null): String? {
        System.getenv(key)?.let { return it }
        return values[key] ?: default
    }

    @JvmStatic
    fun getOrDefault(key: String, default: String): String = get(key) ?: default

    /**
     * Comma-separated list from [key] (Laravel `explode(',', env(...))` shape).
     * Empty segments after trim are dropped.
     */
    @JvmStatic
    @JvmOverloads
    fun csv(key: String, default: String = ""): List<String> =
        getOrDefault(key, default).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    @JvmStatic
    fun require(key: String): String =
        get(key) ?: throw IllegalStateException("Missing environment variable [$key]")

    @JvmStatic
    fun clear() {
        values.clear()
    }
}
