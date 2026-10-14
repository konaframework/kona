package dev.kona.brew

/**
 * Wraps component HTML in a Brew root element with snapshot attributes.
 */
object BrewRenderer {
    @JvmStatic
    fun render(name: String, component: Component): String =
        render(name, component, assignId = true, callMount = true)

    @JvmStatic
    fun render(
        name: String,
        component: Component,
        assignId: Boolean,
        callMount: Boolean,
    ): String {
        if (assignId && component.brewId.isBlank()) {
            component.brewId = "brew-" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        }
        if (callMount) {
            component.mount()
        }
        val inner = component.render()
        val snapshot = BrewSnapshot.encode(name, component)
        val id = component.brewId
        return """<div data-brew="$snapshot" data-brew-name="$name" id="$id">$inner</div>"""
    }
}
