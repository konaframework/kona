package org.konaframework.support.collection

/**
 * Runs one collection operation against a property (`get`) or a no-arg method (`call`) on each item.
 *
 * Operations: `map`, `filter`, `reject`, `each`, `sum`, `every`, `first`.
 * `map`, `filter`, and `reject` stay lazy when this proxy was opened from a [LazyCollection].
 */
class HigherOrderCollectionProxy<T>(
    private val source: Iterable<T>,
    private val operation: String,
    private val lazy: Boolean,
) {
    /** Reads a map key, `get`/`is` accessor, or field named [key] on each item. */
    fun get(key: String): Any? = apply { readProperty(it, key) }

    /** Invokes a public no-arg method named [method] on each item. */
    fun call(method: String): Any? = apply { invokeMethod(it, method) }

    private fun apply(read: (T) -> Any?): Any? =
        when (operation) {
            "map" ->
                if (lazy) {
                    LazyCollection.of(source).map(read)
                } else {
                    Collection.of(source).map(read)
                }
            "filter" ->
                if (lazy) {
                    LazyCollection.of(source).filter { truthy(read(it)) }
                } else {
                    Collection.of(source).filter { truthy(read(it)) }
                }
            "reject" ->
                if (lazy) {
                    LazyCollection.of(source).reject { truthy(read(it)) }
                } else {
                    Collection.of(source).reject { truthy(read(it)) }
                }
            "each" -> {
                for (item in source) {
                    read(item)
                }
                source
            }
            "sum" -> {
                var total = 0.0
                for (item in source) {
                    val value = read(item)
                    if (value is Number) {
                        total += value.toDouble()
                    }
                }
                total
            }
            "every" -> {
                var all = true
                for (item in source) {
                    if (!truthy(read(item))) {
                        all = false
                        break
                    }
                }
                all
            }
            "first" -> {
                var found: T? = null
                for (item in source) {
                    if (truthy(read(item))) {
                        found = item
                        break
                    }
                }
                found
            }
            else -> throw IllegalArgumentException("Unknown higher-order operation [$operation]")
        }
}

internal fun accessorSuffix(key: String): String {
    if (key.isEmpty()) {
        return ""
    }
    return key[0].uppercaseChar() + key.substring(1)
}

internal fun readProperty(item: Any?, key: String): Any? {
    if (item == null) {
        return null
    }
    if (item is Map<*, *>) {
        return item[key]
    }
    val suffix = accessorSuffix(key)
    val getter = item.javaClass.methods.firstOrNull { method ->
        method.parameterCount == 0 &&
            (method.name == "get$suffix" || method.name == "is$suffix" || method.name == key)
    }
    if (getter != null) {
        return getter.invoke(item)
    }
    val field = item.javaClass.declaredFields.firstOrNull { it.name == key }
    if (field != null) {
        field.isAccessible = true
        return field.get(item)
    }
    return null
}

internal fun invokeMethod(item: Any?, name: String): Any? {
    if (item == null) {
        return null
    }
    val method = item.javaClass.methods.firstOrNull { candidate ->
        candidate.parameterCount == 0 && candidate.name == name
    }
    if (method == null) {
        throw IllegalArgumentException("No no-arg method [$name] on [${item.javaClass.name}]")
    }
    return method.invoke(item)
}

internal fun truthy(value: Any?): Boolean =
    when (value) {
        null -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is CharSequence -> value.isNotEmpty()
        is Iterable<*> -> value.iterator().hasNext()
        is Array<*> -> value.isNotEmpty()
        else -> true
    }
