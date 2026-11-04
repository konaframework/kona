package org.konaframework.firefly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.auth.authorization.Gate
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.SoftDeletes
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.actions.ActionFailure
import org.konaframework.firefly.actions.ActionHalt
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.notifications.FireflyNotification
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.BulkAction
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.widgets.Page
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.net.URLEncoder

/** Record actions with server handlers (#467): edit / view header actions, table row actions, relation managers. */
class RecordActionsTest {
    private val log = ArrayList<String>()

    @BeforeEach
    fun seed() {
        val url = "jdbc:h2:mem:ffra${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
        val connection = database.connection()
        connection.statement("create table ra_entries (id bigint auto_increment primary key, name varchar(100), state varchar(20), tenant_id varchar(20))")
        connection.statement("create table rm_projects (id bigint auto_increment primary key, name varchar(100), team varchar(20))")
        connection.statement(
            "create table rm_tasks (id bigint auto_increment primary key, project_id bigint, name varchar(100), sort_order int, deleted_at timestamp)",
        )
        database.setAsGlobal()
        log.clear()
    }

    @AfterEach
    fun clear() {
        Firefly.authorizeActionsWith(null)
    }

    // ——— Header actions on edit / view ———

    @Test
    fun editHeaderShowsExtraActionsNextToTheDefaults() {
        val draft = entry("Draft entry", "draft")
        val posted = entry("Posted entry", "posted")
        val router = mount(entries())
        val session = RmSession()
        val html = get(router, "/admin/entries/edit", mapOf("id" to draft.key()), session)
        val header = html.let(::headerOf)
        val save = header.indexOf("""form="ff-form"""")
        val post = header.indexOf("""action="/admin/entries/actions/post"""")
        val view = header.indexOf("""href="view?id=${draft.key()}"""")
        val cancel = header.indexOf("""href="list"""")
        val delete = header.indexOf("""data-ff-action="delete"""")
        assertTrue(save in 0 until post, header)
        assertTrue(post < view && view < cancel && cancel < delete, header)
        // Handler actions post to the record actions route with CSRF, the record id, and the return location.
        val token = session.get("_token") as String
        assertTrue(header.contains("""<input type="hidden" name="_token" value="$token" />"""))
        assertTrue(header.contains("""<input type="hidden" name="id" value="${draft.key()}" />"""))
        assertTrue(header.contains("""<input type="hidden" name="_return" value="/admin/entries/edit?id=${draft.key()}" />"""))
        assertTrue(header.contains("Post entry"))
        // Dialog action: the schema renders as the dialog form posting to its own route.
        assertTrue(header.contains("""action="/admin/entries/actions/rename""""))
        assertTrue(header.contains("""name="new_name""""))
        assertFalse(header.contains("""name="rename.new_name""""))
        // The reset action only shows once the entry is posted.
        assertFalse(header.contains("actions/reset"))
        val postedHtml = get(router, "/admin/entries/edit", mapOf("id" to posted.key()), session)
        val postedHeader = postedHtml.let(::headerOf)
        assertFalse(postedHeader.contains("actions/post"))
        assertTrue(postedHeader.contains("actions/reset"))
    }

    @Test
    fun viewHeaderTakesFluentActionsAndGetOverrides() {
        val entry = entry("Entry", "draft")
        val view = ViewPage("view").headerActions(Action.make("stamp").label("Stamp").action { _ -> })
        val router = mount(entries(view = view))
        val html = get(router, "/admin/entries/view", mapOf("id" to entry.key()), RmSession())
        val header = html.let(::headerOf)
        val edit = header.indexOf("""href="edit?id=${entry.key()}"""")
        val stamp = header.indexOf("""action="/admin/entries/actions/stamp"""")
        val back = header.indexOf("""href="list"""")
        assertTrue(edit in 0 until stamp && stamp < back, header)
        assertEquals(listOf("stamp"), view.extraHeaderActions().map { it.name })
        // The fluent call replaces earlier extras instead of replacing the defaults.
        view.headerActions(Action.make("other").action { _ -> })
        assertEquals(listOf("other"), view.extraHeaderActions().map { it.name })
        assertEquals(listOf("edit"), EditPage("edit").headerActions(Action.make("edit")).extraHeaderActions().map { it.name })
        assertTrue(get(router, "/admin/entries/view", mapOf("id" to entry.key()), RmSession()).contains("Delete"))
        // Without a session the header still renders (no CSRF field).
        assertTrue(get(router, "/admin/entries/view", mapOf("id" to entry.key()), null).contains("""action="/admin/entries/actions/other""""))
    }

    @Test
    fun extraActionsReplaceDefaultsByNameAndMustBeUnique() {
        val entry = entry("Entry", "draft")
        val replacing = object : ViewPage("view") {
            override fun getHeaderActions(): List<Action> = listOf(
                Action.make("delete").label("Discard").action { _ -> log.add("discard") },
            )
        }
        val router = mount(entries(view = replacing))
        val html = get(router, "/admin/entries/view", mapOf("id" to entry.key()), RmSession())
        val header = html.let(::headerOf)
        assertTrue(header.contains("Discard"))
        assertFalse(header.contains("Delete this record permanently?"))
        assertTrue(header.contains("""action="/admin/entries/actions/delete""""))
        // Posting the replaced name runs the custom handler.
        val response = post(router, "/admin/entries/actions/delete", "id=${entry.key()}", RmSession())
        assertEquals(302, response.status)
        assertEquals(listOf("discard"), log)

        val duplicate = object : ViewPage("view") {
            override fun getHeaderActions(): List<Action> = listOf(Action.make("same"), Action.make("same"))
        }
        val failure = assertThrows<FireflyException> {
            get(mount(entries(view = duplicate)), "/admin/entries/view", mapOf("id" to entry.key()), RmSession())
        }
        assertTrue(failure.message!!.contains("Duplicate header action [same]"))
    }

    @Test
    fun createPageAndPlainPagesKeepTheirOwnHeader() {
        val router = mount(entries())
        // An edit page opened without a record still renders its header.
        assertTrue(get(router, "/admin/entries/edit", emptyMap(), RmSession()).contains("""data-ff-path="save""""))
        assertTrue(get(router, "/admin/entries/view", emptyMap(), RmSession()).contains("""data-ff-path="edit""""))
        val html = get(router, "/admin/entries/create", emptyMap(), RmSession())
        assertFalse(html.contains("actions/post"))
        val list = get(router, "/admin/entries/list", emptyMap(), RmSession())
        assertTrue(list.contains("""href="create""""))
    }

    // ——— Posting header actions ———

    @Test
    fun postRunsTheHandlerFlashesASuccessToastAndReturns() {
        val draft = entry("Draft entry", "draft")
        val router = mount(entries())
        val session = RmSession()
        val response = post(router, "/admin/entries/actions/post", "id=${draft.key()}&_return=/admin/entries/edit%3Fid%3D${draft.key()}", session)
        assertEquals(302, response.status)
        assertEquals("/admin/entries/edit?id=${draft.key()}", response.headers["Location"])
        assertEquals(listOf("post:${draft.key()}"), log)
        assertEquals("posted", reload(draft).getAttribute("state"))
        assertToast(session, "Entry posted", "success")
        // Without a session the action still runs and the redirect still happens.
        val fresh = entry("Another", "draft")
        val noSession = post(router, "/admin/entries/actions/post", "id=${fresh.key()}", null)
        assertEquals(302, noSession.status)
        assertEquals("/admin/entries/list", noSession.headers["Location"])
        assertEquals("posted", reload(fresh).getAttribute("state"))
    }

    @Test
    fun successRedirectAndSilentSuccess() {
        val draft = entry("Draft entry", "draft")
        val silent = object : EditPage("edit") {
            override fun getHeaderActions(): List<Action> = listOf(
                Action.make("quiet").successRedirectUrl("/admin/elsewhere").action { _ -> log.add("quiet") },
            )
        }
        val router = mount(entries(edit = silent))
        val session = RmSession()
        val response = post(router, "/admin/entries/actions/quiet", "id=${draft.key()}&_return=/admin/entries/list", session)
        assertEquals("/admin/elsewhere", response.headers["Location"])
        assertNull(session.get(FireflyNotification.FLASH_KEY))
        assertEquals(listOf("quiet"), log)
    }

    @Test
    fun failureFlashesADangerToastAndHaltedStaysQuiet() {
        val draft = entry("Draft entry", "draft")
        val page = object : EditPage("edit") {
            override fun getHeaderActions(): List<Action> = listOf(
                Action.make("fail").failureNotificationTitle("Could not do it").action { _ -> throw ActionFailure("Nope") },
                Action.make("failplain").failureRedirectUrl("/admin/failed").action { _ -> throw ActionFailure("Broken") },
                Action.make("stop").successNotificationTitle("Never").action { _ -> throw ActionHalt() },
            )
        }
        val router = mount(entries(edit = page))
        val session = RmSession()
        val failed = post(router, "/admin/entries/actions/fail", "id=${draft.key()}&_return=/admin/entries/edit", session)
        assertEquals("/admin/entries/edit", failed.headers["Location"])
        assertToast(session, "Could not do it", "danger", "Nope")
        val plain = post(router, "/admin/entries/actions/failplain", "id=${draft.key()}", session)
        assertEquals("/admin/failed", plain.headers["Location"])
        assertToast(session, "Action failed", "danger", "Broken")
        val halted = post(router, "/admin/entries/actions/stop", "id=${draft.key()}&_return=/admin/entries/edit", session)
        assertEquals("/admin/entries/edit", halted.headers["Location"])
        assertNull(session.get(FireflyNotification.FLASH_KEY))
        val noSession = post(router, "/admin/entries/actions/fail", "id=${draft.key()}", null)
        assertEquals(302, noSession.status)
    }

    @Test
    fun postGuardsAreEnforced() {
        val draft = entry("Draft entry", "draft")
        val posted = entry("Posted entry", "posted")
        val router = mount(entries())
        val session = RmSession()
        assertEquals(422, post(router, "/admin/entries/actions/post", "", session).status)
        assertEquals(422, post(router, "/admin/entries/actions/post", "record=", session).status)
        assertEquals(404, post(router, "/admin/entries/actions/post", "id=9999", session).status)
        assertEquals(404, post(router, "/admin/entries/actions/missing", "id=${draft.key()}", session).status)
        // A link action (no handler) is not a record action.
        assertEquals(404, post(router, "/admin/entries/actions/go", "id=${draft.key()}", session).status)
        // Hidden for this record (visibleForRecord) and disabled actions are refused.
        assertEquals(403, post(router, "/admin/entries/actions/post", "id=${posted.key()}", session).status)
        assertEquals(403, post(router, "/admin/entries/actions/locked", "id=${draft.key()}", session).status)
        assertTrue(log.isEmpty())
        // `record` works as the id field too.
        assertEquals(302, post(router, "/admin/entries/actions/post", "record=${draft.key()}", session).status)
    }

    @Test
    fun csrfSignInAndHostChecksApply() {
        val draft = entry("Draft entry", "draft")
        val router = mount(entries())
        val session = RmSession()
        get(router, "/admin/entries/edit", mapOf("id" to draft.key()), session)
        assertEquals(419, post(router, "/admin/entries/actions/post", "id=${draft.key()}", session, token = "wrong").status)
        assertTrue(log.isEmpty())

        val guarded = Router()
        Panel().path("admin").resource(entries()).mount(guarded)
        val anonymous = post(guarded, "/admin/entries/actions/post", "id=${draft.key()}", null)
        assertEquals(302, anonymous.status)
        assertEquals("/admin/login", anonymous.headers["Location"])

        val guard = MemoryPanelGuard()
        guard.register("Ada", "ada@example.test", "secret")
        guard.attempt("ada@example.test", "secret")
        val signedIn = Router()
        Panel().path("admin").guard(guard).resource(entries()).mount(signedIn)
        assertEquals(302, post(signedIn, "/admin/entries/actions/post", "id=${draft.key()}", RmSession()).status)
        assertEquals(listOf("post:${draft.key()}"), log)
        log.clear()

        val hosted = Router()
        Panel().path("admin").domain("ops.example").authenticate(false).resource(entries()).mount(hosted)
        val wrongHost = post(hosted, "/admin/entries/actions/post", "id=${draft.key()}", null, headers = mapOf("Host" to "other.example"))
        assertEquals(404, wrongHost.status)
        assertTrue(log.isEmpty())
    }

    @Test
    fun returnLocationMustBeALocalPath() {
        val router = mount(entries())
        val unsafe = listOf("https://evil.example/x", "//evil.example/x", "/ok\\evil", "/a\nb", "no-slash")
        for (value in unsafe) {
            val draft = entry("Draft", "draft")
            val response = post(router, "/admin/entries/actions/post", "id=${draft.key()}&_return=${URLEncoder.encode(value, Charsets.UTF_8)}", RmSession())
            assertEquals("/admin/entries/list", response.headers["Location"], value)
        }
        val local = entry("Draft", "draft")
        val ok = post(router, "/admin/entries/actions/post", "id=${local.key()}&_return=%2Fadmin%2Fentries%3Fsearch%3Dx", RmSession())
        assertEquals("/admin/entries?search=x", ok.headers["Location"])
    }

    // ——— Dialog schema ———

    @Test
    fun dialogDataIsValidatedAndPassedToTheHandler() {
        val draft = entry("Draft entry", "draft")
        val router = mount(entries())
        val session = RmSession()
        val missing = post(router, "/admin/entries/actions/rename", "id=${draft.key()}&_return=/admin/entries/edit%3Fid%3D${draft.key()}", session)
        assertEquals(302, missing.status)
        assertEquals("/admin/entries/edit?id=${draft.key()}", missing.headers["Location"])
        assertNotNull(missing.sessionErrors!!["new_name"])
        assertToast(session, "Please fix the form", "danger")
        assertEquals("draft", reload(draft).getAttribute("state"))
        assertTrue(log.isEmpty())

        val ok = post(router, "/admin/entries/actions/rename", "id=${draft.key()}&new_name=Renamed&secret_note=x&skipped=y", session)
        assertEquals(302, ok.status)
        // Hidden, non-dehydrated, and layout-only components stay out of the data.
        assertEquals(listOf("rename:{new_name=Renamed}"), log)
        assertEquals("Renamed", reload(draft).getAttribute("name"))
        assertToast(session, "Renamed", "success")
        // Old input is kept for the failed submit; without a session the failure still redirects.
        assertEquals(draft.key(), missing.sessionOldInput!!["id"])
        assertEquals(302, post(router, "/admin/entries/actions/rename", "id=${draft.key()}", null).status)
    }

    // ——— Authorization ———

    @Test
    fun authorizeResolvesThroughTheGateAgainstTheRecord() {
        val draft = entry("Draft entry", "draft")
        val posted = entry("Posted entry", "posted")
        val gate = Gate().define("sign") { _, arguments -> (arguments[0] as RaEntry).getAttribute("state") == "posted" }
        Firefly.authorizeActionsWith(gate)
        val page = object : EditPage("edit") {
            override fun getHeaderActions(): List<Action> = listOf(
                Action.make("sign").label("Sign").authorize("sign").action { _ -> log.add("sign") },
            )
        }
        val router = mount(entries(edit = page))
        assertFalse(get(router, "/admin/entries/edit", mapOf("id" to draft.key()), RmSession()).contains("actions/sign"))
        assertTrue(get(router, "/admin/entries/edit", mapOf("id" to posted.key()), RmSession()).contains("actions/sign"))
        assertEquals(403, post(router, "/admin/entries/actions/sign", "id=${draft.key()}", RmSession()).status)
        assertEquals(302, post(router, "/admin/entries/actions/sign", "id=${posted.key()}", RmSession()).status)
        assertEquals(listOf("sign"), log)
        // Clearing the gate denies named abilities again.
        Firefly.authorizeActionsWith(null)
        assertEquals(403, post(router, "/admin/entries/actions/sign", "id=${posted.key()}", RmSession()).status)
    }

    // ——— Table record actions ———

    @Test
    fun tableRecordActionsPostPerRowAndRunAgainstTheRow() {
        val draft = entry("Draft entry", "draft")
        val posted = entry("Posted entry", "posted")
        val stamp = Action.make("stamp").label("Stamp")
            .visibleForRecord { (it as RaEntry).getAttribute("state") == "draft" }
            .successNotificationTitle("Stamped")
            .action { record -> log.add("stamp:${(record as RaEntry).key()}") }
        val router = mount(entries(tableActions = listOf(stamp)))
        val session = RmSession()
        val html = get(router, "/admin/entries/list", mapOf("search" to "entry"), session)
        val draftRow = html.substringAfter("""data-ff-record="${draft.key()}"""").substringBefore("</tr>")
        val postedRow = html.substringAfter("""data-ff-record="${posted.key()}"""").substringBefore("</tr>")
        assertTrue(draftRow.contains("""<form method="post" action="/admin/entries/actions/stamp""""), draftRow)
        assertTrue(draftRow.contains("""<input type="hidden" name="id" value="${draft.key()}" />"""))
        assertTrue(draftRow.contains("""<input type="hidden" name="_return" value="/admin/entries/list?search=entry" />"""))
        assertFalse(postedRow.contains("actions/stamp"))
        val response = post(router, "/admin/entries/actions/stamp", "id=${draft.key()}&_return=%2Fadmin%2Fentries%2Flist%3Fsearch%3Dentry", session)
        assertEquals("/admin/entries/list?search=entry", response.headers["Location"])
        assertEquals(listOf("stamp:${draft.key()}"), log)
        assertToast(session, "Stamped", "success")
        // Hidden for the posted row.
        assertEquals(403, post(router, "/admin/entries/actions/stamp", "id=${posted.key()}", session).status)
    }

    @Test
    fun tableRecordActionsHonorTheTableQueryAndTheTenant() {
        val draft = entry("Draft entry", "draft")
        val posted = entry("Posted entry", "posted")
        val stamp = Action.make("stamp").action { record -> log.add("stamp:${(record as RaEntry).key()}") }
        val scoped = mount(entries(tableActions = listOf(stamp), onlyDrafts = true))
        // The table only lists drafts, so a posted record is outside the action's reach.
        assertEquals(404, post(scoped, "/admin/entries/actions/stamp", "id=${posted.key()}", RmSession()).status)
        assertEquals(302, post(scoped, "/admin/entries/actions/stamp", "id=${draft.key()}", RmSession()).status)
        assertEquals(listOf("stamp:${draft.key()}"), log)

        val acme = entry("Acme", "draft", tenant = "acme")
        val beta = entry("Beta", "draft", tenant = "beta")
        val untenanted = entry("Loose", "draft")
        val tenanted = Router()
        Panel().path("admin").authenticate(false).tenant(Tenant("acme")).tenant(Tenant("beta"))
            .resource(entries(tableActions = listOf(stamp), tenant = true)).mount(tenanted)
        log.clear()
        assertEquals(404, post(tenanted, "/admin/entries/actions/stamp", "id=${beta.key()}", RmSession(), query = mapOf("tenant" to "acme")).status)
        assertEquals(302, post(tenanted, "/admin/entries/actions/stamp", "id=${acme.key()}", RmSession(), query = mapOf("tenant" to "acme")).status)
        assertEquals(404, post(tenanted, "/admin/entries/actions/stamp", "id=${untenanted.key()}", RmSession(), query = mapOf("tenant" to "acme")).status)
        // Header actions are tenant-scoped too, and an unscoped request sees every tenant.
        assertEquals(404, post(tenanted, "/admin/entries/actions/post", "id=${beta.key()}", RmSession(), query = mapOf("tenant" to "acme")).status)
        assertEquals(302, post(tenanted, "/admin/entries/actions/post", "id=${beta.key()}", RmSession()).status)
        assertEquals(listOf("stamp:${acme.key()}", "post:${beta.key()}"), log)
    }

    @Test
    fun bulkActionsReceiveRecordObjects() {
        val one = entry("One", "draft")
        val two = entry("Two", "draft")
        val resource = object : Resource("entries") {
            init {
                label("Entry")
                model(RaEntry::class.java)
            }

            override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name")).bulkActions(
                BulkAction.make("mark").successNotificationTitle("Marked").actionOnRecords { records ->
                    log.add("mark:" + records.joinToString(",") { (it as RaEntry).key() })
                },
            )
        }
        val router = mount(resource)
        val session = RmSession()
        val response = post(router, "/admin/entries/list", "_bulk_action=mark&_bulk_keys=${one.key()},${two.key()}", session)
        assertEquals(302, response.status)
        assertEquals(listOf("mark:${one.key()},${two.key()}"), log)
        assertToast(session, "Marked", "success")
    }

    // ——— Relation managers ———

    @Test
    fun relationManagerRecordActionsRunOnTheRelatedRecord() {
        val project = RmProject().fill(mapOf("name" to "Kona", "team" to "a")).also { it.save() }
        val other = RmProject().fill(mapOf("name" to "Other", "team" to "b")).also { it.save() }
        val mine = RmTask().fill(mapOf("project_id" to project.getKey(), "name" to "Mine")).also { it.save() }
        val theirs = RmTask().fill(mapOf("project_id" to other.getKey(), "name" to "Theirs")).also { it.save() }
        val touch = Action.make("touch").label("Touch")
            .successNotificationTitle("Touched")
            .action { record ->
                val task = record as RmTask
                task.fill(mapOf("name" to "Touched ${task.getAttribute("name")}"))
                task.save()
            }
        val retitle = Action.make("retitle").schema(TextInput.make("title").required())
            .actionWithData { record, data -> log.add("retitle:${(record as RmTask).getKey()}:${data["title"]}") }
        val tasks = RelationManager("tasks").recordTitleAttribute("name")
            .table { table -> table.columns(TextColumn.make("name")).recordActions(touch, retitle) }
        val resource = object : Resource("projects") {
            init {
                label("Project")
                model(RmProject::class.java)
                relation(tasks)
            }

            override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

            override fun infolist(schema: Schema): Schema = schema.components(TextEntry.make("name"))
        }
        val router = mount(resource)
        val session = RmSession()
        val html = get(router, "/admin/projects/edit", mapOf("id" to project.getKey().toString()), session)
        val row = html.substringAfter("""data-ff-record="${mine.getKey()}"""").substringBefore("</tr>")
        val url = "/admin/projects/relations/actions/touch?relation=tasks&amp;owner=${project.getKey()}&amp;page=edit"
        assertTrue(row.contains("""<form method="post" action="$url""""), row)
        assertTrue(row.contains("""<input type="hidden" name="id" value="${mine.getKey()}" />"""))
        assertTrue(row.contains("""name="_return" value="/admin/projects/edit?id=${project.getKey()}""""))
        // The view page is read-only: no record actions.
        val view = get(router, "/admin/projects/view", mapOf("id" to project.getKey().toString()), session)
        assertFalse(view.contains("relations/actions/touch"))

        val query = mapOf("relation" to "tasks", "owner" to project.getKey().toString(), "page" to "edit")
        val response = post(router, "/admin/projects/relations/actions/touch", "id=${mine.getKey()}", session, query = query)
        assertEquals(302, response.status)
        assertEquals("/admin/projects/edit?id=${project.getKey()}&relation=tasks", response.headers["Location"])
        assertToast(session, "Touched", "success")
        assertEquals("Touched Mine", RmTask().newQuery().where("id", mine.getKey()).first()!!.getAttribute("name"))
        // Dialog data comes from the form; the route's own query parameters never leak into it.
        val retitled = post(router, "/admin/projects/relations/actions/retitle", "id=${mine.getKey()}&title=Fresh", session, query = query)
        assertEquals(302, retitled.status)
        assertEquals(listOf("retitle:${mine.getKey()}:Fresh"), log)
        // Related rows of another owner are out of scope; unknown actions, relations and read-only pages are refused.
        assertEquals(404, post(router, "/admin/projects/relations/actions/touch", "id=${theirs.getKey()}", session, query = query).status)
        assertEquals(404, post(router, "/admin/projects/relations/actions/missing", "id=${mine.getKey()}", session, query = query).status)
        assertEquals(404, post(router, "/admin/projects/relations/actions/touch", "id=${mine.getKey()}", session, query = query + ("relation" to "nope")).status)
        assertEquals(403, post(router, "/admin/projects/relations/actions/touch", "id=${mine.getKey()}", session, query = query + ("page" to "view")).status)
        assertEquals("Theirs", RmTask().newQuery().where("id", theirs.getKey()).first()!!.getAttribute("name"))
    }

    // ——— Helpers ———

    private fun entry(name: String, state: String, tenant: String? = null): RaEntry =
        RaEntry().fill(mapOf("name" to name, "state" to state, "tenant_id" to tenant)).also { it.save() }

    private fun reload(entry: RaEntry): RaEntry = RaEntry().newQuery().where("id", entry.getKey()).first()!!

    private fun RaEntry.key(): String = getKey().toString()

    private fun mount(resource: Resource): Router {
        val router = Router()
        Panel().path("admin").authenticate(false).resource(resource).mount(router)
        return router
    }

    private fun entries(
        edit: EditPage? = null,
        view: ViewPage = ViewPage("view"),
        tableActions: List<Action> = emptyList(),
        tenant: Boolean = false,
        onlyDrafts: Boolean = false,
    ): Resource {
        val editPage = edit ?: EntryEditPage(log)
        return object : Resource("entries") {
            init {
                label("Entry")
                model(RaEntry::class.java)
                if (tenant) tenant("tenant_id")
            }

            override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

            override fun infolist(schema: Schema): Schema = schema.components(TextEntry.make("name"))

            override fun table(table: Table<*, *>): Table<*, *> {
                val built = table.columns(TextColumn.make("name").searchable()).recordActions(*tableActions.toTypedArray())
                if (onlyDrafts) {
                    @Suppress("UNCHECKED_CAST")
                    (built as Table<Any?, Any?>).modifyQueryUsing { query -> (query as ModelBuilder<*>).where("state", "draft") }
                }
                return built
            }

            override fun getPages(): Map<String, Page> =
                linkedMapOf("list" to ListPage("list"), "create" to CreatePage("create"), "edit" to editPage, "view" to view)
        }
    }

    private fun headerOf(html: String): String = html.substring(html.indexOf("""<div class="ff-schema"><div class="ff-component ff-action" """))

    private fun get(router: Router, path: String, query: Map<String, String>, session: RequestSession?): String {
        val match = router.find("GET", path) ?: throw AssertionError("missing $path")
        val request = Request("GET", path, emptyMap(), query, "")
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        val response = match.route.action.handle(request)
        assertEquals(200, response.status, response.body)
        return response.body
    }

    private fun post(
        router: Router,
        path: String,
        body: String,
        session: RequestSession?,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        token: String? = null,
    ): Response {
        val match = router.find("POST", path) ?: throw AssertionError("missing $path")
        val sessionToken = session?.get("_token") as? String
        val fields = body.split('&').filter { it.isNotEmpty() }.map { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            "$key=$value"
        }.toMutableList()
        val csrf = token ?: sessionToken
        if (csrf != null) fields.add("_token=$csrf")
        val request = Request(
            "POST",
            path,
            mapOf("Content-Type" to "application/x-www-form-urlencoded") + headers,
            query,
            fields.joinToString("&"),
        )
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
    }

    private fun assertToast(session: RmSession, title: String, status: String, body: String? = null) {
        val flash = session.get(FireflyNotification.FLASH_KEY) as? List<*> ?: throw AssertionError("no toast flashed")
        val last = flash.last() as Map<*, *>
        assertEquals(title, last["title"], flash.toString())
        assertEquals(status, last["status"], flash.toString())
        if (body != null) assertEquals(body, last["body"], flash.toString())
        session.put(FireflyNotification.FLASH_KEY, null)
    }
}

/** Edit page whose header carries the actions the tests post: post, reset, rename, locked, go. */
private class EntryEditPage(private val log: MutableList<String>) : EditPage("edit") {
    override fun getHeaderActions(): List<Action> = listOf(
        Action.make("post").label("Post entry")
            .visibleForRecord { (it as RaEntry).getAttribute("state") == "draft" }
            .successNotificationTitle("Entry posted")
            .action { record ->
                val entry = record as RaEntry
                entry.fill(mapOf("state" to "posted"))
                entry.save()
                log.add("post:${entry.getKey()}")
            },
        Action.make("reset").label("Reset to draft")
            .visibleForRecord { (it as RaEntry).getAttribute("state") == "posted" }
            .requiresConfirmation()
            .action { _ -> log.add("reset") },
        Action.make("rename").label("Rename")
            .schema(
                TextInput.make("new_name").required(),
                TextInput.make("secret_note").hidden(),
                TextInput.make("skipped").dehydrated(false),
                Text.make("Pick a new name"),
            )
            .successNotificationTitle("Renamed")
            .actionWithData { record, data ->
                val entry = record as RaEntry
                entry.fill(mapOf("name" to data["new_name"]))
                entry.save()
                log.add("rename:$data")
            },
        mutableLocked(),
        Action.make("go").label("Go").url("elsewhere"),
    )

    private fun mutableLocked(): Action {
        val locked = Action.make("locked").label("Locked").action { _ -> log.add("locked") }
        locked.disabled(true)
        return locked
    }
}

class RaEntry : Cadence<RaEntry>() {
    override val table = "ra_entries"
    override val timestamps = false
    override val fillable = setOf("name", "state", "tenant_id")
}
