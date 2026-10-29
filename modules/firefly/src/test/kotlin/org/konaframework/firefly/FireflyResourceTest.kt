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
    fun manageCreateDialogRendersRelationshipSelectCombobox() {
        databaseWithProjects()
        ProjectShelf().forceFill(mapOf("name" to "Desk")).save()
        ProjectShelf().forceFill(mapOf("name" to "Lab")).save()
        val tasks = object : Resource("task") {
            init {
                label("Task")
                simple()
                model(TaskShelf::class.java)
            }

            override fun form(schema: Schema): Schema =
                schema.components(Select.make("project_id").label("Project").relationship("project", "name").preload())

            override fun table(table: Table<*, *>): Table<*, *> =
                Table.memory(mapOf("id" to "1", "project_id" to "1")).columns(
                    TextColumn.make("project_id").relationship("project", "name"),
                )
        }
        val html = tasks.pages().single().render()
        assertTrue(html.contains("ff-combobox"))
        assertTrue(html.contains("data-ff-combobox"))
        assertTrue(html.contains("data-ff-combobox-value"))
        assertTrue(html.contains("""name="project_id""""))
        assertTrue(html.contains("""data-ff-relationship="project""""))
        assertTrue(html.contains("""data-ff-preload="true""""))
        assertTrue(html.contains("ff-option"))
        assertTrue(html.contains("""data-ff-label="Desk""""))
        assertTrue(html.contains("Desk"))
        assertTrue(html.contains("Lab"))
        assertTrue(html.contains(">Project<"))
        assertFalse(html.contains(">project_id<"))
        assertFalse(html.contains("<select"))
        val router = Router()
        Panel().path("admin").authenticate(false).resource(tasks).mount(router)
        val match = router.find("GET", "/admin/task/form-options") ?: throw AssertionError("missing form-options")
        val body = match.route.action.handle(
            Request("GET", "/admin/task/form-options", emptyMap(), mapOf("field" to "project_id", "q" to "Lab"), ""),
        ).body
        assertTrue(body.contains("Lab"))
        assertTrue(body.contains("\"value\""))
        assertTrue(body.contains("\"label\""))
        val miss = match.route.action.handle(
            Request("GET", "/admin/task/form-options", emptyMap(), mapOf("field" to "project_id", "q" to "Nope"), ""),
        ).body
        assertTrue(miss.contains("[]") || !miss.contains("Desk"))
        val blankField = match.route.action.handle(
            Request("GET", "/admin/task/form-options", emptyMap(), mapOf("q" to "Lab"), ""),
        ).body
        assertTrue(blankField.contains("[]"))
        val unknownField = match.route.action.handle(
            Request("GET", "/admin/task/form-options", emptyMap(), mapOf("field" to "missing", "q" to "Lab"), ""),
        ).body
        assertTrue(unknownField.contains("[]"))
        val noQuery = match.route.action.handle(
            Request("GET", "/admin/task/form-options", emptyMap(), mapOf("field" to "project_id"), ""),
        ).body
        assertTrue(noQuery.contains("Desk") || noQuery.contains("Lab") || noQuery.contains("["))
    }

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
        // Manage list is table-only — no standing #ff-form (create/edit live in dialogs).
        assertFalse(html.contains("""id="ff-form""""))
        assertFalse(html.contains("ff-resource-form"))
        val headerActions = html.substringAfter("""class="ff-page-header-actions"""").substringBefore("</div>")
        assertTrue(headerActions.contains("""data-ff-action="create""""))
        assertFalse(headerActions.contains("""data-ff-action="edit""""))
        assertFalse(headerActions.contains("""data-ff-action="view""""))
        assertFalse(headerActions.contains("""data-ff-action="delete""""))
        assertTrue(html.contains("ff-row-actions"))
        assertTrue(html.contains("ff-row-action-menu"))
        assertTrue(html.contains("ff-table-more"))
        assertTrue(html.contains("""data-ff-action="view""""))
        assertTrue(html.contains("""data-ff-action="edit""""))
        assertTrue(html.contains("""data-ff-action="delete""""))
        assertTrue(html.contains("ff-table-button-dialog"))
        assertTrue(html.contains("""data-ff-dialog-panel"""))
        // Create/edit dialogs use native POST forms (not Wire-only Submit).
        assertTrue(html.contains("""class="ff-action-form""""))
        assertTrue(html.contains("""method="post" action="create""""))
        assertTrue(html.contains("""method="post" action="edit""""))
        assertFalse(html.contains("""wire:click="create""""))
        assertFalse(html.contains("""wire:click="edit""""))
        // Field names must be flat (`name`), not nested under the action (`create.name`).
        assertTrue(html.contains("""name="name""""))
        assertFalse(html.contains("""name="create.name""""))
        assertFalse(html.contains("""name="edit.name""""))
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
        // Simple resource manage dialogs POST to create/edit store routes (no CreatePage/EditPage GET).
        assertNotNull(router.find("POST", "/admin/notes/create"))
        assertNotNull(router.find("POST", "/admin/notes/edit"))
        assertTrue(manage.contains("""method="post" action="create""""))
        assertTrue(list.contains("ff-list"))
        assertTrue(list.contains(">Orders<"))
        // Singular label() still renders a plural list/manage heading (Shamar).
        val users = object : Resource("user") {
            init {
                label("User")
                model(Shelf::class.java)
            }

            override fun table(table: Table<*, *>): Table<*, *> =
                Table.memory(mapOf("id" to "1")).columns(TextColumn.make("id"))
        }
        val userRouter = Router()
        Panel().path("desk").authenticate(false).resource(users).mount(userRouter)
        assertTrue(send(userRouter, "/desk/user/list").contains(">Users<"))
        assertTrue(list.contains("ff-breadcrumbs"))
        assertTrue(list.contains("""href="/admin""""))
        assertTrue(list.contains("""href="create""""))
        assertTrue(list.contains(">New<"))
        assertTrue(list.contains("ff-row-actions"))
        assertTrue(list.contains("ff-row-action-menu") || list.contains("ff-table-more"))
        assertTrue(list.contains("""href="view?id=1""""))
        assertTrue(list.contains("""href="edit?id=1""""))
        assertTrue(list.contains("Ada"))
        // Create is a page link on full resources — dialogs stay on simple/manage and delete.
        // Full-resource create is a header link — dialogs belong on simple/manage and delete.
        val listSection = list.substringAfter("""class="ff-page ff-list"""").substringBefore("</section>")
        val headerActions = listSection.substringAfter("""class="ff-page-header-actions"""").substringBefore("</div>")
        assertTrue(headerActions.contains("""href="create""""))
        assertTrue(headerActions.contains("New"))
        assertFalse(headerActions.contains("ff-dialog"))
        assertFalse(headerActions.contains("ff-action-dialog"))
        // Row delete mounts a dialog; it must stay closed so the list stays clickable.
        assertTrue(list.contains("ff-table-button-dialog"))
        assertTrue(list.contains("""data-ff-dialog-panel"""))
        assertTrue(list.contains("""ff-table-button-dialog" data-ff-dialog-panel x-show="open" x-cloak style="display:none" hidden"""))
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
        // Edit mounts relations only — must not nest them in an .ff-infolist card (#271).
        // Match the host element (stylesheet comments also mention .ff-infolist).
        assertFalse(edit.contains("""class="ff-infolist""""))
        assertTrue(edit.contains("data-ff-mode=\"edit\""))
        assertTrue(edit.contains("Edit Orders"))
        assertTrue(edit.contains("data-ff-title=\"sku\""))
        assertTrue(edit.contains("data-ff-tone=\"info\""))
        assertTrue(edit.contains("data-ff-color=\"danger\""))
        assertTrue(view.contains("ff-view"))
        assertTrue(view.contains("data-ff-mode=\"detail\""))
        assertTrue(view.contains("ff-relation"))
        assertTrue(view.contains("""class="ff-infolist""""))
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
        // A page-less resource still gets one entry, pointing at the resource root (plural nav label).
        assertTrue(list.contains("""<a href="/admin/empty">empties</a>"""))
        // Resources render as flat nav items, never CRUD clusters (no nav cluster markup).
        assertFalse(list.contains("""class="ff-nav-item ff-cluster""""))
        // Create lives in the page header actions (the list toolbar), not the sidebar.
        val header = list.substringAfter("ff-page-header")
        assertTrue(header.contains("New"))
        assertTrue(header.contains("""href="create""""))
        // Row actions sit on the table (⋯ menu by default), still not in the sidebar.
        assertTrue(list.contains("ff-row-actions"))
        assertTrue(list.contains("ff-row-action-menu"))
        assertTrue(list.contains("""href="view?id=1""""))
        assertTrue(list.contains("""href="edit?id=1""""))

        // The single entry stays active across the resource's create/edit/view pages.
        val create = send(router, "/admin/orders/create")
        assertTrue(create.contains("""<li class="ff-nav-item ff-active"><a href="/admin/orders/list">Orders</a>"""))
        assertTrue(send(router, "/admin/orders/edit").contains("ff-active"))
        assertTrue(send(router, "/admin/orders/view").contains("ff-active"))
        // Dashboard (`/admin`) must not stay highlighted on resource pages.
        assertFalse(list.contains("""<li class="ff-nav-item ff-active"><a href="/admin">Dashboard</a>"""))
        assertTrue(send(router, "/admin").contains("""<li class="ff-nav-item ff-active"><a href="/admin">Dashboard</a>"""))
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
        assertEquals("resources", Resource().pluralLabel())
        assertEquals("Notes", Resource().label("Notes").label())
        assertEquals("Notes", Resource().label("Notes").pluralLabel())
        assertEquals("Users", Resource().label("User").pluralLabel())
        assertEquals("People", Resource().label("User").pluralLabel("People").pluralLabel())
        assertEquals("Staff", Resource().label("User").pluralLabel { "Staff" }.pluralLabel())
        assertEquals("Desk", Resource().label { "Desk" }.label())
        assertEquals("Desks", Resource().label { "Desk" }.pluralLabel())
        assertEquals("Categories", englishPluralLabel("Category"))
        assertEquals("Boxes", englishPluralLabel("Box"))
        assertEquals("Buses", englishPluralLabel("Bus"))
        assertEquals("Orders", englishPluralLabel("Orders"))
        assertEquals("", englishPluralLabel(""))
        assertEquals("   ", englishPluralLabel("   "))
        assertEquals("As", englishPluralLabel("A"))
        assertEquals("Classes", englishPluralLabel("Class"))
        assertEquals("Crisises", englishPluralLabel("Crisis"))
        assertEquals("Days", englishPluralLabel("Day"))
        assertEquals("Branches", englishPluralLabel("Branch"))
        assertEquals("Dishes", englishPluralLabel("Dish"))
        assertEquals("Quizes", englishPluralLabel("Quiz"))
        assertEquals("Users", englishPluralLabel("User "))
        assertThrows<FireflyException> { Resource().label(" ") }
        assertThrows<FireflyException> { Resource().label { " " }.label() }
        assertThrows<FireflyException> { Resource().pluralLabel(" ") }
        assertThrows<FireflyException> { Resource().pluralLabel { " " }.pluralLabel() }
        assertThrows<FireflyException> { Resource().maxWidth("huge") }
        assertThrows<FireflyException> { Resource().formColumns(0) }
        assertThrows<FireflyException> { Resource().infolistColumns(0) }
        val grid = Resource("shelf").model(Shelf::class.java).formColumns(2).infolistColumns(3)
        assertEquals(2, grid.formColumns())
        assertEquals(3, grid.infolistColumns())
        // formColumns / infolistColumns apply when pages mount (prepareForm / prepareInfolist).
        val gridResource = object : Resource("shelf") {
            init {
                model(Shelf::class.java)
                formColumns(2)
                infolistColumns(2)
                simple()
            }

            override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

            override fun infolist(schema: Schema): Schema = schema.components(TextEntry.make("name"))

            override fun table(table: Table<*, *>): Table<*, *> =
                Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name"))
        }
        val gridRouter = Router()
        Panel().path("admin").authenticate(false).resource(gridResource).mount(gridRouter)
        val gridManage = send(gridRouter, "/admin/shelf/manage")
        assertTrue(gridManage.contains("ff-manage") || gridManage.contains("ff-row-actions"))
        val widthRouter = Router()
        Panel().path("admin").authenticate(false)
            .resource(Resource("shelf").model(Shelf::class.java).maxWidth("full").simple())
            .mount(widthRouter)
        assertTrue(
            send(widthRouter, "/admin/shelf/manage").contains("""class="ff-page ff-manage" data-ff-max-width="full""""),
        )
        val pageOverride = object : Resource("shelf") {
            init {
                model(Shelf::class.java)
                maxWidth("full")
                simple()
            }

            override fun getPages(): Map<String, Page> =
                mapOf("manage" to ManagePage("manage").maxWidth("sm"))
        }
        val overrideRouter = Router()
        Panel().path("admin").authenticate(false).resource(pageOverride).mount(overrideRouter)
        val overrideHtml = send(overrideRouter, "/admin/shelf/manage")
        assertTrue(overrideHtml.contains("""class="ff-page ff-manage" data-ff-max-width="sm""""))
        assertFalse(overrideHtml.contains("""class="ff-page ff-manage" data-ff-max-width="full""""))
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
        // Manage fills form/infolist per row dialog (not from page ?id= state).
        val late = object : LateForm() {
            override fun table(table: Table<*, *>): Table<*, *> =
                Table.memory(mapOf("id" to "Ada", "name" to "Note")).columns(TextColumn.make("name"))
        }
        val manage = late.pages().single()
        assertNull(late.seenRecord)
        assertNull(late.tenantAtForm)
        manage.render()
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

        // Filament-style getRelations() declaration (issue #249).
        val declared = object : Resource("orders") {
            override fun getRelations(): List<RelationManager> =
                listOf(RelationManager("lines").titleAttribute("sku"))
        }.label("Orders").model(Shelf::class.java)
        assertEquals(1, declared.relationManagers().size)
        assertEquals("lines", declared.relationManagers().single().name())
        // Merge fluent relation() with getRelations() (same name wins from declared).
        val merged = object : Resource("orders") {
            init {
                relation(RelationManager("lines").titleAttribute("old"))
                relation(RelationManager("items").titleAttribute("sku"))
            }

            override fun getRelations(): List<RelationManager> =
                listOf(RelationManager("lines").titleAttribute("sku"))
        }.label("Orders").model(Shelf::class.java)
        assertEquals(setOf("lines", "items"), merged.relationManagers().map { it.name() }.toSet())
        assertTrue(merged.relationManagers().first { it.name() == "lines" }.render().contains("""data-ff-title="sku""""))
        val edit = declared.pages().first { it is EditPage }
        val editHtml = edit.render(
            mapOf(
                "panelHome" to "/admin",
                "resourceList" to "/admin/orders/list",
            ),
        )
        assertTrue(editHtml.contains("ff-breadcrumbs"))
        assertTrue(editHtml.contains("""href="/admin""""))
        assertTrue(editHtml.contains("""href="/admin/orders/list""""))
        assertTrue(editHtml.contains("ff-relation"))
        assertThrows<FireflyException> {
            object : Resource("notes") {
                override fun getRelations(): List<RelationManager> = listOf(RelationManager("lines"))
            }.simple()
        }
    }

    @Test
    fun editAndViewFillRecordAndDeleteRemovesIt() {
        database()
        val shelf = Shelf().fill(mapOf("name" to "LoadedShelf"))
        shelf.save()
        val id = shelf.getKey()!!.toString()
        val router = Router()
        Panel().path("admin").authenticate(false).resource(orders()).mount(router)

        val edit = send(router, "/admin/orders/edit?id=$id")
        assertTrue(edit.contains("""value="LoadedShelf""""), edit)
        assertTrue(edit.contains("""name="id" value="$id""""), edit)
        assertTrue(edit.contains("data-ff-dialog-open"), edit)
        assertTrue(edit.contains("""action="delete""""), edit)

        val view = send(router, "/admin/orders/view?id=$id")
        assertTrue(view.contains("LoadedShelf"), view)
        assertTrue(view.contains("data-ff-mode=\"detail\""), view)

        val deleteMatch = router.find("POST", "/admin/orders/delete")!!
        assertEquals(
            422,
            deleteMatch.route.action.handle(
                Request("POST", "/admin/orders/delete", emptyMap(), emptyMap(), ""),
            ).status,
        )
        assertEquals(
            404,
            deleteMatch.route.action.handle(
                Request("POST", "/admin/orders/delete", emptyMap(), emptyMap(), "id=99999"),
            ).status,
        )
        val deleted = deleteMatch.route.action.handle(
            Request("POST", "/admin/orders/delete", emptyMap(), emptyMap(), "id=$id"),
        )
        assertEquals(302, deleted.status)
        assertEquals("/admin/orders/list", deleted.headers["Location"])
        assertEquals(0L, Shelf().newQuery().where("name", "LoadedShelf").count())

        // Missing Cadence row leaves the form empty (find → null); blank record id is ignored.
        val missing = send(router, "/admin/orders/edit?id=99999")
        assertTrue(missing.contains("ff-edit"))
        assertFalse(missing.contains("""value="LoadedShelf""""))
        send(router, "/admin/orders/edit?id=")
        val blankRecord = orders().pages().first { it is EditPage }.render(mapOf("record" to "  "))
        assertTrue(blankRecord.contains("ff-edit"))

        // Auth-gated delete redirects to login when signed out; proceeds when signed in.
        val gated = Router()
        val guard = MemoryPanelGuard()
        Panel().path("admin").authenticate(true).guard(guard).resource(orders()).mount(gated)
        assertEquals(
            302,
            gated.find("POST", "/admin/orders/delete")!!.route.action.handle(
                Request("POST", "/admin/orders/delete", emptyMap(), emptyMap(), "id=1"),
            ).status,
        )
        guard.register("Ada", "ada@example.com", "secret")
        guard.attempt("ada@example.com", "secret")
        assertEquals(
            404,
            gated.find("POST", "/admin/orders/delete")!!.route.action.handle(
                Request("POST", "/admin/orders/delete", emptyMap(), emptyMap(), "id=1"),
            ).status,
        )
        val session = FakeFireflySession().also { it.put("_token", "tok") }
        assertEquals(
            419,
            deleteMatch.route.action.handle(
                Request("POST", "/admin/orders/delete", emptyMap(), emptyMap(), "_token=bad&id=1").also {
                    it.setAttribute(Request.SESSION_ATTRIBUTE, session)
                },
            ).status,
        )
        val gatedDomain = Router()
        Panel().path("admin").authenticate(false).domain("admin.test").resource(orders()).mount(gatedDomain)
        assertEquals(
            404,
            gatedDomain.find("POST", "/admin/orders/delete")!!.route.action.handle(
                Request("POST", "/admin/orders/delete", emptyMap(), emptyMap(), "id=1"),
            ).status,
        )
    }

    @Test
    fun simpleManageCreateAndEditPostPersistAndRedirect() {
        database()
        val router = Router()
        Panel().path("admin").authenticate(false).resource(notes()).mount(router)
        val create = router.find("POST", "/admin/notes/create")!!
        val created = create.route.action.handle(
            Request("POST", "/admin/notes/create", emptyMap(), emptyMap(), "name=SimpleCreated"),
        )
        assertEquals(302, created.status)
        assertEquals("/admin/notes/manage", created.headers["Location"])
        val id = Shelf().newQuery().where("name", "SimpleCreated").value("id")!!.toString()
        val edit = router.find("POST", "/admin/notes/edit")!!
        val updated = edit.route.action.handle(
            Request("POST", "/admin/notes/edit", emptyMap(), emptyMap(), "id=$id&name=SimpleUpdated"),
        )
        assertEquals(302, updated.status)
        assertEquals("/admin/notes/manage", updated.headers["Location"])
        assertEquals(1L, Shelf().newQuery().where("name", "SimpleUpdated").count())
        // Legacy dotted action paths still map onto columns.
        val dotted = create.route.action.handle(
            Request(
                "POST",
                "/admin/notes/create",
                emptyMap(),
                emptyMap(),
                "create.name=DottedCreated",
            ),
        )
        assertEquals(302, dotted.status)
        assertEquals(1L, Shelf().newQuery().where("name", "DottedCreated").count())
        val editDotted = edit.route.action.handle(
            Request(
                "POST",
                "/admin/notes/edit",
                emptyMap(),
                emptyMap(),
                "id=$id&edit.name=EditDotted",
            ),
        )
        assertEquals(302, editDotted.status)
        assertEquals(1L, Shelf().newQuery().where("name", "EditDotted").count())
        // Manage + Create pages: do not double-register the create store route.
        val hybrid = Router()
        Panel().path("desk").authenticate(false).resource(
            object : Resource("mix") {
                init {
                    model(Shelf::class.java)
                }

                override fun getPages(): Map<String, Page> = mapOf(
                    "manage" to ManagePage("manage"),
                    "create" to CreatePage("create"),
                    "edit" to EditPage("edit"),
                )
            },
        ).mount(hybrid)
        assertNotNull(hybrid.find("POST", "/desk/mix/create"))
        assertNotNull(hybrid.find("POST", "/desk/mix/edit"))
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

private open class LateForm : Resource("notes") {
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

class ProjectShelf : Cadence<ProjectShelf>() {
    override val table = "ff_projects"
    override val timestamps = false
    override val fillable = setOf("name")
}

class TaskShelf : Cadence<TaskShelf>() {
    override val table = "ff_tasks"
    override val timestamps = false
    override val fillable = setOf("project_id", "name")

    fun project() = belongsTo(ProjectShelf::class.java, "project_id")
}

class Paper : org.konaframework.cadence.document.Document()

private fun database() {
    val url = "jdbc:h2:mem:ffres${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
    database.connection().statement("create table ff_shelves (id bigint auto_increment primary key, name varchar(100))")
    database.setAsGlobal()
}

private fun databaseWithProjects() {
    val url = "jdbc:h2:mem:ffresproj${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
    database.connection().statement(
        """
        create table ff_projects (id bigint auto_increment primary key, name varchar(100));
        create table ff_tasks (
            id bigint auto_increment primary key,
            project_id bigint,
            name varchar(100)
        )
        """.trimIndent(),
    )
    database.setAsGlobal()
}

