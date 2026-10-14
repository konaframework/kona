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
}
