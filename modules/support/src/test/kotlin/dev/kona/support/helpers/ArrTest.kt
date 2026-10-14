package dev.kona.support.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArrTest {
    @Test
    fun getHasSet() {
        val map = linkedMapOf<String, Any?>(
            "name" to "kona",
            "nested" to linkedMapOf("a" to 1, "b" to linkedMapOf("c" to 2)),
            "nullish" to null,
        )
        assertEquals("kona", Arr.get(map, "name"))
        assertEquals("fallback", Arr.get(map, "missing", "fallback"))
        assertNull(Arr.get(map, "nullish", "fallback"))
        assertEquals(1, Arr.get(map, "nested.a"))
        assertEquals(2, Arr.get(map, "nested.b.c"))
        assertEquals("d", Arr.get(map, "nested.missing", "d"))
        assertEquals("d", Arr.get(map, "name.child", "d"))
        assertEquals("d", Arr.get(map, "nested.a.child", "d"))

        assertTrue(Arr.has(map, "name"))
        assertTrue(Arr.has(map, "nullish"))
        assertFalse(Arr.has(map, "missing"))
        assertTrue(Arr.has(map, "nested.b.c"))
        assertFalse(Arr.has(map, "nested.missing"))
        assertFalse(Arr.has(map, "name.child"))
        assertFalse(Arr.has(map, "nested.a.x"))

        val mutable = linkedMapOf<String, Any?>("x" to 1)
        Arr.set(mutable, "y", 2)
        assertEquals(2, mutable["y"])
        Arr.set(mutable, "deep.child", 3)
        assertEquals(3, Arr.get(mutable, "deep.child"))
        Arr.set(mutable, "deep.other", 4)
        assertEquals(4, Arr.get(mutable, "deep.other"))
        Arr.set(mutable, "x.nested", 9)
        assertEquals(9, Arr.get(mutable, "x.nested"))
    }

    @Test
    fun onlyExceptWrapFlatten() {
        val map = mapOf("a" to 1, "b" to 2, "c" to 3)
        assertEquals(mapOf("a" to 1, "c" to 3), Arr.only(map, listOf("a", "c", "z")))
        assertEquals(mapOf("b" to 2), Arr.except(map, listOf("a", "c")))
        assertNull(Arr.first(emptyList<Any>()))
        assertEquals(1, Arr.first(listOf(1, 2)))
        assertNull(Arr.last(emptyList<Any>()))
        assertEquals(2, Arr.last(listOf(1, 2)))
        assertEquals(emptyList<Any?>(), Arr.wrap(null))
        assertEquals(listOf(1, 2), Arr.wrap(listOf(1, 2)))
        assertEquals(listOf("a", "b"), Arr.wrap(arrayOf("a", "b")))
        assertEquals(listOf(5), Arr.wrap(5))
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            Arr.flatten(listOf(1, listOf(2, arrayOf(3, 4)), 5)),
        )
    }
}
