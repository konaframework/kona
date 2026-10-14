package hello

import dev.kona.broadcasting.Broadcast
import dev.kona.broadcasting.BroadcastServiceProvider
import dev.kona.broadcasting.Channel
import dev.kona.broadcasting.ShouldBroadcast
import dev.kona.core.Kona
import dev.kona.search.Scout
import dev.kona.search.SearchServiceProvider
import dev.kona.search.Searchable

object M10Demo {
    class Ping : ShouldBroadcast {
        override fun broadcastOn() = listOf(Channel.publicChannel("demo"))
        override fun broadcastAs() = "ping"
        override fun broadcastWith() = mapOf("ok" to true)
    }

    data class Item(val id: String, val name: String) : Searchable {
        override fun searchableAs() = "items"
        override fun getScoutKey() = id
        override fun toSearchableArray() = mapOf("name" to name)
    }

    fun run(args: Array<String>): List<String> {
        val app = Kona.create(args).providers(BroadcastServiceProvider(), SearchServiceProvider()).boot()
        Broadcast.event(Ping())
        Scout.update(Item("1", "kona roast"))
        val search = Scout.search("items", "kona")
        return listOf(
            "broadcast=${Broadcast.manager().log().messages().size}",
            "search=${search.keys()}",
        )
    }
}
