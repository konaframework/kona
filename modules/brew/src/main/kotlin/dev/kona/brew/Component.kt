package dev.kona.brew

/**
 * Stateful server component. Public instance fields (Java `public` fields or Kotlin `@JvmField`)
 * are serialized into the Brew snapshot. Action methods invoked from the client must be **public**.
 */
abstract class Component {
    /** Stable DOM id for this instance (`brew-…`). Assigned on first render when blank. */
    @JvmField
    var brewId: String = ""

    open fun mount() {}

    /** HTML fragment for this component (may call Crema). */
    abstract fun render(): String

    /**
     * Apply client-synced property values (from `wire:model` / `data-brew-model`).
     * Default implementation writes known state keys via [BrewState].
     */
    open fun sync(values: Map<String, Any?>) {
        BrewState.write(this, values)
    }
}
