package io.konaframework.support.collection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionTest {
    @Test
    fun basicsAndMutations() {
        val empty = Collection.empty<Int>()
        assertTrue(empty.isEmpty())
        assertFalse(empty.isNotEmpty())
        assertEquals(0, empty.count())
        assertNull(empty.first())
        assertNull(empty.last())
        assertNull(empty.get(0))

        val c = Collection.make(1, 2, 3)
        assertEquals(listOf(1, 2, 3), c.all())
        assertEquals(listOf(1, 2, 3), c.values().toList())
        assertEquals(3, c.count())
        assertTrue(c.isNotEmpty())
        assertEquals(1, c.first())
        assertEquals(3, c.last())
        assertEquals(2, c.get(1))
        assertNull(c.get(99))

        c.push(4)
        assertEquals(listOf(1, 2, 3, 4), c.toList())
        c.put(1, 20)
        assertEquals(20, c.get(1))
        c.put(100, 5)
        assertEquals(5, c.last())
        c.put(-1, 9)
        assertEquals(9, c.last())
    }

    @Test
    fun transformHelpers() {
        val c = collect(1, 2, 3, 2, 4)
        assertEquals(listOf(2, 2, 4), c.filter { it % 2 == 0 }.toList())
        assertEquals(listOf(2, 4, 6, 4, 8), c.map { it * 2 }.toList())
        val seen = mutableListOf<Int>()
        c.each { seen.add(it) }
        assertEquals(c.toList(), seen)
        assertEquals(listOf(1, 2), c.take(2).toList())
        assertEquals(emptyList<Int>(), c.take(-1).toList())
        assertEquals(listOf(3, 2, 4), c.skip(2).toList())
        assertEquals(c.toList(), c.skip(-5).toList())
        assertEquals(listOf(4, 2, 3, 2, 1), c.reverse().toList())
        assertEquals(listOf(1, 2, 3, 4), c.unique().toList())
        assertEquals(listOf(1, 2, 3, 4), c.uniqueBy { it }.toList())
        assertEquals(listOf(1, 2, 2, 3, 4), Collection.of(listOf(3, 1, 2, 4, 2)).sort().toList())
        data class Row(val name: String, val n: Int)
        val rows = Collection.of(listOf(Row("b", 2), Row("a", 1), Row("c", 3)))
        assertEquals(listOf("a", "b", "c"), rows.sortBy { it.name }.map { it.name }.toList())
        assertEquals(listOf("c", "b", "a"), rows.sortByDesc { it.name }.map { it.name }.toList())
        assertTrue(c.contains(2))
        assertFalse(c.contains(99))
        assertTrue(c.contains { it > 3 })
        assertFalse(c.contains { it > 100 })
    }

    @Test
    fun pluckGroupImplodeEquals() {
        val maps = collect(
            mapOf("id" to 1, "name" to "a"),
            mapOf("id" to 2, "name" to "b"),
            "not-a-map",
        )
        assertEquals(listOf(1, 2, null), maps.pluck("id").toList())

        val grouped = collect(1, 2, 3, 4).groupBy { it % 2 }
        assertEquals(listOf(1, 3), grouped[1]!!.toList())
        assertEquals(listOf(2, 4), grouped[0]!!.toList())

        assertEquals("1-2-3", collect(1, 2, 3).implode("-"))
        assertEquals("a|b", collect("a", "b").implode("|") { it })

        val a = Collection.make(1, 2)
        val b = Collection.of(listOf(1, 2))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, Collection.make(1))
        assertNotEquals(a, "nope")
        assertTrue(a.toString().contains("1"))
        assertEquals(listOf(1, 2), a.iterator().asSequence().toList())
        assertEquals(listOf("x", "y"), collect(listOf("x", "y")).toList())
    }
}
