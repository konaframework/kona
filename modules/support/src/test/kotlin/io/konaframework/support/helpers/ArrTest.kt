package io.konaframework.support.helpers

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

    @Test
    fun forgetPluckDot() {
        val map = linkedMapOf<String, Any?>(
            "a" to 1,
            "nested" to linkedMapOf<String, Any?>("b" to 2, "c" to 3),
        )
        Arr.forget(map, "a")
        assertFalse(Arr.has(map, "a"))
        Arr.forget(map, "nested.b")
        assertFalse(Arr.has(map, "nested.b"))
        assertTrue(Arr.has(map, "nested.c"))
        Arr.forget(map, "nested.missing.x")
        Arr.forget(map, "gone")
        // Nested scalar → mid-path is not a Map.
        Arr.forget(linkedMapOf<String, Any?>("a" to "scalar"), "a.b.c")
        // Nested Map that is not a MutableMap (delegation).
        val readOnlyNest =
            object : Map<String, Any?> by mapOf("x" to 1) {}
        val frozen = linkedMapOf<String, Any?>("nest" to readOnlyNest)
        Arr.forget(frozen, "nest.x")
        assertEquals(1, Arr.get(frozen, "nest.x"))
        // Mid-path missing for longer dots.
        Arr.forget(linkedMapOf("a" to linkedMapOf<String, Any?>("b" to 1)), "a.missing.z")
        // UOE path: Map that implements MutableMap but rejects remove.
        val stubborn =
            linkedMapOf<String, Any?>(
                "nest" to
                    object : LinkedHashMap<String, Any?>() {
                        init {
                            put("x", 1)
                        }

                        override fun remove(key: String): Any? = throw UnsupportedOperationException()
                    },
            )
        Arr.forget(stubborn, "nest.x")

        val rows: List<Map<String, Any?>> =
            listOf(
                mapOf("id" to 1, "name" to "a"),
                mapOf("id" to 2, "name" to "b"),
                mapOf("name" to "no-id"),
            )
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("a", "b", "no-id"), Arr.pluck(rows, "name") as List<*>)
        @Suppress("UNCHECKED_CAST")
        assertEquals(mapOf(1 to "a", 2 to "b"), Arr.pluck(rows, "name", "id") as Map<*, *>)

        assertEquals(
            mapOf("user.name" to "Ada", "user.age" to 1, "x" to 2),
            Arr.dot(mapOf("user" to mapOf("name" to "Ada", "age" to 1), "x" to 2)),
        )
    }
}
