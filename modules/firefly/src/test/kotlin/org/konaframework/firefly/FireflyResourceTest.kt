package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.widgets.Dashboard
import org.konaframework.firefly.widgets.Page
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router

class FireflyResourceTest {
    @Test
    fun simpleResourceKeepsCreateEditViewAndDeleteInDialogs() {
        val notes = notes()
        assertTrue(notes.form(Schema.make()).components().isNotEmpty())
        assertTrue(notes.infolist(Schema.make()).components().isNotEmpty())
        assertTrue(notes.table(Table.memory()).render().contains("Ada"))
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
        assertTrue(list.contains(">Orders<"))
        assertTrue(list.contains("""href="create""""))
        assertTrue(list.contains(">New<"))
        assertTrue(list.contains("ff-row-actions"))
        assertTrue(list.contains("""href="view""""))
        assertTrue(list.contains("""href="edit""""))
        assertTrue(list.contains("Ada"))
        // Create is a page link on full resources — dialogs stay on simple/manage and delete.
        // Full-resource create is a header link — dialogs belong on simple/manage and delete.
        val listSection = list.substringAfter("""class="ff-page ff-list"""").substringBefore("</section>")
        val headerActions = listSection.substringAfter("""class="ff-page-header-actions"""").substringBefore("</div>")
        assertTrue(headerActions.contains("""href="create""""))
        assertTrue(headerActions.contains("New"))
        assertFalse(headerActions.contains("ff-dialog"))
        assertFalse(headerActions.contains("ff-action-dialog"))
        assertTrue(create.contains("ff-create"))
        assertTrue(create.contains("ff-page-mode"))
        assertTrue(create.contains("data-ff-mode=\"create\""))
        assertTrue(create.contains("New Orders"))
        assertTrue(create.contains("ff-combobox"))
        assertTrue(create.contains("""id="ff-form""""))
        assertTrue(create.contains("""method="post""""))
        assertTrue(create.contains("""type="submit""""))
        assertTrue(create.contains("""form="ff-form""""))
        assertTrue(create.contains("""href="list""""))
        assertTrue(create.contains("Cancel"))
        assertTrue(list.contains("ff-table-toolbar") || list.contains("ff-table-search"))
        assertTrue(list.contains("ff-pagination") || list.contains("ff-table"))
        assertTrue(edit.contains("ff-relation"))
        assertTrue(edit.contains("data-ff-mode=\"edit\""))
        assertTrue(edit.contains("Edit Orders"))
        assertTrue(edit.contains("data-ff-title=\"sku\""))
        assertTrue(edit.contains("data-ff-tone=\"info\""))
        assertTrue(edit.contains("data-ff-color=\"danger\""))
        assertTrue(view.contains("ff-view"))
        assertTrue(view.contains("data-ff-mode=\"detail\""))
        assertTrue(view.contains("ff-relation"))
        assertTrue(view.contains("data-ff-path=\"name\""))
        assertTrue(view.contains("Back to list"))
        assertTrue(manage.contains("ff-picker"))
        assertTrue(manage.contains("ff-dialog"))
        val query = orders.query()
        assertTrue(query is ModelBuilder<*>)
        assertTrue((query as ModelBuilder<*>).toSql().contains("ff_shelves"))
        val related = orders.relationManagers().single().query()
        assertTrue(related is ModelBuilder<*>)
    }

    @Test
    fun resourcesGetOneSidebarEntryAndCrudStaysInPageActions() {
        database()
        val router = Router()
        val tools = object : Resource("tools") {
            init {
                model { Shelf::class.java }
            }

            override fun getPages(): Map<String, Page> = mapOf("panel" to CustomPage("panel"))
        }
        val home = object : Resource("home") {
            init {
                model { Shelf::class.java }
            }

            override fun getPages(): Map<String, Page> = mapOf("" to PanelPage(""))
        }
        val emptyPages = object : Resource("empty") {
            override fun getPages(): Map<String, Page> = emptyMap()
        }
        val panel = Panel().path("admin").authenticate(false).navigation(NavigationType.SIDE)
            .resource(orders())
            .resource(notes())
            .resource(tools)
            .resource(home)
            .resource(emptyPages)
        panel.mount(router)

        // One sidebar entry per resource, linking to the list/manage/index page.
        val list = send(router, "/admin/orders/list")
        assertTrue(list.contains("""<a href="/admin/orders/list">Orders</a>"""))
        assertEquals(1, countOf(list, """href="/admin/orders/list""""))
        // Create / edit / view are NEVER standalone sidebar links.
        assertFalse(list.contains("""href="/admin/orders/create""""))
        assertFalse(list.contains("""href="/admin/orders/edit""""))
        assertFalse(list.contains("""href="/admin/orders/view""""))
        // Simple resource links to its manage page; custom/empty resources link to their index.
        assertTrue(list.contains("""<a href="/admin/notes/manage">Notes</a>"""))
        assertTrue(list.contains("""href="/admin/tools/panel""""))
        assertTrue(list.contains("""href="/admin/home""""))
        // A page-less resource still gets one entry, pointing at the resource root.
        assertTrue(list.contains("""<a href="/admin/empty">empty</a>"""))
        // Resources render as flat nav items, never CRUD clusters (no nav cluster markup).
        assertFalse(list.contains("""class="ff-nav-item ff-cluster""""))
        // Create lives in the page header actions (the list toolbar), not the sidebar.
        val header = list.substringAfter("ff-page-header")
        assertTrue(header.contains("New"))
        assertTrue(header.contains("""href="create""""))
        // Row actions sit on the table, still not in the sidebar.
        assertTrue(list.contains("ff-row-actions"))
        assertTrue(list.contains("""class="ff-table-button" href="view""""))
        assertTrue(list.contains("""class="ff-table-button" href="edit""""))

        // The single entry stays active across the resource's create/edit/view pages.
        val create = send(router, "/admin/orders/create")
        assertTrue(create.contains("""<li class="ff-nav-item ff-active"><a href="/admin/orders/list">Orders</a>"""))
        assertTrue(send(router, "/admin/orders/edit").contains("ff-active"))
        assertTrue(send(router, "/admin/orders/view").contains("ff-active"))
        // Empty-slug resource pages mount at the resource root.
        assertEquals(200, router.find("GET", "/admin/home")!!.route.action.handle(Request("GET", "/admin/home", emptyMap(), emptyMap(), "")).status)
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
    fun bareResourcesStillResolveLabelsAndDefaultPages() {
        val bareResource = Resource()
        val barePages = bareResource.pages()
        assertEquals(listOf("list", "create", "edit", "view"), barePages.map { it.slug() })
        assertTrue(bareResource.pages().first() === barePages.first())
        assertTrue(Resource().form(Schema.make()).components().isEmpty())
        assertTrue(Resource().infolist(Schema.make()).components().isEmpty())
        assertTrue(Resource().table(Table.memory()).render().contains("ff-table"))
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
        val custom = object : Resource("box") {
            override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

            override fun getPages(): Map<String, Page> = mapOf("extra" to CustomPage("extra"))
        }
        val extra = custom.pages().single()
        assertEquals("extra", extra.slug())
        assertEquals(1, custom.pages().size)
        assertFalse(extra.render().contains("ff-input"))
        assertEquals(4, Resource("box").simple(false).pages().size)
        val explicit = object : Resource("box") {
            override fun getPages(): Map<String, Page> = mapOf("all" to ListPage("all"))
        }
        explicit.model(Shelf::class.java)
        assertEquals(1, explicit.pages().size)
        val late = LateForm()
        val manage = late.pages().single()
        assertNull(late.seenRecord)
        assertNull(late.tenantAtForm)
        manage.render(mapOf("record" to "Ada"))
        assertEquals("Ada", late.seenRecord)
        assertNull(late.tenantAtForm)
        FireflyTenant.use("acme") { manage.render() }
        assertEquals("acme", late.tenantAtForm)
        val home = object : Resource("box") {
            override fun getPages(): Map<String, Page> = mapOf("" to PanelPage(""))
        }
        assertEquals("", home.pages().single().slug())
        assertThrows<FireflyException> { NotAPanelPage().pages() }
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

    @Test
    fun createPostPersistsRecordAndRedirectsToList() {
        database()
        val router = Router()
        Panel().path("admin").authenticate(false).resource(orders()).mount(router)
        assertNotNull(router.find("POST", "/admin/orders/create"))
        val match = router.find("POST", "/admin/orders/create")!!
        val response = match.route.action.handle(
            Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "name=CreatedShelf"),
        )
        assertEquals(302, response.status)
        assertEquals("/admin/orders/list", response.headers["Location"])
        assertEquals(1L, Shelf().newQuery().where("name", "CreatedShelf").count())
        val createGet = send(router, "/admin/orders/create")
        assertTrue(createGet.contains("""id="ff-form""""))
        assertTrue(createGet.contains("""type="submit""""))

        // CSRF: session token present requires a matching field.
        val session = FakeFireflySession()
        session.put("_token", "good-token")
        val badCsrf = match.route.action.handle(
            Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "name=Nope").also {
                it.setAttribute(Request.SESSION_ATTRIBUTE, session)
            },
        )
        assertEquals(419, badCsrf.status)
        val wrongCsrf = match.route.action.handle(
            Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "_token=nope&name=Nope").also {
                it.setAttribute(Request.SESSION_ATTRIBUTE, session)
            },
        )
        assertEquals(419, wrongCsrf.status)
        val goodCsrf = match.route.action.handle(
            Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "_token=good-token&name=SafeShelf").also {
                it.setAttribute(Request.SESSION_ATTRIBUTE, session)
            },
        )
        assertEquals(302, goodCsrf.status)

        // Edit POST needs an id; missing → 422, unknown → 404, known → update + redirect.
        val editMatch = router.find("POST", "/admin/orders/edit")!!
        assertEquals(
            422,
            editMatch.route.action.handle(
                Request("POST", "/admin/orders/edit", emptyMap(), emptyMap(), "name=X"),
            ).status,
        )
        assertEquals(
            404,
            editMatch.route.action.handle(
                Request("POST", "/admin/orders/edit", emptyMap(), emptyMap(), "id=99999&name=X"),
            ).status,
        )
        val created = Shelf().newQuery().where("name", "CreatedShelf").first()!!
        val id = created.getKey()!!.toString()
        val edited = editMatch.route.action.handle(
            Request("POST", "/admin/orders/edit", emptyMap(), emptyMap(), "id=$id&name=RenamedShelf"),
        )
        assertEquals(302, edited.status)
        assertEquals("/admin/orders/list", edited.headers["Location"])
        assertEquals(1L, Shelf().newQuery().where("name", "RenamedShelf").count())

        // List query params drive search/sort/page (and ignore bad values).
        val listed = send(router, "/admin/orders/list?search=Renamed&sort=name&direction=desc&page=1&perPage=10")
        assertTrue(listed.contains("ff-table"))
        send(router, "/admin/orders/list?search=x&sort=missing&direction=sideways&page=0&perPage=0")
        send(router, "/admin/orders/list?page=abc&perPage=abc")

        // CSRF via header / alternate field; blank session token is ignored.
        val blankTok = FakeFireflySession().also { it.put("_token", "  ") }
        assertEquals(
            302,
            match.route.action.handle(
                Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "name=BlankTok").also {
                    it.setAttribute(Request.SESSION_ATTRIBUTE, blankTok)
                },
            ).status,
        )
        val headerOk = FakeFireflySession().also { it.put("_token", "hdr") }
        assertEquals(
            302,
            match.route.action.handle(
                Request(
                    "POST",
                    "/admin/orders/create",
                    mapOf("X-CSRF-TOKEN" to "hdr"),
                    emptyMap(),
                    "name=HeaderShelf",
                ).also { it.setAttribute(Request.SESSION_ATTRIBUTE, headerOk) },
            ).status,
        )
        val altField = FakeFireflySession().also { it.put("_token", "alt") }
        assertEquals(
            302,
            match.route.action.handle(
                Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "csrf-token=alt&name=AltShelf").also {
                    it.setAttribute(Request.SESSION_ATTRIBUTE, altField)
                },
            ).status,
        )

        // Create GET with session injects csrf field; query record id is accepted.
        val getSession = FakeFireflySession()
        val createWithCsrf = router.find("GET", "/admin/orders/create")!!.route.action.handle(
            Request("GET", "/admin/orders/create", emptyMap(), emptyMap(), "").also {
                it.setAttribute(Request.SESSION_ATTRIBUTE, getSession)
            },
        )
        assertEquals(200, createWithCsrf.status)
        assertTrue(createWithCsrf.body.contains("""name="_token""""))
        send(router, "/admin/orders/edit?id=$id&record=$id")

        // Resource whose columns are not searchable/sortable still accepts query params safely.
        val locked = Router()
        Panel().path("admin").authenticate(false).resource(LockedColumnsResource()).mount(locked)
        send(locked, "/admin/orders/list?search=x&sort=name&direction=desc&page=2&perPage=5")
        send(locked, "/admin/orders/list?sort=name") // direction defaults to asc

        // Auth-required panel redirects create POST to login when unauthenticated.
        val gated = Router()
        Panel().path("secure").resource(orders()).mount(gated)
        val gatedResp = gated.find("POST", "/secure/orders/create")!!.route.action.handle(
            Request("POST", "/secure/orders/create", emptyMap(), emptyMap(), "name=Nope"),
        )
        assertEquals(302, gatedResp.status)
        assertTrue(gatedResp.headers["Location"]!!.contains("login"))
        // Authenticated create POST proceeds (authenticatePages && check() both true).
        val open = object : PanelGuard by MemoryPanelGuard() {
            override fun check(): Boolean = true
            override fun id(): String? = "1"
        }
        val authed = Router()
        Panel().path("secure").guard(open).resource(orders()).mount(authed)
        assertEquals(
            302,
            authed.find("POST", "/secure/orders/create")!!.route.action.handle(
                Request("POST", "/secure/orders/create", emptyMap(), emptyMap(), "name=AuthedShelf"),
            ).status,
        )

        // Non-string session token is treated as absent (no CSRF reject).
        val weird = FakeFireflySession().also { it.put("_token", 42) }
        assertEquals(
            302,
            match.route.action.handle(
                Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "name=WeirdTok").also {
                    it.setAttribute(Request.SESSION_ATTRIBUTE, weird)
                },
            ).status,
        )
        val emptyTok = FakeFireflySession().also { it.put("_token", "") }
        assertEquals(
            302,
            match.route.action.handle(
                Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "name=EmptyTok").also {
                    it.setAttribute(Request.SESSION_ATTRIBUTE, emptyTok)
                },
            ).status,
        )

        // Edit accepts `record=` and ignores underscore-prefixed fields; create-only resource redirects to slug.
        assertEquals(
            302,
            editMatch.route.action.handle(
                Request(
                    "POST",
                    "/admin/orders/edit",
                    emptyMap(),
                    emptyMap(),
                    "record=$id&_method=PUT&name=RecordParamShelf",
                ),
            ).status,
        )
        val createOnly = Router()
        Panel().path("admin").authenticate(false).resource(CreateOnlyResource()).mount(createOnly)
        val createOnlyPost = createOnly.find("POST", "/admin/orders/create")!!.route.action.handle(
            Request("POST", "/admin/orders/create", emptyMap(), emptyMap(), "name=OnlyCreate"),
        )
        assertEquals(302, createOnlyPost.status)
        assertEquals("/admin/orders", createOnlyPost.headers["Location"])

        // Domain gate rejects mismatched hosts on create POST.
        val domainPanel = Router()
        Panel().path("ops").domain("ops.example").authenticate(false).resource(orders()).mount(domainPanel)
        assertEquals(
            404,
            domainPanel.find("POST", "/ops/orders/create")!!.route.action.handle(
                Request("POST", "/ops/orders/create", mapOf("Host" to "wrong.example"), emptyMap(), "name=X"),
            ).status,
        )
    }

    private fun notes(): Resource = NotesResource()

    private fun orders(): Resource = OrdersResource()

    private fun countOf(haystack: String, needle: String): Int {
        var count = 0
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            count++
            index = haystack.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun send(router: Router, path: String): String {
        val uri = path.substringBefore('?')
        val query = path.substringAfter('?', "")
        val params = if (query.isEmpty()) {
            emptyMap()
        } else {
            query.split('&').filter { it.isNotBlank() }.associate { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) pair to "" else pair.substring(0, idx) to pair.substring(idx + 1)
            }
        }
        val match = router.find("GET", uri) ?: throw AssertionError("missing $uri")
        val response: Response = match.route.action.handle(Request("GET", uri, emptyMap(), params, ""))
        assertEquals(200, response.status)
        return response.body
    }
}

private class FakeFireflySession : org.konaframework.http.RequestSession {
    private val values = LinkedHashMap<String, Any?>()
    override fun get(key: String): Any? = values[key]
    override fun put(key: String, value: Any?): org.konaframework.http.RequestSession {
        values[key] = value
        return this
    }
    override fun flash(key: String, value: Any?): org.konaframework.http.RequestSession = put(key, value)
    override fun flashInput(input: Map<String, String>): org.konaframework.http.RequestSession = this
    override fun old(key: String, default: String?): String? = default
    override fun oldInput(): Map<String, String> = emptyMap()
    override fun previousUrl(): String? = null
    override fun setPreviousUrl(url: String): org.konaframework.http.RequestSession = this
}

private class LockedColumnsResource : Resource("orders") {
    init {
        label("Orders")
        model(Shelf::class.java)
    }

    override fun table(table: Table<*, *>): Table<*, *> =
        Table.memory(mapOf("id" to "1", "name" to "Ada"))
            .columns(TextColumn.make("name").searchable(false).sortable(false))
}

private class CreateOnlyResource : Resource("orders") {
    init {
        label("Orders")
        model(Shelf::class.java)
    }

    override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

    override fun getPages(): Map<String, Page> = mapOf("create" to CreatePage("create"))
}

private class NotesResource : Resource("notes") {
    init {
        label("Notes")
        simple()
        model(Shelf::class.java)
    }

    override fun form(schema: Schema): Schema = schema.components(
        TextInput.make("name"),
        Select.make("status").options(mapOf("open" to "Open")).extraInputAttributes(mapOf("data-ff-extra" to "1")),
        DateTimePicker.make("due"),
    )

    override fun infolist(schema: Schema): Schema = schema.components(TextEntry.make("name"))

    override fun table(table: Table<*, *>): Table<*, *> =
        Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name"))
}

private class OrdersResource : Resource("orders") {
    init {
        label("Orders")
        model { Shelf::class.java }
        relation(
            RelationManager("lines")
                .related(Shelf::class.java)
                .titleAttribute("sku")
                .extraAttributes(mapOf("data-ff-tone" to "info"))
                .table(Table.memory(mapOf("id" to "1", "sku" to "A")).columns(TextColumn.make("sku"))),
        )
    }

    override fun form(schema: Schema): Schema = schema.components(
        TextInput.make("name"),
        Select.make("status").options(mapOf("open" to "Open")),
        DateTimePicker.make("due"),
    )

    override fun infolist(schema: Schema): Schema = schema.components(TextEntry.make("name"))

    override fun table(table: Table<*, *>): Table<*, *> =
        Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name"))

    override fun getPages(): Map<String, Page> = mapOf(
        "list" to ListPage("list"),
        "create" to CreatePage("create"),
        "edit" to EditPage("edit"),
        "view" to ViewPage("view"),
    )
}

private class LateForm : Resource("notes") {
    var seenRecord: Any? = null
    var tenantAtForm: String? = null

    init {
        simple()
    }

    override fun form(schema: Schema): Schema {
        seenRecord = schema.record()
        tenantAtForm = FireflyTenant.id()
        return schema.components(TextInput.make("name"))
    }
}

private class NotAPanelPage : Resource("dash") {
    override fun getPages(): Map<String, Page> = mapOf("home" to Dashboard())
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

