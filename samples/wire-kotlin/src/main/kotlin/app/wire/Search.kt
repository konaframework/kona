package app.wire

import org.konaframework.wire.Component
import org.konaframework.wire.Computed

/** Live search: the input syncs 300ms after typing stops and the list re-renders. */
class Search : Component() {
    var query = ""

    @Computed
    fun results(): List<String> =
        BEANS.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }

    override fun template() = """
        <div class="card">
            <h2>Search beans</h2>
            <input type="search" wire:model.live.debounce.300ms="query" placeholder="Try 'kona'">
            <ul>
            @foreach(results as bean)
                <li wire:key="{{ bean }}">{{ bean }}</li>
            @endforeach
            </ul>
        </div>
    """.trimIndent()

    companion object {
        val BEANS = listOf("Kona Extra Fancy", "Kona Peaberry", "Ethiopia Yirgacheffe", "Colombia Supremo", "Sumatra Mandheling")
    }
}
