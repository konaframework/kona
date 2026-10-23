package org.konaframework.wire

/**
 * Custom value object that can live in component state (`Livewire\Wireable`).
 *
 * Implementations serialize themselves with [toWire] and expose a static factory
 * `fromWire(Object)` (Kotlin: `@JvmStatic fun fromWire(value: Any?)` in a companion object).
 * `fromBrew(Object)` remains a deprecated factory name.
 */
interface Wireable {
    fun toWire(): Any?
}
