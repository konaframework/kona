package org.konaframework.brew

import org.konaframework.brew.lifecycle.ComponentContext

/** Outcome of mounting or updating a component: the instance, its HTML (null when skipped), snapshot, effects. */
class ComponentResult internal constructor(
    val component: Component,
    val html: String?,
    val snapshot: Map<String, Any?>,
    val context: ComponentContext,
) {
    val effects: Map<String, Any?> get() = context.effects

    /** View rendered during this round-trip (null when rendering was skipped). */
    val view: BrewView? get() = component.state().renderedView

    /** Data passed to the view, including `@Computed` entries. */
    val viewData: Map<String, Any?> get() = component.state().renderedData ?: emptyMap()
}
