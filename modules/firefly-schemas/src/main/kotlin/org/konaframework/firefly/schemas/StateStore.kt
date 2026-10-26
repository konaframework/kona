package org.konaframework.firefly.schemas

import org.konaframework.firefly.support.FireflyException

class StateStore {
    private val root = LinkedHashMap<String, Any?>()

    fun contains(path: String): Boolean {
        val parts = parts(path)
        var current: Any? = root
        var index = 0
        while (index < parts.size) {
            if (current !is MutableMap<*, *>) return false
            if (!current.containsKey(parts[index])) return false
            current = current[parts[index]]
            index += 1
        }
        return true
    }

    fun read(path: String): Any? {
        val parts = parts(path)
        var current: Any? = root
        for (part in parts) {
            if (current !is Map<*, *>) return null
            current = current[part]
        }
        return current
    }

    fun write(path: String, value: Any?) {
        val parts = parts(path)
        var current: MutableMap<String, Any?> = root
        var index = 0
        while (index < parts.size - 1) {
            val key = parts[index]
            val next = current[key]
            if (next is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                current = next as MutableMap<String, Any?>
            } else {
                val created = LinkedHashMap<String, Any?>()
                current[key] = created
                current = created
            }
            index += 1
        }
        current[parts[parts.size - 1]] = value
    }

    fun snapshot(): Map<String, Any?> = LinkedHashMap(root)

    private fun parts(path: String): List<String> {
        if (path.isEmpty()) throw FireflyException("State path is empty")
        return path.split('.')
    }
}

fun interface StateHook {
    fun updated(state: Any?, old: Any?, get: Get, set: Set)
}

fun interface HydratedHook {
    fun hydrated(state: Any?, get: Get, set: Set)
}

class Get internal constructor(private val lookup: (String) -> Any?) {
    fun get(path: String): Any? = lookup(path)
}

class Set internal constructor(private val write: (String, Any?, Boolean) -> Unit) {
    fun set(path: String, value: Any?) {
        write(path, value, true)
    }

    fun set(path: String, value: Any?, shouldCallUpdatedHooks: Boolean) {
        write(path, value, shouldCallUpdatedHooks)
    }
}
