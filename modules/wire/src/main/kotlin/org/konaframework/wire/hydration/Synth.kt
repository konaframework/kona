package org.konaframework.wire.hydration

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * Property synthesizer (`Livewire\Mechanisms\HandleComponents\Synthesizers\Synth`).
 *
 * Non-scalar values travel as tuples `[value, {"s": key, …meta}]`. Synths also implement [get]/[set]/[unset] so
 * `wire:model="todos.0.title"` can update nested data.
 */
abstract class Synth {
    /** Short key written to `meta.s` (e.g. `arr`, `enm`). */
    abstract val key: String

    /** Whether this synth handles the runtime [value]. */
    abstract fun match(value: Any): Boolean

    /** Dehydrate into a JSON-safe value; return extra metadata (without `s`). */
    abstract fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>>

    /** Rebuild a value from its dehydrated form. [type] is the declared target type when known. */
    abstract fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any?

    open fun get(target: Any, key: String): Any? =
        throw UnsupportedOperationException("Synth [${this.key}] does not support nested reads")

    /** Set [key] on [target]; returns the (possibly new) target so immutable containers are supported. */
    open fun set(target: Any, key: String, value: Any?): Any =
        throw UnsupportedOperationException("Synth [${this.key}] does not support nested writes")

    open fun unset(target: Any, key: String): Any =
        throw UnsupportedOperationException("Synth [${this.key}] does not support removing keys")

    /** Declared type of the child at [key] given the container's declared [type]. */
    open fun childType(type: Type?, target: Any?, key: String): Type? = null

    companion object {
        /** Type argument [index] of a parameterized [type] (wildcards resolved to their upper bound). */
        @JvmStatic
        fun typeArgument(type: Type?, index: Int): Type? {
            val p = type as? ParameterizedType ?: return null
            val arg = p.actualTypeArguments.getOrNull(index) ?: return null
            return if (arg is WildcardType) arg.upperBounds.first() else arg
        }

        @JvmStatic
        fun rawClass(type: Type?): Class<*>? = when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as Class<*>
            is WildcardType -> rawClass(type.upperBounds.first())
            else -> null
        }
    }
}

/** Recursion handle passed to synths. */
class SynthContext internal constructor(
    private val synths: Synthesizers,
    val path: String,
) {
    fun dehydrateChild(key: String, value: Any?): Any? = synths.dehydrate(value, child(key))

    fun hydrateChild(key: String, value: Any?, type: Type?): Any? = synths.hydrate(value, child(key), type)

    fun child(key: String): SynthContext = SynthContext(synths, if (path.isEmpty()) key else "$path.$key")

    fun policy(): SecurityPolicy = synths.policy
}
