package org.konaframework.brew.features

import org.konaframework.brew.BrewManager
import org.konaframework.brew.BrewView
import org.konaframework.brew.CannotCallComputedDirectlyException
import org.konaframework.brew.Component
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.lifecycle.ReturnEarly
import org.konaframework.brew.meta.ComponentMeta
import org.konaframework.crema.CremaLazy
import java.util.concurrent.ConcurrentHashMap

/** Cache backend for `@Computed(persist = true)` / `@Computed(cache = true)`. */
interface ComputedCache {
    /** Cached value wrapper, or null when absent/expired. */
    fun get(key: String): Entry?

    fun put(key: String, value: Any?, seconds: Int)

    fun forget(key: String)

    class Entry(val value: Any?)
}

/** Process-local TTL cache (swap for a Kona cache-backed implementation in clustered deployments). */
class InMemoryComputedCache @JvmOverloads constructor(
    private val clock: () -> Long = System::currentTimeMillis,
) : ComputedCache {
    private class Slot(val value: Any?, val expiresAt: Long)

    private val slots = ConcurrentHashMap<String, Slot>()

    override fun get(key: String): ComputedCache.Entry? {
        val slot = slots[key] ?: return null
        if (slot.expiresAt <= clock()) {
            slots.remove(key)
            return null
        }
        return ComputedCache.Entry(slot.value)
    }

    override fun put(key: String, value: Any?, seconds: Int) {
        slots[key] = Slot(value, clock() + seconds * 1000L)
    }

    override fun forget(key: String) {
        slots.remove(key)
    }
}

/** `@Computed`: per-request memo, optional cross-request persistence, template access (`$name`, `$this.name`). */
class SupportComputed : ComponentHook() {
    override fun call(
        method: String,
        params: List<Any?>,
        returnEarly: ReturnEarly,
        metadata: Map<String, Any?>,
        context: ComponentContext,
    ): ((Any?) -> Any?)? {
        if (ComponentMeta.of(component).computed.containsKey(method)) {
            throw CannotCallComputedDirectlyException(method, component.name())
        }
        return null
    }

    override fun render(view: BrewView, data: MutableMap<String, Any?>): ((String) -> String?)? {
        for (name in ComponentMeta.of(component).computed.keys) {
            if (!data.containsKey(name)) data[name] = CremaLazy { value(manager, component, name) }
        }
        return null
    }

    companion object {
        @JvmStatic
        fun value(manager: BrewManager, component: Component, name: String): Any? {
            val meta = ComponentMeta.of(component).computed[name]
                ?: throw IllegalArgumentException("[$name] is not a @Computed method on ${component.javaClass.name}")
            val memo = component.state().computedMemo
            if (memo.containsKey(name)) return memo[name]
            val attribute = meta.attribute
            val key = cacheKey(component, name)
            val cached = key?.let { manager.computedCache.get(it) }
            val value = when {
                cached != null -> cached.value
                key != null -> invoke(manager, component, meta.method).also { manager.computedCache.put(key, it, attribute.seconds) }
                else -> invoke(manager, component, meta.method)
            }
            memo[name] = value
            return value
        }

        @JvmStatic
        fun forget(manager: BrewManager, component: Component, name: String) {
            component.state().computedMemo.remove(name)
            cacheKey(component, name)?.let { manager.computedCache.forget(it) }
        }

        private fun invoke(manager: BrewManager, component: Component, method: java.lang.reflect.Method): Any? =
            manager.invoker.call(component, method, emptyList())

        private fun cacheKey(component: Component, name: String): String? {
            val meta = ComponentMeta.of(component).computed[name] ?: return null
            val attribute = meta.attribute
            return when {
                attribute.cache -> attribute.key.ifEmpty { "brew.computed.${component.javaClass.name}.$name" }
                attribute.persist -> "brew.computed.${component.id()}.$name"
                else -> null
            }
        }
    }
}
