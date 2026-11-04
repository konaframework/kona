package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.forms.Toggle
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.notifications.FireflyNotification
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Section
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.widgets.Page
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.util.function.Function

/**
 * Resource pages set `Schema.operation` (create / edit / view), closures read it, `dehydrated`
 * predicates and `default()` apply, and the canonical password field works end to end (#463).
 */
class ResourceOperationTest {
    private val renames = ArrayList<Map<String, Any?>>()

    @BeforeEach
    fun seed() {
        val url = "jdbc:h2:mem:ffop${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
        database.connection().statement(
            "create table op_users (id bigint auto_increment primary key, name varchar(100), password varchar(200), nick varchar(100), active boolean)",
        )
        database.setAsGlobal()
        renames.clear()
    }

    private fun user(name: String, password: String = "old-hash", nick: String? = null): OpUser =
        OpUser().fill(mapOf("name" to name, "password" to password, "nick" to nick, "active" to false)).also { it.save() }

    // ——— Pages set the operation ———

    @Test
    fun createEditAndViewPagesSetTheirOperation() {
        val ada = user("Ada")
        val router = mount(users())

        val create = get(router, "/admin/users/create")
        assertFalse(wrapper(create, "createOnly").contains("ff-hidden"), create)
        assertTrue(wrapper(create, "editOnly").contains("ff-hidden"), create)
        assertTrue(create.contains("""name="password""""))

        val edit = get(router, "/admin/users/edit", mapOf("id" to ada.id()))
        assertTrue(wrapper(edit, "createOnly").contains("ff-hidden"), edit)
        assertFalse(wrapper(edit, "editOnly").contains("ff-hidden"), edit)

        val view = get(router, "/admin/users/view", mapOf("id" to ada.id()))
        assertFalse(wrapper(view, "name").contains("ff-hidden"), view)
        assertTrue(view.contains("View only: Ada"), view)
        assertTrue(wrapper(view, "other").contains("ff-hidden"), view)
    }

    @Test
    fun closuresSeeTheRecordOnEditAndViewPages() {
        val ada = user("Ada")
        val router = mount(users())
        val edit = get(router, "/admin/users/edit", mapOf("id" to ada.id()))
        assertTrue(edit.contains("Editing ${ada.id()}"), edit)
        val create = get(router, "/admin/users/create")
        assertTrue(create.contains("Creating a new record"), create)
    }

    @Test
    fun simpleResourceDialogsSetTheirOperation() {
        val ada = user("Ada")
        val router = mount(manageUsers())
        val manage = get(router, "/admin/members/manage")
        // The Create dialog is operation create; the row Edit dialog is operation edit; View is view.
        val create = manage.substringAfter("""data-ff-action="create"""")
        assertFalse(wrapper(create, "createOnly").contains("ff-hidden"), manage)
        assertTrue(wrapper(create, "editOnly").contains("ff-hidden"), manage)
        assertTrue(manage.contains("""name="id" value="${ada.id()}""""), manage)
        val rowEdit = manage.substringAfter("""name="id" value="${ada.id()}"""")
        assertFalse(wrapper(rowEdit, "editOnly").contains("ff-hidden"), rowEdit)
        assertTrue(wrapper(rowEdit, "createOnly").contains("ff-hidden"), rowEdit)
        assertTrue(manage.contains("View only: Ada"), manage)
    }

    @Test
    fun relationshipSearchRunsTheCreateForm() {
        user("Ada")
        val resource = users()
        assertTrue(resource.searchFormOptions("missing", "").isEmpty())
    }

    // ——— Defaults ———

    @Test
    fun defaultsApplyOnCreateOnly() {
        val ada = user("Ada")
        val router = mount(users())
        val create = get(router, "/admin/users/create")
        assertTrue(wrapper(create, "active").contains("ff-toggle"), create)
        assertTrue(create.contains("""value="Anonymous""""), create)
        val edit = get(router, "/admin/users/edit", mapOf("id" to ada.id()))
        assertFalse(edit.contains("Anonymous"), edit)
        // The stored false stays false on edit.
        assertTrue(wrapper(edit, "active").contains("ff-toggle"), edit)
        assertTrue(edit.substringAfter("""data-ff-path="active"""").contains("""aria-pressed="false""""), edit)
    }

    // ——— Canonical password field ———

    @Test
    fun createRequiresAPasswordAndHashesIt() {
        val router = mount(users())
        val session = RmSession()

        val missing = post(router, "/admin/users/create", "name=Bea&password=", session)
        assertEquals(302, missing.status)
        assertEquals("/admin/users/create", missing.headers["Location"])
        assertEquals(0L, OpUser().newQuery().count())
        assertToast(session, "Please fix the form")
        // The next page load shows the error and the old input, never the password.
        val again = get(router, "/admin/users/create", emptyMap(), session)
        assertTrue(again.contains("The Password field is required."), again.substringAfter("<form"))
        assertTrue(again.contains("""value="Bea""""), again)
        assertTrue(again.contains("""data-ff-invalid="true""""), again)

        val ok = post(router, "/admin/users/create", "name=Bea&password=s3cret", RmSession())
        assertEquals(302, ok.status)
        assertEquals("/admin/users/list", ok.headers["Location"])
        val saved = OpUser().newQuery().where("name", "Bea").first()!!
        assertEquals("hashed:s3cret", saved.getAttribute("password"))
    }

    @Test
    fun editLeavesABlankPasswordAloneAndHashesAFilledOne() {
        val ada = user("Ada", "old-hash")
        val router = mount(users())

        val blank = post(router, "/admin/users/edit", "id=${ada.id()}&name=Ada+Lovelace&password=", RmSession())
        assertEquals("/admin/users/list", blank.headers["Location"])
        var stored = OpUser().newQuery().where("id", ada.getKey()).first()!!
        assertEquals("Ada Lovelace", stored.getAttribute("name"))
        assertEquals("old-hash", stored.getAttribute("password"))

        post(router, "/admin/users/edit", "id=${ada.id()}&name=Ada&password=new-secret", RmSession())
        stored = OpUser().newQuery().where("id", ada.getKey()).first()!!
        assertEquals("hashed:new-secret", stored.getAttribute("password"))

        // A page never echoes the stored hash.
        val edit = get(router, "/admin/users/edit", mapOf("id" to ada.id()))
        assertFalse(edit.contains("hashed:new-secret"), edit)
    }

    @Test
    fun hiddenFieldsAreNotSavedAndPostedJunkOutsideTheFormStaysAsBefore() {
        val ada = user("Ada", nick = "ada")
        val router = mount(users())
        // createOnly is hidden on edit, so its value is dropped; "nick" is not a form field and is saved as posted.
        post(router, "/admin/users/edit", "id=${ada.id()}&name=Ada&password=&createOnly=x&nick=lovelace", RmSession())
        val stored = OpUser().newQuery().where("id", ada.getKey()).first()!!
        assertEquals("lovelace", stored.getAttribute("nick"))
    }

    @Test
    fun anUnpostedRequiredFieldStillFailsAndUntouchedFieldsAreNotRevalidated() {
        val router = mount(users())
        // No password key at all on create: required, still empty.
        val absent = post(router, "/admin/users/create", "name=Cy", RmSession())
        assertEquals("/admin/users/create", absent.headers["Location"])
        assertEquals(0L, OpUser().newQuery().count())
        // Edit without a password key: the stored value satisfies it (and edit does not require it anyway).
        val ada = user("Ada")
        val ok = post(router, "/admin/users/edit", "id=${ada.id()}&name=Ada2", RmSession())
        assertEquals("/admin/users/list", ok.headers["Location"])
        assertEquals("Ada2", OpUser().newQuery().where("id", ada.getKey()).first()!!.getAttribute("name"))
    }

    @Test
    fun anUnpostedRequiredFieldWithAStoredValuePassesOnEdit() {
        val ada = user("Ada")
        val router = mount(users(nameRequired = true))
        val ok = post(router, "/admin/users/edit", "id=${ada.id()}&nick=ada", RmSession())
        assertEquals("/admin/users/list", ok.headers["Location"])
    }

    @Test
    fun invalidSimpleEditsReturnToTheListAndTheRecordParamWorksToo() {
        val ada = user("Ada")
        val router = mount(manageUsers())
        val session = RmSession()
        val response = post(router, "/admin/members/edit", "id=${ada.id()}&name=&password=", session)
        assertEquals("/admin/members/manage", response.headers["Location"])
        assertToast(session, "Please fix the form")
        val strict = mount(users(nameRequired = true))
        val viaRecord = post(strict, "/admin/users/edit", "record=${ada.id()}&name=&password=", RmSession())
        assertEquals("/admin/users/edit?id=${ada.id()}", viaRecord.headers["Location"])
    }

    @Test
    fun invalidEditReturnsToTheEditPageWithTheId() {
        val ada = user("Ada")
        val router = mount(users(nameRequired = true))
        val session = RmSession()
        val response = post(router, "/admin/users/edit", "id=${ada.id()}&name=&password=", session)
        assertEquals("/admin/users/edit?id=${ada.id()}", response.headers["Location"])
        val page = get(router, "/admin/users/edit", mapOf("id" to ada.id()), session)
        assertTrue(page.contains("The Name field is required."), page.substringAfter("<form"))
        assertEquals("Ada", OpUser().newQuery().where("id", ada.getKey()).first()!!.getAttribute("name"))
    }

    @Test
    fun simpleResourcesReturnToTheListWithAToastOnInvalidPosts() {
        val router = mount(manageUsers())
        val session = RmSession()
        val response = post(router, "/admin/members/create", "name=Dee&password=", session)
        assertEquals("/admin/members/manage", response.headers["Location"])
        assertToast(session, "Please fix the form")
        assertNull(session.get(FormSubmission.ERRORS_FLASH))
        assertEquals(0L, OpUser().newQuery().count())
        // Without a session the redirect still happens.
        assertEquals(302, post(router, "/admin/members/create", "name=Dee&password=", null).status)
    }

    @Test
    fun resourcesWithoutAFormSaveWhatIsPosted() {
        val router = Router()
        Panel().path("admin").authenticate(false).resource(bare()).mount(router)
        val response = post(router, "/admin/bare/create", "name=Raw", RmSession())
        assertEquals("/admin/bare/list", response.headers["Location"])
        assertEquals(1L, OpUser().newQuery().where("name", "Raw").count())
        // Edit works without a model store lookup failure too.
        val raw = OpUser().newQuery().where("name", "Raw").first()!!
        post(router, "/admin/bare/edit", "id=${raw.getKey()}&name=Raw2", RmSession())
        assertEquals(1L, OpUser().newQuery().where("name", "Raw2").count())
    }

    @Test
    fun pageWithoutFlashedErrorsRendersNormally() {
        val router = mount(users())
        val session = RmSession()
        val html = get(router, "/admin/users/create", emptyMap(), session)
        assertFalse(html.contains("data-ff-invalid"), html)
        // Flashed errors for fields the form does not have are ignored.
        session.flash(FormSubmission.ERRORS_FLASH, mapOf("ghost" to listOf("Nope")))
        val again = get(router, "/admin/users/create", emptyMap(), session)
        assertFalse(again.contains("data-ff-invalid"), again)
        // Unnamed form components are skipped when errors are applied.
        session.flash(FormSubmission.ERRORS_FLASH, mapOf("name" to listOf("Bad")))
        val named = get(router, "/admin/users/create", emptyMap(), session)
        assertTrue(named.contains("Bad"), named)
    }

    // ——— Record action dialogs ———

    @Test
    fun recordActionDialogsRunWithTheActionNameAsTheirOperation() {
        val ada = user("Ada")
        val router = mount(users())
        val session = RmSession()
        val missing = post(router, "/admin/users/actions/rename", "id=${ada.id()}&nick=", session)
        assertEquals(302, missing.status)
        assertToast(session, "Please fix the form")
        assertTrue(renames.isEmpty())
        val ok = post(router, "/admin/users/actions/rename", "id=${ada.id()}&nick=countess", session)
        assertEquals(302, ok.status)
        assertEquals(listOf(mapOf<String, Any?>("nick" to "countess")), renames)
    }

    // ——— Fixtures ———

    private fun users(nameRequired: Boolean = false): Resource = object : Resource("users") {
        init {
            label("User")
            model(OpUser::class.java)
        }

        override fun form(schema: Schema): Schema = schema.components(
            TextInput.make("name").also { if (nameRequired) it.required() },
            TextInput.make("password").password()
                .dehydrateStateUsing { "hashed:" + it }
                .dehydrated { !it?.toString().isNullOrBlank() }
                .required { it.operation == "create" },
            Toggle.make("active").default(true),
            Section.make("Profile").schema(
                TextInput.make("greeting").default { get -> if (get.operation == "create") "Anonymous" else "Never" },
            ),
            TextInput.make(""),
            TextInput.make("createOnly").visibleOn("create"),
            TextInput.make("editOnly").visibleOn("edit"),
            TextInput.make("recordNote")
                .placeholder { get ->
                    val key = (get.record as OpUser).getKey()
                    if (key == null) "Creating a new record" else "Editing $key"
                },
        )

        override fun infolist(schema: Schema): Schema = schema.components(
            TextEntry.make("name").label("Name").state(Function<Get, Any?> { get -> "View only: " + get.get("name") }).visibleOn("view"),
            TextEntry.make("other").label("Elsewhere").state("Not on view").hiddenOn("view"),
        )

        override fun table(table: Table<*, *>): Table<*, *> {
            val rename = Action.make("rename").label("Rename")
                .schema(TextInput.make("nick").required { it.operation == "rename" })
                .actionWithData { _, data -> renames.add(LinkedHashMap(data)) }
            return table.columns(TextColumn.make("name")).recordActions(rename)
        }

        override fun getPages(): Map<String, Page> =
            linkedMapOf("list" to ListPage("list"), "create" to CreatePage("create"), "edit" to EditPage("edit"), "view" to ViewPage("view"))
    }

    private fun manageUsers(): Resource = object : Resource("members") {
        init {
            label("Member")
            simple()
            model(OpUser::class.java)
        }

        override fun form(schema: Schema): Schema = schema.components(
            TextInput.make("name").required(),
            TextInput.make("password").password().dehydrated { !it?.toString().isNullOrBlank() }.required { it.operation == "create" },
            TextInput.make("createOnly").visibleOn("create"),
            TextInput.make("editOnly").visibleOn("edit"),
            TextInput.make(""),
        )

        override fun infolist(schema: Schema): Schema = schema.components(
            TextEntry.make("name").state(Function<Get, Any?> { get -> "View only: " + get.get("name") }).visibleOn("view"),
        )
    }

    private fun bare(): Resource = object : Resource("bare") {
        init {
            label("Bare")
            model(OpUser::class.java)
        }

        override fun getPages(): Map<String, Page> =
            linkedMapOf("list" to ListPage("list"), "create" to CreatePage("create"), "edit" to EditPage("edit"))
    }

    private fun mount(resource: Resource): Router {
        val router = Router()
        Panel().path("admin").authenticate(false).resource(resource).mount(router)
        return router
    }

    /** The component wrapper that precedes the control named [field]. */
    private fun wrapper(html: String, field: String): String {
        val at = html.indexOf("""data-ff-path="$field"""")
        assertTrue(at >= 0, "missing $field in $html")
        return html.substring(html.lastIndexOf("<div class=\"ff-component", at), at + field.length + 16)
    }

    private fun get(router: Router, path: String, query: Map<String, String> = emptyMap(), session: RequestSession? = null): String {
        val match = router.find("GET", path) ?: throw AssertionError("missing $path")
        val request = Request("GET", path, emptyMap(), query, "")
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        val response = match.route.action.handle(request)
        assertEquals(200, response.status, response.body)
        return schemaOf(response.body)
    }

    /** Only the page body: the layout carries its own CSS and scripts. */
    private fun schemaOf(html: String): String {
        val start = html.indexOf("<main")
        return if (start < 0) html else html.substring(start, html.indexOf("</main>", start))
    }

    private fun post(router: Router, path: String, body: String, session: RequestSession?): Response {
        val match = router.find("POST", path) ?: throw AssertionError("missing POST $path")
        val request = Request("POST", path, mapOf("Content-Type" to "application/x-www-form-urlencoded"), emptyMap(), body)
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
    }

    private fun assertToast(session: RmSession, title: String) {
        val flash = session.get(FireflyNotification.FLASH_KEY) as? List<*> ?: throw AssertionError("no toast flashed")
        assertEquals(title, (flash.last() as Map<*, *>)["title"], flash.toString())
        session.put(FireflyNotification.FLASH_KEY, null)
    }
}

class OpUser : Cadence<OpUser>() {
    override val table = "op_users"
    override val timestamps = false
    override val fillable = setOf("name", "password", "nick", "active")

    fun id(): String = getKey().toString()
}
