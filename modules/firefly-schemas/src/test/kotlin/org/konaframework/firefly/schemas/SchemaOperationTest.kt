package org.konaframework.firefly.schemas

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Function
import java.util.function.Predicate

/** Operation and record on [Get], `dehydrated(predicate)` and `default()` (#463). */
class SchemaOperationTest {
    @Test
    fun getReadsTheSchemaOperationAndRecord() {
        val record = Any()
        var seen: Get? = null
        val field = NamedField("name").defaultUsing(
            Function { get ->
                seen = get
                "x"
            },
        )
        val schema = Schema.make().components(field)
        schema.seedDefaults()
        val before = seen!!
        assertEquals("", before.operation)
        assertEquals("", before.operation())
        assertNull(before.record)
        assertNull(before.record())

        schema.operation("edit").record(record)
        // The Get reads the schema live, not a snapshot.
        assertEquals("edit", before.operation)
        assertEquals("edit", before.operation())
        assertSame(record, before.record)
        assertSame(record, before.record())
    }

    @Test
    fun aPlainGetHasNoOperationOrRecord() {
        val get = Get { null }
        assertEquals("", get.operation)
        assertNull(get.record)
    }

    @Test
    fun closuresSeeTheOperationAndTheRecord() {
        val record = "row-7"
        val field = NamedField("title")
            .hidden(Predicate { get -> get.operation == "view" && get.record == "row-7" })
        val schema = Schema.make().operation("view").record(record).components(field)
        assertTrue(field.isHidden(schema))
        schema.operation("edit")
        assertFalse(field.isHidden(schema))
    }

    @Test
    fun visibleOnAndHiddenOnFollowTheOperation() {
        val password = NamedField("password").visibleOn("create")
        val slug = NamedField("slug").hiddenOn("edit")
        val create = Schema.make().operation("create").components(password, slug)
        assertFalse(password.isHidden(create))
        assertFalse(slug.isHidden(create))
        val edit = Schema.make().operation("edit").components(password, slug)
        assertTrue(password.isHidden(edit))
        assertTrue(slug.isHidden(edit))
    }

    @Test
    fun dehydratedPredicateDecidesFromTheState() {
        val password = NamedField("password").dehydrated(Predicate { state -> !state?.toString().isNullOrBlank() })
        val schema = Schema.make().components(password, NamedField("name"))
        schema.fill(mapOf("password" to "", "name" to "Ada"))
        assertTrue(password.isDehydrated())
        assertFalse(password.isDehydrated(schema))
        assertEquals(mapOf<String, Any?>("name" to "Ada"), schema.dehydrate())
        schema.fill(mapOf("password" to "secret"))
        assertTrue(password.isDehydrated(schema))
        assertEquals(mapOf<String, Any?>("password" to "secret", "name" to "Ada"), schema.dehydrate())
    }

    @Test
    fun dehydratedBooleanReplacesAPredicateAndTheOtherWayAround() {
        val field = NamedField("a").dehydrated(Predicate { false })
        val schema = Schema.make().components(field).fill(mapOf("a" to "1"))
        assertFalse(field.isDehydrated(schema))
        field.dehydrated(true)
        assertTrue(field.isDehydrated(schema))
        field.dehydrated(false)
        assertFalse(field.isDehydrated(schema))
        assertFalse(field.isDehydrated())
        // A predicate re-enables a field that was switched off.
        field.dehydrated(Predicate { true })
        assertTrue(field.isDehydrated(schema))
    }

    @Test
    fun dehydratedPredicateOnAnUnnamedComponentSeesNull() {
        var seen: Any? = "unset"
        val anonymous = NamedField("").dehydrated(
            Predicate { state ->
                seen = state
                true
            },
        )
        val schema = Schema.make().components(anonymous)
        assertTrue(anonymous.isDehydrated(schema))
        assertNull(seen)
    }

    @Test
    fun defaultAppliesOnCreateAndStandaloneSchemasOnly() {
        fun seeded(operation: String): Map<String, Any?> {
            val schema = Schema.make().components(
                NamedField("active").default(true),
                NamedField("slug").defaultOnCreate(Function { get -> "${get.operation}-slug" }),
            )
            if (operation.isNotEmpty()) schema.operation(operation)
            schema.seedDefaults()
            return mapOf("active" to schema.get("active"), "slug" to schema.get("slug"))
        }
        assertEquals(mapOf<String, Any?>("active" to true, "slug" to "create-slug"), seeded("create"))
        assertEquals(mapOf<String, Any?>("active" to true, "slug" to "-slug"), seeded(""))
        assertEquals(mapOf<String, Any?>("active" to null, "slug" to null), seeded("edit"))
        assertEquals(mapOf<String, Any?>("active" to null, "slug" to null), seeded("view"))
    }

    @Test
    fun defaultNeverReplacesAStoredValueOnEdit() {
        val schema = Schema.make().operation("edit").components(NamedField("active").default(true))
        schema.fill(mapOf("active" to false))
        schema.hydrate()
        assertEquals(false, schema.get("active"))
        assertTrue(schema.render().contains("ff-component"))
        assertEquals(false, schema.get("active"))
    }

    @Test
    fun defaultTrailingLambdaIsAProviderNotAValue() {
        val field = NamedField("greeting").default { get -> "hi " + get.operation }
        val schema = Schema.make().operation("create").components(field)
        schema.seedDefaults()
        assertEquals("hi create", schema.get("greeting"))
    }

    @Test
    fun defaultValueAndDefaultUsingKeepApplyingOnEveryOperation() {
        val value = NamedField("a").defaultValue("A")
        val using = NamedField("b").defaultUsing(Function { "B" })
        val schema = Schema.make().operation("edit").components(value, using)
        schema.seedDefaults()
        assertEquals("A", schema.get("a"))
        assertEquals("B", schema.get("b"))
    }

    @Test
    fun laterDefaultCallsReplaceEarlierOnes() {
        val field = NamedField("a").default(true).defaultValue("plain")
        val schema = Schema.make().operation("edit").components(field)
        schema.seedDefaults()
        assertEquals("plain", schema.get("a"))

        val back = NamedField("b").defaultUsing(Function { "B" }).defaultOnCreate("literal")
        val other = Schema.make().operation("edit").components(back)
        other.seedDefaults()
        assertNull(other.get("b"))
        val create = Schema.make().operation("create").components(NamedField("b").defaultUsing(Function { "B" }).defaultOnCreate("literal"))
        create.seedDefaults()
        assertEquals("literal", create.get("b"))

        val usingLast = NamedField("c").default("literal").defaultOnCreate(Function { "computed" })
        val created = Schema.make().operation("create").components(usingLast)
        created.seedDefaults()
        assertEquals("computed", created.get("c"))
    }
}
