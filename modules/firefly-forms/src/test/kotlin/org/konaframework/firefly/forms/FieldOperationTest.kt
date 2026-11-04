package org.konaframework.firefly.forms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.schemas.Schema

/** The canonical password field and create-only defaults (#463). */
class FieldOperationTest {
    private fun password(): TextInput = TextInput.make("password").password()
        .dehydrateStateUsing { "hashed:" + it }
        .dehydrated { !it?.toString().isNullOrBlank() }
        .required { it.operation == "create" }

    @Test
    fun passwordIsRequiredOnCreateOnly() {
        val field = password()
        val create = Schema.make().operation("create").components(field)
        assertTrue(field.isRequired(create))
        assertTrue(field.validationRules(create).contains("required"))
        assertTrue(FieldValidation.validate(create, create.components()).containsKey("password"))

        val edit = Schema.make().operation("edit").components(password())
        val optional = edit.components().first() as TextInput
        assertFalse(optional.isRequired(edit))
        assertTrue(FieldValidation.validate(edit, edit.components()).isEmpty())
    }

    @Test
    fun blankPasswordIsLeftOutAndAFilledOneIsHashed() {
        val blank = Schema.make().operation("edit").components(password(), TextInput.make("name"))
        blank.fill(mapOf("password" to "", "name" to "Ada"))
        assertEquals(mapOf<String, Any?>("name" to "Ada"), blank.dehydrate())

        val filled = Schema.make().operation("edit").components(password(), TextInput.make("name"))
        filled.fill(mapOf("password" to "secret", "name" to "Ada"))
        assertEquals(mapOf<String, Any?>("password" to "hashed:secret", "name" to "Ada"), filled.dehydrate())
    }

    @Test
    fun passwordCanBeVisibleOnCreateOnly() {
        val field = password().visibleOn("create")
        val create = Schema.make().operation("create").components(field)
        assertFalse(field.isHidden(create))
        val edit = Schema.make().operation("edit").components(field)
        assertTrue(field.isHidden(edit))
        assertTrue(edit.dehydrate().isEmpty())
    }

    @Test
    fun passwordInputNeverRendersItsValue() {
        val field = password()
        assertTrue(field.isPassword())
        val schema = Schema.make().operation("edit").components(field, TextInput.make("name"))
        schema.fill(mapOf("password" to "hash-from-the-database", "name" to "Ada"))
        val html = schema.render()
        assertFalse(html.contains("hash-from-the-database"))
        assertTrue(html.contains("type=\"password\""))
        assertTrue(html.contains("value=\"Ada\""))
        assertFalse(TextInput.make("plain").isPassword())
        // No state at all still renders an empty password input.
        assertTrue(Schema.make().components(TextInput.make("pw").password()).render().contains("name=\"pw\""))
    }

    @Test
    fun defaultStartsAToggleOnForANewRecord() {
        val create = Schema.make().operation("create").components(Toggle.make("active").default(true))
        assertTrue(create.render().contains("ff-toggle"))
        assertEquals(true, create.get("active"))
        val edit = Schema.make().operation("edit").components(Toggle.make("active").default(true))
        edit.render()
        assertNull(edit.get("active"))
        val computed = Schema.make().operation("create").components(
            TextInput.make("slug").default { get -> get.operation + "-slug" },
        )
        computed.render()
        assertEquals("create-slug", computed.get("slug"))
    }

    @Test
    fun dehydratedStaysChainableOnFields() {
        val field = TextInput.make("name").dehydrated(false).dehydrated().label("Name")
        assertTrue(field.isDehydrated())
        assertFalse(TextInput.make("skip").dehydrated(false).label("Skip").isDehydrated())
        assertFalse(TextInput.make("skip").saved(false).isDehydrated())
    }
}
