package org.konaframework.brew.bench

import org.konaframework.brew.Component
import org.konaframework.brew.attr
import org.konaframework.brew.brew
import org.konaframework.brew.call
import org.konaframework.brew.fixtures.Counter
import org.konaframework.brew.snapshotOf
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.view.HtmlRoot
import org.konaframework.support.console.ConsoleIO

class BenchRows : Component() {
    var rows: MutableList<Map<String, Any?>> = MutableList(100) { mapOf("id" to it, "title" to "Row $it", "done" to (it % 3 == 0)) }

    fun toggle(index: Int) {
        val row = rows[index]
        rows[index] = row + ("done" to !(row["done"] as Boolean))
    }

    override fun template() = """
        <ul>
        @foreach(rows as row)
            <li wire:key="{{ row.id }}"><input type="checkbox" @if(row.done) checked @endif> {{ row.title }}</li>
        @endforeach
        </ul>
    """.trimIndent()
}

/**
 * Server hot-path baseline: `./gradlew :kona-brew:bench`. Measures steady-state mount and update round-trips
 * (hydrate → checksum → call → render → dehydrate) on one thread after JIT warm-up.
 */
object BrewBench {
    private fun measure(io: ConsoleIO, label: String, iterations: Int, block: () -> Unit) {
        repeat(iterations / 2) { block() }
        val start = System.nanoTime()
        repeat(iterations) { block() }
        val micros = (System.nanoTime() - start) / 1_000.0 / iterations
        io.twoColumn(label, "%.1f µs/op · %,d ops/s".format(micros, (1_000_000 / micros).toLong()))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val io = ConsoleIO()
        val manager = brew()
        manager.component("counter", Counter::class.java)
        manager.component("rows", BenchRows::class.java)
        val iterations = args.firstOrNull()?.toInt() ?: 20_000

        io.title("Brew server baseline ($iterations iterations)")
        measure(io, "mount counter", iterations) { manager.mount("counter") }
        measure(io, "mount 100-row list", iterations / 10) { manager.mount("rows") }

        val counter = snapshotOf(manager.mount("counter"))
        measure(io, "update counter (increment)", iterations) {
            manager.update(counter, emptyMap(), listOf(call("increment")))
        }
        val rows = snapshotOf(manager.mount("rows"))
        measure(io, "update 100-row list (toggle)", iterations / 10) {
            manager.update(rows, emptyMap(), listOf(call("toggle", 5)))
        }
        if (System.getenv("BREW_BENCH_PROFILE") != null) {
            val engine = manager.views()
            val src = BenchRows().template()
            val data = mapOf("rows" to BenchRows().rows)
            measure(io, "crema render only", iterations / 10) { engine.renderSource(src, data) }
            val list = manager.newComponent("rows")
            val rowsHtml = engine.renderSource(src, data)
            measure(io, "root attrs only", iterations / 10) { HtmlRoot.insertAttributes(rowsHtml, mapOf("wire:snapshot" to "x", "wire:id" to "y"), "rows") }
            measure(io, "snapshot only", iterations / 10) { manager.snapshot(list, ComponentContext(list, mounting = true)) }
        }
        val html = manager.mount("counter")
        io.twoColumn("counter snapshot bytes", attr(html, "wire:snapshot").length.toString())
    }
}
