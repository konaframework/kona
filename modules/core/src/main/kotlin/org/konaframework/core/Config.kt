package org.konaframework.core

/***********************************************************************************************************************
 * Dot-notation config repository backed by nested maps.
 *
 * `set("app.debug", true)` creates `{app: {debug: true}}`; `get("app")` returns the nested map.
 * Application settings come from executable [ConfigFile] modules and [Env], not from JSON files.
 *
 * Example usage:
 * ```kotlin
 * val config = Config()
 * config.set("app.debug", true)
 * config.set("app.port", 8080)
 * val debug = config.getBoolean("app.debug") // true
 * val port = config.getInt("app.port") // 8080
 * val missing = config.getString("app.missing", "default") // "default"
 * val all = config.all() // {app: {debug: true, port: 8080}}
 * ```
 * ***************************************************************************************************************
 */
class Config {
    /** Backing store for config items, using a linked map to preserve insertion order. */
    private val items = linkedMapOf<String, Any?>()

    /** Sets a config value at the given dot-notated key. Creates nested maps as needed. */
    @Synchronized
    fun set(key: String, value: Any?) {
        val segments = key.split('.')
        var node: MutableMap<String, Any?> = items
        for (segment in segments.dropLast(1)) {
            node = node.childMap(segment)
        }
        node[segments.last()] = normalize(value)
    }

    /** Checks if a config value exists at the given dot-notated key. */
    @Synchronized
    fun has(key: String): Boolean = lookup(key) !== MISSING

    /***************************************************************************************************************************
     * Gets a config value at the given dot-notated key, or returns [default] if not found.
     * The return type is inferred from the caller's context.
     * Use `get<Any>(key)` to get the raw value without casting.
     * Use `getString(key)`, `getInt(key)`, or `getBoolean(key)` for type-specific retrieval.
     *
     * @JvmOverloads allows calling this method with or without the default value.
     * @Synchronized ensures thread safety when accessing the config items.
     * @Suppress("UNCHECKED_CAST") suppresses unchecked cast warnings when casting the value to the expected type.
     * ***************************************************************************************************************
     */
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

    /*****************************************************************************************************************
     * Type-specific getters for convenience. These methods call the generic `get` method and cast the result to the expected type.
     * ***************************************************************************************************************
     */
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

    /** Returns a mutable child map for the given segment, creating it if it doesn't exist. */
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

    /** Normalizes a value by converting nested maps to linked maps with string keys. */
    private fun normalize(value: Any?): Any? {
        if (value !is Map<*, *>) {
            return value
        }
        val copy = linkedMapOf<String, Any?>()
        value.forEach { (k, v) -> copy[k.toString()] = normalize(v) }
        return copy
    }

    /** Deep copies a map, recursively copying nested maps. */
    @Suppress("UNCHECKED_CAST")
    private fun deepCopy(map: Map<String, Any?>): Map<String, Any?> =
        map.mapValues { (_, v) -> if (v is Map<*, *>) deepCopy(v as Map<String, Any?>) else v }

    /** Companion object for static utility methods. */
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
