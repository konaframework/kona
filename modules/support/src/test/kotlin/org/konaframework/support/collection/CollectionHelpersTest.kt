package org.konaframework.support.collection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionHelpersTest {
    @Test
    fun flatMapReduceChunkSumAvgWhenUnless() {
        val c = collect(1, 2, 3)
        assertEquals(listOf(1, 1, 2, 2, 3, 3), c.flatMap { listOf(it, it) }.toList())
        assertEquals(6, c.reduce(0) { acc, n -> acc + n })
        assertEquals(6, c.reduce { a, b -> a + b })
        assertThrows(IllegalStateException::class.java) {
            Collection.empty<Int>().reduce { a, b -> a + b }
        }

        val chunks = c.chunk(2)
        assertEquals(2, chunks.count())
        assertEquals(listOf(1, 2), chunks.get(0)!!.toList())
        assertEquals(listOf(3), chunks.get(1)!!.toList())
        assertThrows(IllegalArgumentException::class.java) { c.chunk(0) }
        assertThrows(IllegalArgumentException::class.java) { c.chunk(-1) }

        assertEquals(6.0, collect(1, 2, 3).sum(), 0.0)
        assertEquals(0.0, collect("a", "b").sum(), 0.0)
        assertEquals(2.0, collect(1, 2, 3).avg()!!, 0.0)
        assertNull(Collection.empty<Int>().avg())

        val seen = mutableListOf<String>()
        collect("x").`when`(true) { seen.add("when-true") }
            .`when`(false) { seen.add("when-false") }
            .unless(false) { seen.add("unless-false") }
            .unless(true) { seen.add("unless-true") }
        assertEquals(listOf("when-true", "unless-false"), seen)
        assertTrue(collect(1).`when`(false) { }.count() == 1)
    }
}
