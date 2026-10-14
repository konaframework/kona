package dev.kona.auth.session

import dev.kona.http.Request

/**
 * Minimal session contract for auth without hard-depending on `kona-session`.
 *
 * Sibling session module (or tests) should place a [SessionBag] — or a
 * `MutableMap<String, Any?>` — on the request under attribute [ATTRIBUTE_KEY].
 */
interface SessionBag {
    fun get(key: String): Any?

    fun put(key: String, value: Any?)

    fun forget(key: String)

    fun has(key: String): Boolean = get(key) != null
}

/** Map-backed session used when the request attribute is a [MutableMap]. */
class MapSessionBag(
    private val store: MutableMap<String, Any?>,
) : SessionBag {
    override fun get(key: String): Any? = store[key]

    override fun put(key: String, value: Any?) {
        store[key] = value
    }

    override fun forget(key: String) {
        store.remove(key)
    }
}

object SessionAttributes {
    const val ATTRIBUTE_KEY: String = "session"

    /**
     * Resolves a [SessionBag] from `request.getAttribute("session")`.
     * Accepts [SessionBag] or `MutableMap<String, Any?>`.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun resolve(request: Request): SessionBag? {
        return when (val raw = request.getAttribute(ATTRIBUTE_KEY)) {
            null -> null
            is SessionBag -> raw
            is MutableMap<*, *> -> MapSessionBag(raw as MutableMap<String, Any?>)
            else -> adaptForeignSession(raw)
        }
    }

    /**
     * Adapts `kona-session` Session (or any object with get/put/forget) without a hard module dependency.
     */
    @JvmStatic
    fun adaptForeignSession(raw: Any): SessionBag? {
        val get = findMethod(raw, "get", 1) ?: return null
        val put = findMethod(raw, "put", 2) ?: return null
        val forget = findMethod(raw, "forget", 1) ?: return null
        return object : SessionBag {
            override fun get(key: String): Any? = get.invoke(raw, key)

            override fun put(key: String, value: Any?) {
                put.invoke(raw, key, value)
            }

            override fun forget(key: String) {
                forget.invoke(raw, key)
            }
        }
    }

    private fun findMethod(raw: Any, name: String, arity: Int): java.lang.reflect.Method? {
        for (method in raw.javaClass.methods) {
            if (method.name == name &&
                method.parameterCount == arity &&
                method.parameterTypes[0] == String::class.java
            ) {
                return method
            }
        }
        return null
    }

    /** Ensures a map-backed session exists on the request and returns it. */
    @JvmStatic
    fun ensure(request: Request): SessionBag {
        resolve(request)?.let { return it }
        val map = linkedMapOf<String, Any?>()
        request.setAttribute(ATTRIBUTE_KEY, map)
        return MapSessionBag(map)
    }
}
