package io.konaframework.session

/**
 * Mutable session bound to a [SessionStore].
 *
 * Request attribute [StartSession.ATTRIBUTE] (`"session"`) holds this instance so
 * CSRF middleware and auth session bags resolve the same attribute key.
 */
class StoreSession @JvmOverloads constructor(
    initialId: String,
    private val store: SessionStore,
    private val attributes: MutableMap<String, Any?> = linkedMapOf(),
) : Session {
    override var id: String = initialId
        private set

    @Volatile
    var exists: Boolean = false
        private set

    fun start(exists: Boolean): StoreSession {
        this.exists = exists
        ageFlashData()
        return this
    }

    override fun get(key: String): Any? = attributes[key]

    override fun put(key: String, value: Any?): Session {
        attributes[key] = value
        return this
    }

    override fun forget(key: String): Session {
        attributes.remove(key)
        return this
    }

    override fun flash(key: String, value: Any?): Session {
        put(key, value)
        val neu = when (val raw = attributes[FLASH_NEW]) {
            is List<*> -> raw.map { it.toString() }.toMutableList()
            else -> mutableListOf()
        }
        if (!neu.contains(key)) {
            neu.add(key)
        }
        attributes[FLASH_NEW] = neu
        return this
    }

    override fun flashInput(input: Map<String, String>): Session {
        flash(OLD_INPUT, LinkedHashMap(input))
        return this
    }

    override fun old(key: String, default: String?): String? {
        val bag = attributes[OLD_INPUT] as? Map<*, *> ?: return default
        if (!bag.containsKey(key)) {
            return default
        }
        val value = bag[key]
        return if (value == null) default else value.toString()
    }

    override fun oldInput(): Map<String, String> {
        val bag = attributes[OLD_INPUT] as? Map<*, *> ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        for ((k, v) in bag.entries) {
            out[k.toString()] = if (v == null) "" else v.toString()
        }
        return out
    }

    override fun previousUrl(): String? {
        val value = attributes[PREVIOUS_URL] ?: return null
        return value.toString()
    }

    override fun setPreviousUrl(url: String): Session {
        put(PREVIOUS_URL, url)
        return this
    }

    override fun pull(key: String, default: Any?): Any? {
        if (!attributes.containsKey(key)) {
            return default
        }
        return attributes.remove(key)
    }

    override fun regenerate(destroy: Boolean): Session {
        val previous = id
        id = SessionId.generate()
        if (destroy) {
            store.destroy(previous)
        }
        exists = false
        return this
    }

    override fun invalidate(): Session {
        attributes.clear()
        return regenerate(destroy = true)
    }

    override fun all(): Map<String, Any?> = LinkedHashMap(attributes)

    override fun ageFlashData(): Session {
        val old = when (val raw = attributes[FLASH_OLD]) {
            is List<*> -> raw.map { it.toString() }
            else -> emptyList()
        }
        for (key in old) {
            attributes.remove(key)
        }
        val neu = when (val raw = attributes[FLASH_NEW]) {
            is List<*> -> raw.map { it.toString() }
            else -> emptyList()
        }
        if (neu.isEmpty()) {
            attributes.remove(FLASH_OLD)
            attributes.remove(FLASH_NEW)
        } else {
            attributes[FLASH_OLD] = neu.toMutableList()
            attributes[FLASH_NEW] = mutableListOf<String>()
        }
        return this
    }

    fun save(lifetimeMinutes: Int) {
        store.write(id, LinkedHashMap(attributes), lifetimeMinutes)
        exists = true
    }

    companion object {
        const val FLASH_NEW: String = "_flash.new"
        const val FLASH_OLD: String = "_flash.old"
        const val OLD_INPUT: String = "_old_input"
        const val PREVIOUS_URL: String = "_previous.url"
        const val ERRORS: String = "errors"
    }
}
