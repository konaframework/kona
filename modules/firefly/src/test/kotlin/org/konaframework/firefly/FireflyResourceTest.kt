package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelBuilder
import org.konaframework.firefly.forms.DateTimePicker
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router

class FireflyResourceTest {
    @Test
    fun simpleResourceKeepsCreateEditViewAndDeleteInDialogs() {
        val notes = notes()
        assertTrue(notes.form() != null)
        assertTrue(notes.infolist() != null)
        assertTrue(notes.table() != null)
        assertTrue(notes.isSimple())
        val pages = notes.pages()
        assertEquals(1, pages.size)
        assertTrue(pages[0] is ManagePage)
        assertEquals(1, notes.pages().size)
        val html = pages[0].render()
        assertTrue(html.contains("ff-manage"))
        assertTrue(html.contains("ff-dialog"))
        assertTrue(html.contains("ff-combobox"))
        assertTrue(html.contains("ff-picker"))
        assertTrue(html.contains("Ada"))
        assertFalse(html.contains("ff-relation"))
        assertThrows<FireflyException> { notes.relation(RelationManager("lines")) }
        assertThrows<FireflyException> {
            Resource("box").relation(RelationManager("lines")).simple()
        }
    }

    @Test
    fun fullResourcePagesExposeFormInfolistTableActionsAndRelations() {
        database()
        val orders = orders()
        assertFalse(orders.isSimple())
        val pages = orders.pages()
        assertEquals(listOf("list", "create", "edit", "view"), pages.map { it.slug() })
        val router = Router()
        Panel().path("admin").authenticate(false).resource(orders).resource(notes()).mount(router)
        val list = send(router, "/admin/orders/list")
        val create = send(router, "/admin/orders/create")
        val edit = send(router, "/admin/orders/edit")
        val view = send(router, "/admin/orders/view")
        val manage = send(router, "/admin/notes/manage")
        assertTrue(list.contains("ff-list"))
        assertTrue(list.contains("ff-dialog"))
        assertTrue(list.contains("Ada"))
        assertTrue(create.contains("ff-create"))
        assertTrue(create.contains("ff-combobox"))
        assertTrue(edit.contains("ff-relation"))
        assertTrue(edit.contains("data-ff-title=\"sku\""))
        assertTrue(edit.contains("data-ff-tone=\"info\""))
        assertTrue(view.contains("ff-view"))
        assertTrue(view.contains("ff-relation"))
        assertTrue(view.contains("data-ff-path=\"name\""))
        assertTrue(manage.contains("ff-picker"))
        val query = orders.query()
        assertTrue(query is ModelBuilder<*>)
        assertTrue((query as ModelBuilder<*>).toSql().contains("ff_shelves"))
        val related = orders.relationManagers().single().query()
        assertTrue(related is ModelBuilder<*>)
    }

    @Test
    fun modelTypeSelectsTheQuery() {
        database()
        val shelf = Resource("shelf").model(Shelf::class.java)
        assertEquals(Shelf::class.java, shelf.boundModel())
        assertTrue(shelf.query() is ModelBuilder<*>)
        val closed = Resource("shelf").model { Shelf::class.java }
        assertEquals(Shelf::class.java, closed.boundModel())
        assertThrows<FireflyException> { Resource("plain").model(String::class.java).query() }
        assertThrows<FireflyException> { Resource("paper").model(Paper::class.java).query() }
        assertThrows<FireflyException> { Resource("open").query() }
        val relation = RelationManager("lines").related(Shelf::class.java)
        assertTrue(relation.query() is ModelBuilder<*>)
        assertTrue(RelationManager("lines").related { Shelf::class.java }.query() is ModelBuilder<*>)
        assertThrows<FireflyException> { RelationManager("lines").query() }
        assertThrows<FireflyException> { RelationManager("lines").related(String::class.java).query() }
        assertThrows<FireflyException> { RelationManager("lines").related(Paper::class.java).query() }
    }

