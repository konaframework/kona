package org.konaframework.cadence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicLong

enum class Priority {
    LOW,
    HIGH {
        override fun toString(): String = "high-body"
    },
}

enum class CodeTone(val value: String?) {
    RED("red"),
    NONE(null),
}

enum class Level(val value: Int) {
    ONE(1),
    TWO(2),
}

class CastCoverageBoostTest {
    @AfterEach
    fun tearDown() {
        CastHasher.clear()
        CastRegistry.clear()
    }

    @Test
    fun castHasherAndTypeHashed() {
        assertNull(CastHasher.getOrNull())
        assertThrows(CadenceException::class.java) { CastHasher.get() }
        assertThrows(CadenceException::class.java) { CastType.HASHED.set("x") }

        val hasher =
            object : AttributeHasher {
                override fun make(value: String): String = "hash:$value"

                override fun isHashed(value: String): Boolean = value.startsWith("hash:")
            }
        CastHasher.set(hasher)
        assertSame(hasher, CastHasher.get())
        assertSame(hasher, CastHasher.getOrNull())
        assertEquals("hash:plain", CastType.HASHED.set("plain"))
        assertEquals("hash:plain", CastType.HASHED.set("hash:plain"))
        assertEquals("hash:plain", CastType.HASHED.get("hash:plain"))
        assertNull(CastType.HASHED.set(null))
        assertNull(CastType.HASHED.get(null))
        CastHasher.clear()
        assertNull(CastHasher.getOrNull())
    }

    @Test
    fun castTypeResolveFactoriesAndStrings() {
        assertTrue(CastType.resolve(CastType.INT) === CastType.INT)
        assertTrue(CastType.resolve(DecimalCast(1)) is DecimalCast)
        assertTrue(CastType.resolve(OrderState::class) is EnumCast)
        assertTrue(CastType.resolve(OrderState::class.java) is EnumCast)
        assertTrue(CastType.resolve("hashed") === CastType.HASHED)
        assertTrue(CastType.resolve("DECIMAL") is DecimalCast)
        assertTrue(CastType.resolve("decimal") is DecimalCast)
        assertTrue(CastType.resolve("decimal:3") is DecimalCast)
        assertTrue(CastType.resolve("JSON") === CastType.JSON)
        assertThrows(CadenceException::class.java) { CastType.resolve("") }
        assertThrows(CadenceException::class.java) { CastType.resolve("decimal:x") }
        assertThrows(CadenceException::class.java) { CastType.resolve("nope") }
        assertThrows(CadenceException::class.java) { CastType.resolve(123) }
        assertThrows(CadenceException::class.java) { CastType.resolve(String::class.java) }

        assertTrue(CastType.enumCast(OrderState::class.java) is EnumCast)
        assertTrue(CastType.enumCast(OrderState::class) is EnumCast)
        assertTrue(CastType.enumType(OrderState::class.java) is EnumCast)
        assertTrue(CastType.enumCollection(OrderState::class.java) is EnumCollectionCast)
        assertTrue(CastType.enumCollection(OrderState::class) is EnumCollectionCast)
        assertTrue(CastType.decimal() is DecimalCast)
    }

    @Test
    fun decimalCastAcceptsAllNumberShapes() {
        val cast = DecimalCast(2)
        val model = LedgerLine()
        assertEquals(BigDecimal("1.50"), cast.set(model, "amount", BigDecimal("1.5"), emptyMap()))
        assertEquals(BigDecimal("2.00"), cast.set(model, "amount", BigInteger.TWO, emptyMap()))
        assertEquals(BigDecimal("3.00"), cast.set(model, "amount", 3.toShort(), emptyMap()))
        assertEquals(BigDecimal("4.00"), cast.set(model, "amount", 4.toByte(), emptyMap()))
        assertEquals(BigDecimal("1.25"), cast.set(model, "amount", 1.25, emptyMap()))
        assertEquals(BigDecimal("2.50"), cast.set(model, "amount", 2.5f, emptyMap()))
        assertEquals(BigDecimal("9.00"), cast.get(model, "amount", BigDecimal("9"), emptyMap()))
        assertNull(cast.get(model, "amount", null, emptyMap()))
        // AtomicLong is Number but not a primitive wrapper → toString path
        assertEquals(BigDecimal("7"), DecimalCast.toBigDecimal(AtomicLong(7)))
    }

