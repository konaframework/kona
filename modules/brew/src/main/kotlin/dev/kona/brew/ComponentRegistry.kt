package dev.kona.brew

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * Maps component names to factories (e.g. `"counter"` → `{ Counter() }`).
 */
class ComponentRegistry {
    private val factories = ConcurrentHashMap<String, () -> Component>()

    @JvmSynthetic
    fun register(name: String, factory: () -> Component): ComponentRegistry {
        factories[name] = factory
        return this
    }

    /** Java-friendly registration. */
    fun register(name: String, factory: Supplier<out Component>): ComponentRegistry {
        factories[name] = { factory.get() }
        return this
    }

    fun resolve(name: String): Component {
        val factory = factories[name]
            ?: throw BrewException("Unknown Brew component: $name")
        return factory()
    }

    fun contains(name: String): Boolean = factories.containsKey(name)

    fun names(): Set<String> = factories.keys.toSet()
}