    @Test
    fun bareResourcesStillResolveLabelsAndSkipEmptyPages() {
        assertTrue(Resource().pages().isEmpty())
        assertNull(Resource().form())
        assertNull(Resource().infolist())
        assertNull(Resource().table())
        assertEquals("resource", Resource().label())
        assertEquals("Notes", Resource().label("Notes").label())
        assertEquals("Desk", Resource().label { "Desk" }.label())
        assertThrows<FireflyException> { Resource().label(" ") }
        assertThrows<FireflyException> { Resource().label { " " }.label() }
        assertThrows<FireflyException> { RelationManager(" ") }
        assertThrows<FireflyException> { RelationManager("lines").titleAttribute(" ") }
        assertThrows<FireflyException> { RelationManager("lines").titleAttribute { " " }.render() }
        val bare = Resource("box").model(Shelf::class.java)
        val generated = bare.pages()
        assertEquals(4, generated.size)
        assertFalse(generated[0].render().contains("ff-relation"))
        assertFalse(generated[2].render().contains("ff-relation"))
        val simple = Resource("box").simple().pages().single()
        assertTrue(simple.render().contains("ff-dialog"))
        assertFalse(simple.render().contains("ff-picker"))
        val custom = Resource("box").form(Schema.make().components(TextInput.make("name"))).page(CustomPage("extra"))
        assertEquals("extra", custom.pages().single().slug())
        assertEquals(1, custom.pages().size)
        Resource("box").simple(false)
        val explicit = Resource("box").model(Shelf::class.java).page(ListPage("all"))
        assertEquals(1, explicit.pages().size)
    }

    @Test
    fun relationManagerRendersTableFormAndAttributes() {
        val empty = RelationManager("lines")
        assertEquals("lines", empty.name())
        assertTrue(empty.render().contains("data-ff-title=\"id\""))
        assertFalse(empty.render().contains("ff-input"))
        val tableOnly = RelationManager("lines").titleAttribute("sku").table(
            Table.memory(mapOf("id" to "1", "sku" to "A")).columns(TextColumn.make("sku")),
        )
        assertTrue(tableOnly.render().contains(">A<") || tableOnly.render().contains("A"))
        val formOnly = RelationManager("lines").titleAttribute { "sku" }.form(
            Schema.make().components(TextInput.make("sku")),
        )
        assertTrue(formOnly.render().contains("ff-input"))
        val both = RelationManager("lines")
            .extraAttributes(mapOf("data-ff-tone" to "info"))
            .table(Table.memory(mapOf("id" to "2", "sku" to "B")).columns(TextColumn.make("sku")))
            .form(Schema.make().components(TextInput.make("sku")))
        assertTrue(both.render().contains("data-ff-tone=\"info\""))
        val closed = RelationManager("lines").extraAttributes { mapOf("data-ff-tone" to "info") }
        assertTrue(closed.render().contains("data-ff-tone=\"info\""))
    }

    private fun notes(): Resource = Resource("notes").label("Notes").simple()
        .model(Shelf::class.java)
        .form(
            Schema.make().components(
                TextInput.make("name"),
                Select.make("status").options(mapOf("open" to "Open")).extraInputAttributes(mapOf("data-ff-extra" to "1")),
                DateTimePicker.make("due"),
            ),
        )
        .infolist(Infolist.make().components(TextEntry.make("name")))
        .table(Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name")))

    private fun orders(): Resource {
        val lines = RelationManager("lines")
            .related(Shelf::class.java)
            .titleAttribute("sku")
            .extraAttributes(mapOf("data-ff-tone" to "info"))
            .table(Table.memory(mapOf("id" to "1", "sku" to "A")).columns(TextColumn.make("sku")))
        return Resource("orders").label("Orders")
            .model { Shelf::class.java }
            .form(
                Schema.make().components(
                    TextInput.make("name"),
                    Select.make("status").options(mapOf("open" to "Open")),
                    DateTimePicker.make("due"),
                ),
            )
            .infolist(Infolist.make().components(TextEntry.make("name")))
            .table(Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name")))
            .relation(lines)
    }

    private fun send(router: Router, path: String): String {
        val match = router.find("GET", path) ?: throw AssertionError("missing $path")
        val response: Response = match.route.action.handle(Request("GET", path, emptyMap(), emptyMap(), ""))
        assertEquals(200, response.status)
        return response.body
    }
}

class Shelf : Cadence<Shelf>() {
    override val table = "ff_shelves"
    override val timestamps = false
    override val fillable = setOf("name")
}

class Paper : org.konaframework.cadence.document.Document()

private fun database() {
    val url = "jdbc:h2:mem:ffres${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
    database.connection().statement("create table ff_shelves (id bigint auto_increment primary key, name varchar(100))")
    database.setAsGlobal()
}
