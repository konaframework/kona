package dev.kona.concurrency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable

class ConcurrencyTest {
    @Test
    fun mapAndRun() {
        assertEquals(emptyList<Int>(), Concurrency.map(emptyList<Int>(), { n: Int -> n }))
        assertEquals(listOf(2, 4, 6), Concurrency.map(listOf(1, 2, 3), { n: Int -> n * 2 }))
        assertEquals(emptyList<String>(), Concurrency.run(emptyList<Callable<String>>()))
        assertEquals(listOf("a", "b"), Concurrency.run(listOf(Callable { "a" }, Callable { "b" })))
    }
}
