package app.wire

import org.konaframework.wire.Component
import org.konaframework.wire.Lazy
import org.konaframework.wire.Modelable
import org.konaframework.wire.Reactive

/** Nested child used by [Dashboard] — reactive title, modelable value, island body. */
class Panel : Component() {
    @field:Reactive
    var title: String = "Panel"

    @field:Modelable
    var value: String = ""

    fun save() {
        dispatch("saved", mapOf("label" to title))
    }

    override fun template() = """
        <div class="card">
            <h3>{{ title }}</h3>
            <input wire:model="value" />
            <button type="button" wire:click="save">Save</button>
            <p>value={{ value }}</p>
        </div>
    """.trimIndent()
}

/** Parent that nests children, slots, an island, and a lazy panel. */
class Dashboard : Component() {
    var heard = ""

    fun onPanelSaved(label: String = "") {
        heard = label.ifBlank { "saved" }
    }

    override fun template() = """
        <div class="card">
            <h2>Dashboard</h2>
            <p>heard={{ heard }}</p>
            @island('stats')
            <p>Island stats</p>
            @endisland
            <wire:panel wire:key="main" title="Main" @saved="onPanelSaved" />
            <wire:lazy-panel lazy />
            {{ slots.get('default') }}
        </div>
    """.trimIndent()
}

@Lazy
class LazyPanel : Component() {
    override fun placeholder() = "<em>Loading panel…</em>"
    override fun template() = "<div class=\"card\">Lazy ready</div>"
}
