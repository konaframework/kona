package dev.kona.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Environment access with optional `.env` file loading (Laravel/Almasix shape).
 */
object Env {
    private val values = linkedMapOf<String, String>()

    @JvmStatic
    fun load(path: Path) {
        if (!Files.isRegularFile(path)) {
            return
        }
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEach
            }
            val idx = line.indexOf('=')
            if (idx <= 0) {
                return@forEach
            }
            val key = line.substring(0, idx).trim()
            values[key] = line.substring(idx + 1).trim()
        }
    }

    @JvmStatic
    fun set(key: String, value: String) {
        values[key] = value
    }

    @JvmStatic
    fun get(key: String, default: String? = null): String? {
        values[key]?.let { return it }
        return System.getenv(key) ?: default
    }

    @JvmStatic
    fun getOrDefault(key: String, default: String): String = get(key) ?: default

    @JvmStatic
    fun require(key: String): String =
        get(key) ?: throw IllegalStateException("Missing environment variable [$key]")

    @JvmStatic
    fun clear() {
        values.clear()
    }
}
