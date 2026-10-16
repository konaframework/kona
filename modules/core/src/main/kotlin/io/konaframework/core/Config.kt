package io.konaframework.core

/**
 * Dot-notation config repository backed by nested maps.
 *
 * `set("app.debug", true)` creates `{app: {debug: true}}`; `get("app")` returns the nested map.
 * Application settings come from executable [ConfigFile] modules and [Env], not from JSON files.
 */
class Config {
    private val items = linkedMapOf<String, Any?>()

    @Synchronized
    fun set(key: String, value: Any?) {
        val segments = key.split('.')
        var node: MutableMap<String, Any?> = items
        for (segment in segments.dropLast(1)) {
            node = node.childMap(segment)
        }
        node[segments.last()] = normalize(value)
    }

    @Synchronized
    fun has(key: String): Boolean = lookup(key) !== MISSING

    @JvmOverloads
    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, default: T? = null): T? {
        val value = lookup(key)
        if (value === MISSING) {
            return default
        }
        return value as T?
    }

    fun getString(key: String): String? = get<Any>(key)?.toString()

    fun getString(key: String, default: String): String = getString(key) ?: default

    fun getInt(key: String): Int? = when (val value = get<Any>(key)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }

    fun getInt(key: String, default: Int): Int = getInt(key) ?: default

    fun getBoolean(key: String): Boolean? = toBoolean(get<Any>(key))

    fun getBoolean(key: String, default: Boolean): Boolean = getBoolean(key) ?: default

    /** Deep copy of all items as nested maps. */
    @Synchronized
    fun all(): Map<String, Any?> = deepCopy(items)

    /** Merges [map] under [prefix] (empty prefix merges at the root). */
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

    /** Merges a [ConfigFile] under its [ConfigFile.key]. */
    fun load(file: ConfigFile) {
        loadMap(file.key(), file.config())
    }

    private fun lookup(key: String): Any? {
        var current: Any? = items
        for (segment in key.split('.')) {
            if (current !is Map<*, *> || !current.containsKey(segment)) {
                return MISSING
            }
            current = current[segment]
        }
        return current
    }

    @Suppress("UNCHECKED_CAST")
    private fun MutableMap<String, Any?>.childMap(segment: String): MutableMap<String, Any?> {
        val existing = this[segment]
        if (existing is MutableMap<*, *>) {
            return existing as MutableMap<String, Any?>
        }
        val created = linkedMapOf<String, Any?>()
        this[segment] = created
        return created
    }

    private fun normalize(value: Any?): Any? {
        if (value !is Map<*, *>) {
            return value
        }
        val copy = linkedMapOf<String, Any?>()
        value.forEach { (k, v) -> copy[k.toString()] = normalize(v) }
        return copy
    }

    @Suppress("UNCHECKED_CAST")
    private fun deepCopy(map: Map<String, Any?>): Map<String, Any?> =
        map.mapValues { (_, v) -> if (v is Map<*, *>) deepCopy(v as Map<String, Any?>) else v }

    companion object {
        private val MISSING = Any()

        /** Boolean coercion: true/1/yes/on and false/0/no/off/"" (case-insensitive). */
        @JvmStatic
        fun toBoolean(value: Any?): Boolean? = when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off", "" -> false
                else -> null
            }
            else -> null
        }
    }
}
