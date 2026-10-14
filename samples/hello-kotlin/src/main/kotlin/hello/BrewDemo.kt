package hello

import dev.kona.brew.Brew
import dev.kona.brew.BrewManager
import dev.kona.brew.BrewServiceProvider
import dev.kona.brew.Component
import dev.kona.core.Kona
import dev.kona.crema.CremaEngine
import dev.kona.crema.MapViewFinder
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.Request

object BrewDemo {
    class Counter : Component() {
        @JvmField
        var count: Int = 0

        fun increment() {
            count++
        }

        fun decrement() {
            count--
        }

        override fun render(): String =
            """
            <div>
              <p>Count: $count</p>
              <button data-brew-click="increment">+</button>
              <button data-brew-click="decrement">-</button>
            </div>
            """.trimIndent()
    }

    class CremaCounter(
        private val engine: CremaEngine,
    ) : Component() {
        @JvmField
        var count: Int = 0

        fun increment() {
            count++
        }

        override fun render(): String =
            Brew.view(engine, "brew.counter", mapOf("count" to count))
    }

    fun run(args: Array<String>): List<String> {
        val app = Kona.create(args)
            .providers(HttpServiceProvider(), BrewServiceProvider())
            .boot()
        val brew = app.resolve(BrewManager::class.java)
        brew.register("counter") { Counter() }
        val engine = CremaEngine(MapViewFinder().put("brew.counter", "<em>{{ count }}</em>"))
        brew.register("crema-counter") { CremaCounter(engine) }

        val html = Brew.mount("counter")
        val snapshot = Regex("""data-brew="([^"]+)"""").find(html)!!.groupValues[1]
        val updated = brew.update("counter", snapshot, "increment")
        val cremaHtml = brew.mount("crema-counter")

        val kernel = app.resolve(HttpKernel::class.java)
        val script = kernel.handle(Request.get("/brew/brew.js"))
        return listOf(
            "mount=${html.contains("Count: 0")}",
            "increment=${updated.html.contains("Count: 1")}",
            "crema=${cremaHtml.contains("<em>0</em>")}",
            "script=${script.status}",
        )
    }
}
