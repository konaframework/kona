package app.wire

import org.konaframework.wire.Component
import org.konaframework.wire.Renderless

/** A single-file component: state, actions, and template in one class. */
class Counter : Component() {
    var count = 0

    fun mount(start: Int = 0) {
        count = start
    }

    fun increment() {
        count++
    }

    fun decrement() {
        count--
    }

    fun add(amount: Int) {
        count += amount
    }

    /** Returned to the browser; `await $wire.doubled()` resolves with it. */
    fun doubled(): Int = count * 2

    /** Updates state without re-rendering; the page keeps showing the old number until the next render. */
    @Renderless
    fun clearQuietly() {
        count = 0
    }

    override fun template() = """
        <div class="card">
            <h2>Counter</h2>
            <button wire:click="decrement">-</button>
            <output>{{ count }}</output>
            <button wire:click="increment">+</button>
            <button wire:click="add(5)">+5</button>
            <button x-data x-on:click="alert(await ${'$'}wire.doubled())">Doubled?</button>
        </div>
    """.trimIndent()
}
