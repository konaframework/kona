package org.konaframework.brew

/**
 * Custom value object that can live in component state (`Livewire\Wireable`).
 *
 * Implementations serialize themselves with [toBrew] and expose a static factory
 * `fromBrew(Object)` (Kotlin: `@JvmStatic fun fromBrew(value: Any?)` in a companion object).
 */
interface Wireable {
    fun toBrew(): Any?
}
