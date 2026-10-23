package org.konaframework.wire

import org.konaframework.wire.fixtures.Address
import org.konaframework.wire.fixtures.Kitchen
import org.konaframework.wire.fixtures.Status
import org.konaframework.wire.hydration.Synth
import org.konaframework.wire.hydration.SynthContext
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Type
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class HydrationTest {
    private val manager = brew().component("kitchen", Kitchen::class.java)

    private fun mounted(): Map<String, Any?> = snapshotOf(manager.mount("kitchen"))

    private fun data(snapshot: Map<String, Any?>): Map<*, *> = snapshot["data"] as Map<*, *>

    private fun roundTrip(updates: Map<String, Any?>): Kitchen =
        manager.updateComponent(parse(manager.json(mounted())), updates, emptyList()).component as Kitchen

    @Test
    fun `tuples for non-scalars`() {
        val d = data(mounted())
        assertEquals("a", d["text"])
        assertEquals(listOf(listOf("x", "y"), mapOf("s" to "arr")), d["tags"])
        assertEquals(listOf(mapOf("a" to 1), mapOf("s" to "map")), d["lookup"])
        assertEquals(listOf(listOf("u"), mapOf("s" to "set")), d["unique"])
        assertEquals(listOf("DRAFT", mapOf("s" to "enm", "class" to Status::class.java.name)), d["status"])
        assertEquals(listOf("2026-01-02", mapOf("s" to "dt", "type" to "LocalDate")), d["date"])
        assertEquals(listOf("9.99", mapOf("s" to "num", "class" to "decimal")), d["price"])
        assertEquals(listOf("9007199254740993", mapOf("s" to "num", "class" to "long")), d["big"])
        assertEquals(listOf("00000000-0000-0000-0000-000000000001", mapOf("s" to "uuid")), d["id"])
        assertEquals(listOf(mapOf("street" to "Main", "city" to "Kona"), mapOf("s" to "obj", "class" to Address::class.java.name)), d["address"])
        assertEquals(250, (d["money"] as List<*>)[0])
    }

    @Test
    fun `hydration restores declared types`() {
        val k = roundTrip(emptyMap())
        assertEquals(listOf("x", "y"), k.tags)
        assertEquals(listOf(listOf(1, 2), listOf(3)), k.matrix)
        assertEquals(mapOf("a" to 1), k.lookup)
        assertEquals(setOf("u"), k.unique)
        assertEquals(Status.DRAFT, k.status)
        assertEquals(LocalDate.of(2026, 1, 2), k.date)
        assertEquals(BigDecimal("9.99"), k.price)
        assertEquals(9_007_199_254_740_993L, k.big)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), k.id)
        assertEquals(Address("Main", "Kona"), k.address)
        assertEquals(listOf(Address("A", "B")), k.addresses)
        assertEquals(250L, k.money.cents)
        assertArrayEquals(arrayOf("p", "q"), k.array)
    }

    @Test
    fun `scalar coercion and typed guards`() {
        val k = roundTrip(
            mapOf(
                "number" to "42", "decimal" to 3, "flag" to "true", "text" to 7, "nullable" to "",
                "status" to "PUBLISHED", "date" to "2027-05-06", "price" to "1.25",
            ),
        )
        assertEquals(42, k.number)
        assertEquals(3.0, k.decimal)
        assertEquals(true, k.flag)
        assertEquals("7", k.text)
        assertNull(k.nullable)
        assertEquals(Status.PUBLISHED, k.status)
        assertEquals(LocalDate.of(2027, 5, 6), k.date)
        assertEquals(BigDecimal("1.25"), k.price)
        assertEquals(0, roundTrip(mapOf("number" to "")).number)
        assertEquals(0, roundTrip(mapOf("number" to null)).number)
        assertEquals(1, roundTrip(mapOf("status" to 1)).status.ordinal)
        assertThrows<PropertyTypeMismatchException> { roundTrip(mapOf("number" to "abc")) }
        assertThrows<PropertyTypeMismatchException> { roundTrip(mapOf("status" to "NOPE")) }
        assertThrows<PropertyTypeMismatchException> { roundTrip(mapOf("text.deep" to 1)) }
    }

    @Test
    fun `deep paths set, append and remove`() {
        val k = roundTrip(
            mapOf(
                "tags.0" to "first", "tags.2" to "third", "lookup.b" to "2", "matrix.1.1" to 4,
                "addresses.0.city" to "Hilo", "address.street" to "Ali'i", "unique.1" to "v",
            ),
        )
        assertEquals(listOf("first", "y", "third"), k.tags)
        assertEquals(mapOf("a" to 1, "b" to 2), k.lookup)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), k.matrix)
        assertEquals("Hilo", k.addresses[0].city)
        assertEquals("Ali'i", k.address.street)
        assertEquals(setOf("u", "v"), k.unique)
        val removed = roundTrip(mapOf("tags.0" to "__rm__", "lookup.a" to "__rm__", "unique.0" to "__rm__", "array.1" to "q2"))
        assertEquals(listOf("y"), removed.tags)
        assertEquals(emptyMap<String, Int>(), removed.lookup)
        assertEquals(emptySet<String>(), removed.unique)
        assertArrayEquals(arrayOf("p", "q2"), removed.array)
        assertArrayEquals(arrayOf("q"), roundTrip(mapOf("array.0" to "__rm__")).array)
    }

    @Test
    fun `locked, read-only and unknown properties are rejected`() {
        assertThrows<CannotUpdateLockedPropertyException> { roundTrip(mapOf("secret" to "x")) }
        assertThrows<CannotUpdateLockedPropertyException> { roundTrip(mapOf("readOnly" to "x")) }
        assertThrows<PublicPropertyNotFoundException> { roundTrip(mapOf("missing" to 1)) }
    }

    private fun tamper(property: String, value: Any?): Map<String, Any?> {
        val snapshot = mounted().toMutableMap()
        val d = LinkedHashMap(data(snapshot) as Map<String, Any?>)
        d[property] = value
        snapshot["data"] = d
        snapshot.remove("checksum")
        snapshot["checksum"] = manager.checksum.generate(snapshot)
        return snapshot
    }

    @Test
    fun `security policy blocks dangerous classes even with valid checksums`() {
        val evil = tamper("address", listOf(emptyMap<String, Any?>(), mapOf("s" to "obj", "class" to "java.lang.ProcessBuilder")))
        assertThrows<CorruptComponentPayloadException> { manager.update(evil, emptyMap(), emptyList()) }
        val unknown = tamper("address", listOf(emptyMap<String, Any?>(), mapOf("s" to "nope")))
        assertThrows<CorruptComponentPayloadException> { manager.update(unknown, emptyMap(), emptyList()) }
        val missing = tamper("address", listOf(emptyMap<String, Any?>(), mapOf("s" to "obj", "class" to "app.Missing")))
        assertThrows<CorruptComponentPayloadException> { manager.update(missing, emptyMap(), emptyList()) }
        val notWireable = tamper("money", listOf(1, mapOf("s" to "wrbl", "class" to Address::class.java.name)))
        assertThrows<CorruptComponentPayloadException> { manager.update(notWireable, emptyMap(), emptyList()) }
    }

    class Point(val x: Int, val y: Int)

    object PointSynth : Synth() {
        override val key = "point"

        override fun match(value: Any) = value is Point

        override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> =
            "${(value as Point).x},${value.y}" to emptyMap()

        override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? =
            value.toString().split(',').let { Point(it[0].toInt(), it[1].toInt()) }
    }

    class Plotter : Component() {
        var point = Point(1, 2)

        fun move() {
            point = Point(point.x + 1, point.y)
        }

        override fun template() = "<div>{{ point.x }}</div>"
    }

    @Test
    fun `custom property synthesizers take precedence`() {
        val m = brew().component("plotter", Plotter::class.java).propertySynthesizer(PointSynth)
        val snapshot = snapshotOf(m.mount("plotter"))
        assertEquals(listOf("1,2", mapOf("s" to "point")), data(snapshot)["point"])
        val result = m.updateComponent(snapshot, emptyMap(), listOf(call("move")))
        assertEquals(listOf("2,2", mapOf("s" to "point")), data(result.snapshot)["point"])
    }
}
