package dev.kona.core

/**
 * Dot-notation config repository (Laravel Config shape).
 */
class Config {
    private val items = linkedMapOf<String, Any?>()

    fun set(key: String, value: Any?) {
        items[key] = value
    }

    fun has(key: String): Boolean = items.containsKey(key)

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, default: T? = null): T? {
        if (items.containsKey(key)) {
            return items[key] as T?
        }
        return default
    }

    fun getString(key: String, default: String? = null): String? = get(key, default)

    fun all(): Map<String, Any?> = items.toMap()

    fun loadMap(prefix: String, map: Map<String, Any?>) {
        map.forEach { (k, v) ->
            val full = if (prefix.isEmpty()) k else "$prefix.$k"
            when (v) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    loadMap(full, v as Map<String, Any?>)
                }
                else -> set(full, v)
            }
        }
    }
}
