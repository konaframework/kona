package org.konaframework.wire

/**
 * Validation error bag (`MessageBag` / Livewire `$this->getErrorBag()`).
 * Persisted in `memo.errors` and exposed to Crema as `errors` plus JS `$wire.$errors`.
 */
class ErrorBag {
    private val messages = linkedMapOf<String, MutableList<String>>()

    fun isEmpty(): Boolean = messages.isEmpty()

    fun isNotEmpty(): Boolean = messages.isNotEmpty()

    fun has(key: String): Boolean = !messages[key].isNullOrEmpty()

    fun first(key: String? = null): String? {
        if (key != null) return messages[key]?.firstOrNull()
        return messages.values.firstOrNull()?.firstOrNull()
    }

    fun get(key: String): List<String> = messages[key].orEmpty().toList()

    fun all(): Map<String, List<String>> = messages.mapValues { it.value.toList() }

    fun add(key: String, message: String): ErrorBag {
        messages.getOrPut(key) { mutableListOf() }.add(message)
        return this
    }

    fun merge(errors: Map<String, List<String>>): ErrorBag {
        for ((key, list) in errors) list.forEach { add(key, it) }
        return this
    }

    fun clear(key: String? = null): ErrorBag {
        if (key == null) messages.clear() else messages.remove(key)
        return this
    }

    /** Wire shape: `{ field: [messages…] }` for memo / effects. */
    fun toWire(): Map<String, List<String>> = all()

    companion object {
        @JvmStatic
        fun fromWire(raw: Any?): ErrorBag {
            val bag = ErrorBag()
            val map = raw as? Map<*, *> ?: return bag
            for ((k, v) in map) {
                if (k == null) continue
                val key = k.toString()
                if (v is Collection<*>) {
                    for (item in v) if (item != null) bag.add(key, item.toString())
                } else if (v != null) {
                    bag.add(key, v.toString())
                }
            }
            return bag
        }
    }
}
