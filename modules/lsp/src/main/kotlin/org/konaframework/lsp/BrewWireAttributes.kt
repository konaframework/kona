package org.konaframework.lsp

/**
 * Known Wire `wire:*` HTML attributes used in Crema views.
 * Mirrors the IDEA plugin list ([kona-idea] BrewWireAttributes).
 */
object BrewWireAttributes {
    /** Base attribute names (no modifiers) — IDEA / DX contract. */
    @JvmField
    val BASE: List<String> =
        listOf(
            "wire:model",
            "wire:click",
            "wire:submit",
            "wire:loading",
            "wire:target",
            "wire:ignore",
            "wire:key",
            "wire:dirty",
            "wire:offline",
            "wire:confirm",
            "wire:poll",
            "wire:init",
            "wire:show",
            "wire:transition",
        )

    /** Common completion labels including frequent modifier variants. */
    @JvmField
    val COMPLETION_LABELS: List<String> =
        listOf(
            "wire:model",
            "wire:model.live",
            "wire:model.blur",
            "wire:model.lazy",
            "wire:model.defer",
            "wire:click",
            "wire:click.prevent",
            "wire:click.stop",
            "wire:click.async",
            "wire:click.renderless",
            "wire:click.preserve-scroll",
            "wire:submit",
            "wire:submit.prevent",
            "wire:loading",
            "wire:loading.class",
            "wire:loading.attr",
            "wire:loading.remove",
            "wire:loading.flex",
            "wire:target",
            "wire:target.except",
            "wire:ignore",
            "wire:ignore.self",
            "wire:key",
            "wire:dirty",
            "wire:dirty.class",
            "wire:dirty.remove",
            "wire:offline",
            "wire:offline.class",
            "wire:offline.attr",
            "wire:offline.remove",
            "wire:confirm",
            "wire:confirm.prompt",
            "wire:poll",
            "wire:poll.5s",
            "wire:init",
            "wire:show",
            "wire:transition",
        )

    private val BASE_SET: Set<String> = BASE.toSet()
    private val BASE_NAMES: Set<String> =
        BASE.map { it.removePrefix("wire:") }.toSet()
    private val COMPLETION_SET: Set<String> = COMPLETION_LABELS.toSet()

    @JvmStatic
    fun isKnownAttribute(raw: String): Boolean {
        val name = raw.substringBefore('=').trim()
        if (name in BASE_SET || name in COMPLETION_SET) {
            return true
        }
        if (!name.startsWith("wire:")) {
            return false
        }
        val rest = name.removePrefix("wire:")
        val base = rest.substringBefore('.')
        return base in BASE_NAMES
    }

    @JvmStatic
    fun completionsForPrefix(prefix: String): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty() || p == "wire" || p == "wire:") {
            return COMPLETION_LABELS
        }
        return COMPLETION_LABELS.filter { it.startsWith(p, ignoreCase = true) }
    }
}
