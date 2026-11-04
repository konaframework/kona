package org.konaframework.cadence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.cadence.schema.Schema
import java.math.BigDecimal

enum class OrderState {
    DRAFT,
    POSTED,
    CANCELLED,
}

enum class LineTone(val value: String) {
    HOT("hot"),
    COLD("cold"),
}

class LedgerLine : Cadence<LedgerLine>() {
    override val table = "ledger_lines"
    override val timestamps = false
    override val fillable = setOf("amount", "tax", "state", "tones", "label")
    override val casts = mapOf(
        "amount" to CastType.decimal(2),
        "tax" to "decimal:4",
        "state" to OrderState::class,
        "tones" to CastType.enumCollection(LineTone::class),
    )

    companion object : CadenceCompanion<LedgerLine>(LedgerLine::class.java)
}

class JavaStyleLedger : Cadence<JavaStyleLedger>() {
    override val table = "ledger_lines"
    override val timestamps = false
    override val fillable = setOf("amount", "state")
    override val casts = mapOf(
        "amount" to DecimalCast(2),
        "state" to OrderState::class.java,
    )

    companion object : CadenceCompanion<JavaStyleLedger>(JavaStyleLedger::class.java)
}

class DecimalEnumCastTest {
    @BeforeEach
    fun setUp() {
        TestDb.create()
        Database.global().withConnection { connection ->
            Schema(connection).create("ledger_lines") {
                id()
                decimal("amount", 16, 2).nullable()
                decimal("tax", 16, 4).nullable()
                string("state").nullable()
                text("tones").nullable()
                string("label").nullable()
            }
        }
    }

    @AfterEach
    fun tearDown() {
        CastRegistry.clear()
        Database.clearGlobal()
    }

    @Test
    fun decimalCastRoundTripsWithoutDoubleAndSerializesAsString() {
        val line = LedgerLine.create(
            mapOf(
                "amount" to BigDecimal("12.345"),
                "tax" to "1.23456",
                "state" to OrderState.DRAFT,
                "tones" to listOf(LineTone.HOT, LineTone.COLD),
                "label" to "sale",
            ),
        )
        assertEquals(BigDecimal("12.35"), line["amount"])
        assertEquals(BigDecimal("1.2346"), line["tax"])
        assertEquals(BigDecimal("12.35"), line.getRawAttribute("amount"))
        assertEquals("12.35", line.toMap()["amount"])
        assertTrue(line.toJson().contains("\"amount\":\"12.35\""))

        val found = LedgerLine.find(line.getKey())!!
        assertEquals(BigDecimal("12.35"), found["amount"])
        assertEquals(OrderState.DRAFT, found["state"])
        assertEquals(listOf(LineTone.HOT, LineTone.COLD), found["tones"])
        assertEquals("DRAFT", found.toMap()["state"])
        assertEquals(listOf("hot", "cold"), found.toMap()["tones"])
    }

    @Test
    fun decimalAcceptsIntegralNumbersAndRejectsGarbage() {
        val line = LedgerLine()
        line["amount"] = 10
        assertEquals(BigDecimal("10.00"), line["amount"])
        line["amount"] = 10L
        assertEquals(BigDecimal("10.00"), line["amount"])
        line["amount"] = null
        assertNull(line["amount"])
        assertThrows(CadenceException::class.java) { line.setAttribute("amount", "nope") }
        assertThrows(CadenceException::class.java) { line.setAttribute("amount", true) }
        assertThrows(IllegalArgumentException::class.java) { DecimalCast(-1) }
    }

    @Test
    fun enumCastUsesNameOrValueAndRejectsUnknown() {
        val line = JavaStyleLedger.create(mapOf("amount" to "3.10", "state" to "POSTED"))
        assertEquals(OrderState.POSTED, line["state"])
        assertEquals("POSTED", line.getRawAttribute("state"))

        line["state"] = OrderState.CANCELLED
        line.save()
        assertEquals("CANCELLED", line.getRawAttribute("state"))

        assertThrows(CadenceException::class.java) { line.setAttribute("state", "UNKNOWN") }
        val hydrated = JavaStyleLedger().newFromBuilder(mapOf("id" to 9L, "state" to "UNKNOWN"))
        assertThrows(CadenceException::class.java) { hydrated["state"] }
    }

    @Test
    fun enumWithValuePropertyStoresBackingString() {
        val cast = EnumCast(LineTone::class.java)
        val model = LedgerLine()
        assertEquals("hot", cast.set(model, "tones", LineTone.HOT, emptyMap()))
        assertEquals(LineTone.COLD, cast.get(model, "tones", "cold", emptyMap()))
        assertEquals(LineTone.HOT, cast.get(model, "tones", "HOT", emptyMap()))
    }

    @Test
    fun enumCollectionWritesJsonArray() {
        val line = LedgerLine.create(mapOf("tones" to arrayOf(LineTone.HOT)))
        assertEquals("""["hot"]""", line.getRawAttribute("tones"))
        assertEquals(listOf(LineTone.HOT), line["tones"])
        line["tones"] = null
        assertNull(line.getRawAttribute("tones"))
        assertThrows(CadenceException::class.java) { line.setAttribute("tones", "HOT") }
        assertThrows(CadenceException::class.java) {
            EnumCollectionCast(LineTone::class.java).get(line, "tones", "{\"a\":1}", emptyMap())
        }
    }

    @Test
    fun castRegistryNamedCastsAndNonEnumClassFail() {
        CastRegistry.register("money") { DecimalCast(3) }
        assertTrue(CastRegistry.isRegistered("money"))

        class MoneyLine : Cadence<MoneyLine>() {
            override val table = "ledger_lines"
            override val timestamps = false
            override val casts = mapOf("amount" to "money", "label" to String::class.java)
        }

        class BadDecimal : Cadence<BadDecimal>() {
            override val table = "ledger_lines"
            override val timestamps = false
            override val casts = mapOf("amount" to "decimal:nope")
        }

        val model = MoneyLine()
        model.setAttribute("amount", "1.2345")
        assertEquals(BigDecimal("1.235"), model["amount"])
        assertThrows(CadenceException::class.java) { model.setAttribute("label", "x") }
        assertThrows(CadenceException::class.java) { BadDecimal().setAttribute("amount", "1") }

        CastRegistry.unregister("money")
        assertThrows(CadenceException::class.java) { MoneyLine().setAttribute("amount", "1") }
    }
}
