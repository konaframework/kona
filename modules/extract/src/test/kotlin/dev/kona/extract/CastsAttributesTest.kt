package dev.kona.extract

import dev.kona.extract.schema.Schema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CentsCast : CastsAttributes {
    override fun get(model: Extract<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) return null
        val cents = if (value is Number) value.toLong() else value.toString().toLong()
        return cents / 100.0
    }

    override fun set(model: Extract<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) return null
        val dollars = if (value is Number) value.toDouble() else value.toString().toDouble()
        return Math.round(dollars * 100)
    }
}

class PricedItem : Extract<PricedItem>() {
    override val table = "priced_items"
    override val timestamps = false
    override val fillable = setOf("name", "price")
    override val casts = mapOf("price" to CentsCast())

    companion object : ExtractCompanion<PricedItem>(PricedItem::class.java)
}

class BadCastModel : Extract<BadCastModel>() {
    override val table = "priced_items"
    override val timestamps = false
    override val casts = mapOf("price" to "not-a-cast")

    companion object : ExtractCompanion<BadCastModel>(BadCastModel::class.java)
}

class CastsAttributesTest {
    @BeforeEach
    fun setUp() {
        TestDb.create()
        Database.global().withConnection { connection ->
            Schema(connection).create("priced_items") {
                id()
                string("name")
                bigInteger("price").nullable()
            }
        }
    }

    @AfterEach
    fun tearDown() {
        Database.clearGlobal()
    }

    @Test
    fun customCastRoundTripsThroughAttributesAndDatabase() {
        val item = PricedItem.create(mapOf("name" to "Mug", "price" to 12.5))
        assertEquals(1250L, item.getRawAttribute("price"))
        assertEquals(12.5, item["price"])

        val found = PricedItem.find(item.getKey())!!
        assertEquals(12.5, found["price"])
        found["price"] = 9.99
        found.save()
        assertEquals(999L, found.getRawAttribute("price"))
        assertTrue(found.wasChanged("price"))
        assertEquals(999L, found.getChanges()["price"])
    }

    @Test
    fun wasChangedClearsOnCleanSave() {
        val item = PricedItem.create(mapOf("name" to "Cup", "price" to 1.0))
        assertTrue(item.wasChanged())
        assertTrue(item.save())
        assertFalse(item.wasChanged())
        assertTrue(item.getChanges().isEmpty())
    }

    @Test
    fun invalidCastDeclarationFails() {
        val model = BadCastModel()
        assertThrows(ExtractException::class.java) { model.setAttribute("price", 2) }
        // Seed a raw attribute without going through setAttribute, then read.
        val hydrated = BadCastModel().newFromBuilder(mapOf("id" to 1L, "price" to 1L))
        assertThrows(ExtractException::class.java) { hydrated["price"] }
    }

    @Test
    fun loadAggregatesNoopWhenUnsaved() {
        val fresh = PricedItem()
        assertSame(fresh, fresh.loadCount("x"))
        assertSame(fresh, fresh.loadExists())
        assertSame(fresh, fresh.loadExists("x"))
        assertSame(fresh, fresh.loadSum("x:y"))
        assertSame(fresh, fresh.loadCount())
    }
}
