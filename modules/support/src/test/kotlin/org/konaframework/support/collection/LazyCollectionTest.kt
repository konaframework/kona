package org.konaframework.support.collection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class Named(val name: String)

class Flag(val active: Boolean)

class Votes(val votes: Int, val label: String)

class ExactName {
    fun name(): String = "exact"
}

class LazyCollectionTest {
    @Test
    fun chainsStayLazyUntilAValueIsRead() {
        val reads = CountingList(5)
        val lazy = LazyCollection.of(reads)
        val mapped = lazy.filter { it % 2 == 0 }.take(1).map { it * 10 }
        assertEquals(0, reads.reads)
        assertEquals(20, mapped.first())
        assertTrue(reads.reads < 5)
        assertEquals(listOf(20), mapped.collect().toList())
        assertEquals(listOf(1, 2, 4), LazyCollection.make(1, 2, 2, 4).unique().skip(0).all())
        assertEquals(listOf(1, 2), LazyCollection.of(listOf(listOf(1), listOf(2))).flatMap { it }.all())
        assertTrue(LazyCollection.empty<Int>().isEmpty())
        assertFalse(LazyCollection.make(1).isEmpty())
        assertEquals(0, LazyCollection.empty<Int>().count())
        assertEquals(2, LazyCollection.make(1, 2).count())
        assertNull(LazyCollection.empty<Int>().first())
        assertNull(LazyCollection.make(1, 2).first { it > 5 })
        assertEquals(2, LazyCollection.make(1, 2).first { it > 1 })
        assertEquals(emptyList<Int>(), LazyCollection.make(1, 2, 3).take(-1).all())
        assertEquals(listOf(1, 2, 3), LazyCollection.make(1, 2, 3).skip(-3).all())
        assertEquals(listOf(3), LazyCollection.make(1, 2, 3).skip(2).reject { it < 0 }.all())
        val taps = AtomicInteger()
        assertEquals(listOf(1), LazyCollection.make(1).tap { taps.incrementAndGet() }.all())
        assertEquals(1, taps.get())
        val seen = mutableListOf<Int>()
        val source = LazyCollection.make(4, 5)
        assertSame(source, source.each { seen += it })
        assertEquals(listOf(4, 5), seen)
        assertEquals(listOf(1, 2), Collection.make(1, 2, 3).lazy().take(2).collect().toList())
        val walked = mutableListOf<Int>()
        for (item in LazyCollection.make(7)) {
            walked += item
        }
        assertEquals(listOf(7), walked)
        assertEquals(listOf(1, 2), LazyCollection(listOf(1, 2)).all())
    }

    @Test
    fun higherOrderReadsPropertiesAndMethods() {
        val rows = Collection.make(
            mapOf("name" to "Ada", "active" to true, "votes" to 2),
            mapOf("name" to "Bea", "active" to false, "votes" to 0),
            mapOf("name" to null, "active" to "", "votes" to "nope"),
        )
        @Suppress("UNCHECKED_CAST")
        val names = rows.higher("map").get("name") as Collection<Any?>
        assertEquals(listOf("Ada", "Bea", null), names.toList())
        @Suppress("UNCHECKED_CAST")
        val active = rows.higher("filter").get("active") as Collection<Map<String, Any?>>
        assertEquals(1, active.count())
        @Suppress("UNCHECKED_CAST")
        val rejected = rows.higher("reject").get("active") as Collection<*>
        assertEquals(2, rejected.count())
        assertEquals(2.0, rows.higher("sum").get("votes"))
        assertFalse(rows.higher("every").get("active") as Boolean)
        assertEquals(rows.first(), rows.higher("first").get("active"))

        val people = Collection.make(Named("Ada"), Named("Bea"))
        @Suppress("UNCHECKED_CAST")
        val picked = people.higher("map").get("name") as Collection<Any?>
        assertEquals(listOf("Ada", "Bea"), picked.toList())
        val exact = Collection.make(ExactName()).higher("map").call("name") as Collection<*>
        assertEquals(listOf("exact"), exact.toList())
        val flags = Collection.make(Flag(true), Flag(false))
        assertEquals(1, (flags.higher("filter").get("active") as Collection<*>).count())
        assertEquals(Flag(true).active, (flags.higher("first").get("active") as Flag).active)
        assertTrue(Collection.make(Flag(true)).higher("every").get("active") as Boolean)
        assertNull(Collection.empty<Flag>().higher("first").get("active"))
        assertTrue(Collection.empty<Flag>().higher("every").get("active") as Boolean)
        assertEquals(0.0, Collection.empty<Votes>().higher("sum").get("votes"))

        val pings = AtomicInteger()
        val box = Ping { pings.incrementAndGet() }
        val boxes = Collection.make(box)
        assertSame(boxes, boxes.higher("each").call("ping"))
        assertEquals(1, pings.get())
        assertSame(boxes, boxes.higher("each").get("missing"))
        val missing = Collection.make<Any?>(null).higher("map").get("name") as Collection<*>
        assertEquals(listOf(null), missing.toList())
        val missingCall = Collection.make<Any?>(null).higher("map").call("ping") as Collection<*>
        assertEquals(listOf(null), missingCall.toList())
        assertThrows(IllegalArgumentException::class.java) {
            Collection.make(box).higher("each").call("missing")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Collection.make(1).higher("nope").get("name")
        }

        val reads = CountingList(4)
        val lazy = LazyCollection.of(reads)
        val proxied = lazy.higher("filter").get("ignored")
        assertEquals(0, reads.reads)
        assertTrue(proxied is LazyCollection<*>)
        assertEquals(0, (proxied as LazyCollection<*>).count())
        val lazyMapped = LazyCollection.make(Named("A")).higher("map").get("name") as LazyCollection<*>
        assertEquals(listOf("A"), lazyMapped.collect().toList())
        val lazyRejected = LazyCollection.make(Flag(false), Flag(true)).higher("reject").get("active") as LazyCollection<*>
        assertEquals(1, lazyRejected.count())
        val lazyBoxes = LazyCollection.make(box)
        assertSame(lazyBoxes, lazyBoxes.higher("each").call("ping"))

        assertFalse(truthy(null))
        assertFalse(truthy(false))
        assertTrue(truthy(true))
        assertFalse(truthy(0))
        assertTrue(truthy(2))
        assertFalse(truthy(""))
        assertTrue(truthy("a"))
        assertFalse(truthy(emptyList<Int>()))
        assertTrue(truthy(listOf(1)))
        assertFalse(truthy(emptyArray<String>()))
        assertTrue(truthy(arrayOf("x")))
        assertTrue(truthy(Any()))
        assertNull(readProperty(Named("A"), ""))
        assertEquals("", accessorSuffix(""))
        assertEquals("Name", accessorSuffix("name"))
        assertEquals("L", readProperty(FieldOnly(), "label"))
        assertNull(readProperty(FieldOnly(), "missing"))
    }
}

class Ping(private val action: () -> Unit) {
    fun ping() = action()
}

private class CountingList(override val size: Int) : AbstractList<Int>() {
    var reads: Int = 0

    override fun get(index: Int): Int {
        reads += 1
        return index + 1
    }
}
