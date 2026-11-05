package org.konaframework.firefly.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.firefly.CreatePage
import org.konaframework.firefly.EditPage
import org.konaframework.firefly.ListPage
import org.konaframework.firefly.ManagePage
import org.konaframework.firefly.Resource
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.widgets.Page

class FireflyLivewireTest {
    @Test
    fun fillFormSaveAndAssertErrorsAndNotifications() {
        database()
        val resource = ItemsResource()
        Firefly.livewire(resource, CreatePage::class.java)
            .fillForm(mapOf("name" to ""))
            .call("save")
            .assertHasFormErrors()
            .assertHasFormErrors("name")
            .assertNotified("Please fix the form")
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).call("save").assertHasNoFormErrors()
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).fillForm(mapOf("name" to "")).call("save")
                .assertHasNoFormErrors("name")
        }

        val created = Firefly.livewire(resource, CreatePage())
            .setForm(mapOf("name" to "Ada"))
            .call("save")
            .assertHasNoFormErrors()
            .assertHasNoFormErrors("name")
            .assertNotified()
            .assertNotified("Created")
            .assertFormField("name")
        assertNotNull(created.record())
        assertEquals("Ada", (created.record() as Item).getAttribute("name"))
        assertEquals(mapOf("name" to "Ada"), created.formData())
        assertTrue(created.formErrors().isEmpty())
        assertEquals("Created", created.notification())
        assertNull(created.actor())
        assertNotNull(created.page())
        assertEquals(resource, created.resource())
        assertTrue(created.html().contains("name"))

        val key = (created.record() as Item).getKey()!!
        Firefly.livewire(resource, EditPage(), key)
            .actingAs("cashier")
            .fillForm(mapOf("name" to "Ada Lovelace"))
            .call("save")
            .assertHasNoFormErrors()
            .assertNotified("Saved")
        assertEquals("cashier", Firefly.livewire(resource, EditPage::class.java, key).actingAs("cashier").actor())
        assertEquals("Ada Lovelace", Item.find(key)!!.getAttribute("name"))

        // Edit with a missing key falls through to a new record; Number keys hit recordNeedle.
        Firefly.livewire(resource, EditPage::class.java, 9_999_999L)
            .fillForm(mapOf("name" to "Ghost"))
            .call("save")
            .assertHasNoFormErrors()
            .assertNotified("Saved")
        // EditPage with no record: resolveRecord edit + null key.
        Firefly.livewire(resource, EditPage::class.java)
            .fillForm(mapOf("name" to "Orphan"))
            .call("save")
            .assertHasNoFormErrors()

        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).call("publish")
        }
        assertThrows(IllegalStateException::class.java) {
            Firefly.livewire(ListOnlyResource(), ListPage::class.java).call("save")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).call("save").assertNotified("Nope")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).assertNotified()
        }
        Firefly.livewire(resource, CreatePage::class.java).assertNotNotified()
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).fillForm(mapOf("name" to "x")).call("save")
                .assertNotNotified()
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).fillForm(mapOf("name" to "Valid"))
                .call("save")
                .assertHasFormErrors()
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).fillForm(mapOf("name" to "")).call("save")
                .assertHasFormErrors("missing")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).assertSee("___missing___")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).assertDontSee("name")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(resource, CreatePage::class.java).assertFormField("nope")
        }
        Firefly.livewire(resource, CreatePage::class.java).assertSee("name").assertDontSee("___missing___")
    }

    @Test
    fun tableRecordAndActionAssertions() {
        database()
        Item().forceFill(mapOf("name" to "Ada")).save()
        Item().forceFill(mapOf("name" to "Bob")).save()
        val ada = Item.where("name", "Ada").first()!!
        val bob = Item.where("name", "Bob").first()!!
        val open = ItemsResource()
        Firefly.livewire(open, ListPage::class.java)
            .assertCanSeeTableRecords(ada, bob)
            .assertTableActionVisible("edit", ada)
            .assertTableActionVisible("view")
            .assertSee("Ada")
        assertNotNull(Firefly.livewire(open, ListPage::class.java).table())
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(open, ListPage::class.java).assertCannotSeeTableRecords(ada)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(open, ListPage::class.java).assertCanSeeTableRecords("999")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(open, ListPage::class.java).assertTableActionHidden("edit", ada)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(open, ListPage::class.java).assertTableActionVisible("nope", ada)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(open, ListPage::class.java).assertTableActionVisible("nope")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(open, ListPage::class.java).assertTableActionHidden("edit")
        }

        val locked = LockedDeleteResource()
        Firefly.livewire(locked, ListPage::class.java)
            .assertTableActionHidden("delete", bob)
            .assertTableActionHidden("delete")
            .assertCannotSeeTableRecords("999")
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(locked, ListPage::class.java).assertTableActionVisible("delete", bob)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(open, ListPage::class.java).assertTableActionHidden("delete", "999")
        }

        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(open, org.konaframework.firefly.ViewPage::class.java)
        }

        // rowHtml falls back when the marker has no wrapping <tr>…</tr>.
        val detached = Firefly.livewire(open, ListPage::class.java)
        val htmlField = LivewireTestable::class.java.getDeclaredField("html")
        htmlField.isAccessible = true
        val rowHtml = LivewireTestable::class.java.getDeclaredMethod("rowHtml", String::class.java)
        rowHtml.isAccessible = true
        val key = ada.getKey()!!.toString()
        val noTr = """<div data-ff-record="$key" data-ff-action="edit">row</div>"""
        htmlField.set(detached, noTr)
        assertEquals(noTr, rowHtml.invoke(detached, key))
        val openTr = """<tr data-ff-record="$key" data-ff-action="edit">row"""
        htmlField.set(detached, openTr)
        assertEquals(openTr, rowHtml.invoke(detached, key))

        val isKeyRef = LivewireTestable::class.java.getDeclaredMethod("isKeyRef", Any::class.java)
        isKeyRef.isAccessible = true
        assertTrue(isKeyRef.invoke(detached, null) as Boolean)
        assertTrue(isKeyRef.invoke(detached, "1") as Boolean)
        assertTrue(isKeyRef.invoke(detached, 1L) as Boolean)
        assertTrue(isKeyRef.invoke(detached, 2) as Boolean)
        assertTrue(!(isKeyRef.invoke(detached, ada) as Boolean))
    }

    @Test
    fun staticRenderAssertsCoverDontSeeAndHtml() {
        val html = """<div class="ff-table" data-ff-path="sku">Ada</div><div class="ff-dialog">Export</div>"""
        Firefly.test(html).assertSee("Ada").assertDontSee("missing").assertFormField("sku").assertTable("Ada")
            .assertDialog("Export")
        assertEquals(html, Firefly.test(html).html())
        assertThrows(AssertionError::class.java) { Firefly.test(html).assertDontSee("Ada") }
    }

    @Test
    fun managePageSaveAndCadenceRecordKeys() {
        database()
        val managed = SimpleItemsResource()
        Firefly.livewire(managed, ManagePage::class.java)
            .fillForm(mapOf("name" to "Cy"))
            .call("save")
            .assertHasNoFormErrors()
            .assertNotified("Created")
        val cy = Item.where("name", "Cy").first()!!
        Firefly.livewire(managed, ManagePage::class.java, cy)
            .fillForm(mapOf("name" to "Cyndi"))
            .call("save")
            .assertNotified("Saved")
        Firefly.livewire(ItemsResource(), ListPage::class.java)
            .assertCanSeeTableRecords(cy.getKey()!!.toString(), cy.getKey()!!, cy)
            .assertCannotSeeTableRecords(mapOf("id" to "nope"))
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(ItemsResource(), ListPage::class.java).assertNotified("x")
        }
        Firefly.livewire(ItemsResource(), CreatePage::class.java)
            .fillForm(mapOf("name" to "Zed"))
            .call("save")
            .assertNotified("Created")
        assertThrows(IllegalArgumentException::class.java) {
            Firefly.livewire(ItemsResource(), CreatePage::class.java)
                .fillForm(mapOf("name" to "Zed2"))
                .call("save")
                .assertNotified("Saved")
        }

        // No model: validate succeeds; persist is skipped.
        Firefly.livewire(NoModelResource(), CreatePage())
            .fillForm(mapOf("name" to "Solo"))
            .call("save")
            .assertHasNoFormErrors()
            .assertNotified("Created")

        // Cadence with a null key falls back to toString in recordNeedle.
        val blank = Item()
        Firefly.livewire(ItemsResource(), ListPage::class.java)
            .assertCannotSeeTableRecords(blank)
    }
}

