package dev.kona.support.helpers

/**
 * Array / map helpers (Laravel `Arr` shape).
 */
object Arr {
    @JvmStatic
    @JvmOverloads
    fun get(array: Map<String, Any?>, key: String, default: Any? = null): Any? {
        if (!key.contains('.')) {
            return if (array.containsKey(key)) array[key] else default
        }
        var current: Any? = array
        for (segment in key.split('.')) {
            val map = current as? Map<*, *> ?: return default
            if (!map.containsKey(segment)) {
                return default
            }
            current = map[segment]
        }
        return current
    }

    @JvmStatic
    fun has(array: Map<String, Any?>, key: String): Boolean {
        if (!key.contains('.')) {
            return array.containsKey(key)
        }
        var current: Any? = array
        for (segment in key.split('.')) {
            val map = current as? Map<*, *> ?: return false
            if (!map.containsKey(segment)) {
                return false
            }
            current = map[segment]
        }
        return true
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun set(array: MutableMap<String, Any?>, key: String, value: Any?): MutableMap<String, Any?> {
        if (!key.contains('.')) {
            array[key] = value
            return array
        }
        val parts = key.split('.')
        var current: MutableMap<String, Any?> = array
        for (i in 0 until parts.lastIndex) {
            val part = parts[i]
            val next = current[part]
            if (next is MutableMap<*, *>) {
                current = next as MutableMap<String, Any?>
            } else {
                val created = linkedMapOf<String, Any?>()
                current[part] = created
                current = created
            }
        }
        current[parts.last()] = value
        return array
    }

    @JvmStatic
    fun only(array: Map<String, Any?>, keys: Collection<String>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (key in keys) {
            if (array.containsKey(key)) {
                result[key] = array[key]
            }
        }
        return result
    }

    @JvmStatic
    fun except(array: Map<String, Any?>, keys: Collection<String>): Map<String, Any?> {
        val excluded = keys.toSet()
        return array.filterKeys { it !in excluded }
    }

    @JvmStatic
    fun first(array: Iterable<*>): Any? = array.firstOrNull()

    @JvmStatic
    fun last(array: Iterable<*>): Any? = array.lastOrNull()

    @JvmStatic
    fun wrap(value: Any?): List<Any?> =
        when (value) {
            null -> emptyList()
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> listOf(value)
        }

    @JvmStatic
    fun flatten(array: Iterable<*>): List<Any?> {
        val result = mutableListOf<Any?>()
        for (item in array) {
            when (item) {
                is Iterable<*> -> result.addAll(flatten(item))
                is Array<*> -> result.addAll(flatten(item.toList()))
                else -> result.add(item)
            }
        }
        return result
    }

    @JvmStatic
    fun forget(array: MutableMap<String, Any?>, key: String): MutableMap<String, Any?> {
        if (!key.contains('.')) {
            array.remove(key)
            return array
        }
        val parts = key.split('.')
        var current: Any? = array
        for (i in 0 until parts.lastIndex) {
            current = (current as? Map<*, *>)?.get(parts[i]) ?: return array
        }
        val parent = current as? MutableMap<*, *> ?: return array
        try {
            @Suppress("UNCHECKED_CAST")
            (parent as MutableMap<String, Any?>).remove(parts.last())
        } catch (_: UnsupportedOperationException) {
            return array
        }
        return array
    }

    @JvmStatic
    @JvmOverloads
    fun pluck(array: Iterable<Map<String, Any?>>, value: String, key: String? = null): Any {
        if (key == null) {
            return array.map { get(it, value) }
        }
        val out = linkedMapOf<Any?, Any?>()
        for (row in array) {
            val k = get(row, key) ?: continue
            out[k] = get(row, value)
        }
        return out
    }

    /** Flatten a nested map into dot notation keys. */
    @JvmStatic
    fun dot(array: Map<String, Any?>, prepend: String = ""): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        for ((k, v) in array) {
            val key = if (prepend.isEmpty()) k else "$prepend.$k"
            if (v is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                out.putAll(dot(v as Map<String, Any?>, key))
            } else {
                out[key] = v
            }
        }
        return out
    }
}