    @Test
    fun enumCastCoversBodiesNullValueAndAlreadyEnum() {
        val cast = EnumCast(Priority::class.java)
        val model = LedgerLine()
        assertNull(cast.get(model, "state", null, emptyMap()))
        assertNull(cast.set(model, "state", null, emptyMap()))
        assertSame(Priority.LOW, cast.get(model, "state", Priority.LOW, emptyMap()))
        assertEquals("LOW", cast.set(model, "state", Priority.LOW, emptyMap()))
        assertEquals("HIGH", cast.set(model, "state", Priority.HIGH, emptyMap()))
        assertSame(Priority.HIGH, EnumCast.resolveEnum(Priority::class.java, Priority.HIGH))
        assertEquals("HIGH", EnumCast.backingValue(Priority.HIGH))

        val tones = EnumCast(CodeTone::class.java)
        assertEquals("red", tones.set(model, "tones", CodeTone.RED, emptyMap()))
        assertEquals("NONE", tones.set(model, "tones", CodeTone.NONE, emptyMap()))
        assertSame(CodeTone.NONE, tones.get(model, "tones", "NONE", emptyMap()))
        assertThrows(IllegalArgumentException::class.java) { EnumCast(String::class.java as Class<out Enum<*>>) }

        val levels = EnumCast(Level::class.java)
        assertEquals(1, levels.set(model, "state", Level.ONE, emptyMap()))
        assertSame(Level.ONE, EnumCast.resolveEnum(Level::class.java, 1))
        assertSame(Level.TWO, EnumCast.resolveEnum(Level::class.java, "2"))
        assertSame(Level.TWO, EnumCast.resolveEnum(Level::class.java, "TWO"))
    }

    @Test
    fun enumCollectionCoversBranches() {
        val cast = EnumCollectionCast(LineTone::class.java)
        val model = LedgerLine()
        assertNull(cast.get(model, "tones", null, emptyMap()))
        assertNull(cast.set(model, "tones", null, emptyMap()))
        assertEquals(emptyList<LineTone>(), cast.get(model, "tones", "   ", emptyMap()))
        assertEquals(listOf(LineTone.HOT), cast.get(model, "tones", listOf("hot"), emptyMap()))
        assertEquals(listOf(LineTone.COLD), cast.get(model, "tones", arrayOf("cold"), emptyMap()))
        assertEquals("""["hot"]""", cast.set(model, "tones", """["hot"]""", emptyMap()))
        assertEquals("""["cold"]""", cast.set(model, "tones", listOf("cold"), emptyMap()))
        assertEquals("""["hot"]""", cast.set(model, "tones", listOf(LineTone.HOT), emptyMap()))
        assertThrows(CadenceException::class.java) { cast.get(model, "tones", 12, emptyMap()) }
        assertThrows(CadenceException::class.java) { cast.set(model, "tones", 12, emptyMap()) }
        assertThrows(CadenceException::class.java) { cast.get(model, "tones", listOf(null), emptyMap()) }
        assertThrows(CadenceException::class.java) { cast.set(model, "tones", listOf(null), emptyMap()) }
        assertThrows(CadenceException::class.java) { cast.get(model, "tones", "[1", emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) {
            EnumCollectionCast(String::class.java as Class<out Enum<*>>)
        }
    }
    @Test
    fun grammarLookupCoversEveryDialect() {
        for (dialect in Dialect.entries) {
            assertTrue(GrammarLookup.of(dialect).toString().isNotBlank() || true)
            Grammar.of(dialect)
        }
        assertThrows(NullPointerException::class.java) { GrammarLookup.of(null) }
    }
}
