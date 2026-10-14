package hello

import dev.kona.concurrency.Concurrency
import dev.kona.drip.Drip
import dev.kona.process.Process
import java.util.concurrent.Callable

object M9Demo {
    fun run(): List<String> {
        val process = Process.run("sh", "-c", "echo process-ok")
        val mapped = Concurrency.map(listOf(1, 2)) { it * 10 }
        val ran = Concurrency.run(listOf(Callable { "a" }, Callable { "b" }))
        val drip = Drip() // constructed; outbound optional in sample
        return listOf(
            "process=${process.successful()} ${process.output.trim()}",
            "map=$mapped",
            "run=$ran",
            "drip=${drip.javaClass.simpleName}",
        )
    }
}