private class NoModelResource : Resource("nomodel") {
    override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

    override fun getPages(): Map<String, Page> = mapOf("create" to CreatePage("create"))
}

private class ItemsResource : Resource("items") {
    init {
        label("Item")
        model(Item::class.java)
    }

    override fun form(schema: Schema): Schema = schema.components(TextInput.make("name").required())

    override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name"))

    override fun getPages(): Map<String, Page> = mapOf(
        "list" to ListPage("list"),
        "create" to CreatePage("create"),
        "edit" to EditPage("edit"),
    )
}

private class LockedDeleteResource : Resource("items") {
    init {
        label("Item")
        model(Item::class.java)
    }

    override fun canDelete(record: Any): Boolean = false

    override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

    override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name"))

    override fun getPages(): Map<String, Page> = mapOf("list" to ListPage("list"))
}

private class ListOnlyResource : Resource("plain") {
    init {
        model(Item::class.java)
    }

    override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name"))

    override fun getPages(): Map<String, Page> = mapOf("list" to ListPage("list"))
}

private class SimpleItemsResource : Resource("items") {
    init {
        label("Item")
        model(Item::class.java)
        simple()
    }

    override fun form(schema: Schema): Schema = schema.components(TextInput.make("name").required())

    override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name"))
}

class Item : Cadence<Item>() {
    override val table = "ff_livewire_items"
    override val timestamps = false
    override val fillable = setOf("name")

    companion object : org.konaframework.cadence.CadenceCompanion<Item>(Item::class.java)
}

private fun database() {
    val url = "jdbc:h2:mem:fflivewire${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
    database.connection().statement(
        "create table ff_livewire_items (id bigint auto_increment primary key, name varchar(100))",
    )
    database.setAsGlobal()
}
