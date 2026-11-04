package org.konaframework.firefly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.authorization.Gate
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.BulkAction
import org.konaframework.firefly.tables.BulkActionGroup
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.util.function.Supplier

class FireflyAuthorizationTest {
    private lateinit var gate: Gate
    private lateinit var draft: AzPost
    private lateinit var published: AzPost

    @BeforeEach
    fun seed() {
        azDatabase()
        Firefly.reset()
        gate = Gate().policy(AzPost::class.java, AzPostPolicy())
        Firefly.gate(gate)
        draft = AzPost().fill(mapOf("title" to "Draft", "status" to "draft")).also { it.save() }
        published = AzPost().fill(mapOf("title" to "Live", "status" to "published")).also { it.save() }
    }

    @AfterEach
    fun cleanup() {
        Firefly.reset()
        Gate.clearDefault()
    }

    // ——— Resource hooks ———

    @Test
    fun hooksAskThePolicyForTheCurrentUser() {
        val resource = posts()
        assertEquals(false, as_(null) { resource.canViewAny() })
        for (role in listOf("admin", "editor", "viewer")) assertTrue(as_(role) { resource.canViewAny() }, role)
        assertFalse(as_("blocked") { resource.canViewAny() })

        assertTrue(as_("viewer") { resource.canView(draft) })
        assertFalse(as_("viewer") { resource.canCreate() })
        assertFalse(as_("viewer") { resource.canEdit(draft) })
        assertFalse(as_("viewer") { resource.canDelete(draft) })
        assertFalse(as_("viewer") { resource.canDeleteAny() })

        assertTrue(as_("editor") { resource.canCreate() })
        assertTrue(as_("editor") { resource.canEdit(published) })
        assertTrue(as_("editor") { resource.canDelete(draft) })
        assertFalse(as_("editor") { resource.canDelete(published) })
        assertTrue(as_("editor") { resource.canDeleteAny() })
        assertTrue(as_("editor") { resource.canReplicate(draft) })
        assertFalse(as_("editor") { resource.canRestore(draft) })
        assertFalse(as_("editor") { resource.canRestoreAny() })
        assertFalse(as_("editor") { resource.canForceDelete(draft) })
        assertFalse(as_("editor") { resource.canForceDeleteAny() })
        assertFalse(as_("editor") { resource.canReorder() })

        assertTrue(as_("admin") { resource.canDelete(published) })
        assertTrue(as_("admin") { resource.canRestore(draft) })
        assertTrue(as_("admin") { resource.canRestoreAny() })
        assertTrue(as_("admin") { resource.canForceDelete(draft) })
        assertTrue(as_("admin") { resource.canForceDeleteAny() })
        assertTrue(as_("admin") { resource.canReorder() })

        // Custom abilities go through the same policy.
        assertTrue(as_("admin") { resource.can("publish", draft) })
        assertFalse(as_("editor") { resource.can("publish", draft) })
        assertFalse(as_("editor") { resource.can("publish", draft) })
    }

    @Test
    fun hooksFollowTheUserOfTheScopeAndRestoreThePreviousOne() {
        val outer = azUser("admin")
        val inner = azUser("viewer")
        FireflyAuth.actingAs(outer, Supplier {
            assertSame(outer, FireflyAuth.user())
            FireflyAuth.actingAs(inner, Supplier { assertSame(inner, FireflyAuth.user()) })
            assertSame(outer, FireflyAuth.user())
        })
        assertNull(FireflyAuth.user())
    }

    @Test
    fun requestScopeRemembersLoadedRecordsOnlyInsideAScope() {
        var loads = 0
        val load = { loads++; "record" }
        assertEquals("record", FireflyAuth.remember("k", load))
        assertEquals("record", FireflyAuth.remember("k", load))
        assertEquals(2, loads)
        FireflyAuth.actingAs(null, Supplier {
            assertEquals("record", FireflyAuth.remember("k", load))
            assertEquals("record", FireflyAuth.remember("k", load))
            assertNull(FireflyAuth.remember("none") { null })
            assertNull(FireflyAuth.remember("none") { throw AssertionError("cached null must not reload") })
        })
        assertEquals(3, loads)
    }

    @Test
    fun resourcesWithoutAPolicyAllowEverythingUnlessStrict() {
        val plain = object : Resource("plain") {
            init {
                model(AzPlain::class.java)
            }
        }
        assertTrue(as_("viewer") { plain.canViewAny() })
        assertTrue(as_("viewer") { plain.canDelete(AzPlain()) })
        assertTrue(as_(null) { plain.canCreate() })

        // Resource > panel > global.
        Firefly.authorizeWithoutPolicy(false)
        assertFalse(Firefly.authorizesWithoutPolicy())
        assertFalse(as_("admin") { plain.canViewAny() })
        plain.inheritAuthorizeWithoutPolicy(true)
        assertTrue(as_("admin") { plain.canViewAny() })
        plain.authorizeWithoutPolicy(false)
        assertFalse(as_("admin") { plain.canViewAny() })
        plain.authorizeWithoutPolicy(true)
        assertTrue(as_("admin") { plain.canViewAny() })

        // A model with a policy ignores the switch.
        val strict = posts().authorizeWithoutPolicy(false)
        assertTrue(as_("viewer") { strict.canViewAny() })
        assertFalse(as_("viewer") { strict.canCreate() })

        // No model: nothing to authorize, so only the switch decides.
        val modelless = Resource("modelless")
        assertFalse(as_("admin") { modelless.canViewAny() })
        modelless.authorizeWithoutPolicy(true)
        assertTrue(as_("admin") { modelless.canViewAny() })
    }

    @Test
    fun withoutAnyGateEverythingIsAllowed() {
        Firefly.gate(null)
        Gate.clearDefault()
        assertTrue(as_("viewer") { posts().canDelete(draft) })
        Firefly.authorizeWithoutPolicy(false)
        assertFalse(as_("viewer") { posts().canDelete(draft) })
    }

    @Test
    fun theDefaultGateIsUsedWhenNoneWasSet() {
        Firefly.gate(null)
        Gate.setDefault(Gate().policy(AzPost::class.java, AzPostPolicy()))
        assertFalse(as_("viewer") { posts().canCreate() })
        assertTrue(as_("editor") { posts().canCreate() })
    }

    @Test
    fun overriddenHooksWinOverThePolicy() {
        val resource = object : Resource("posts") {
            init {
                model(AzPost::class.java)
            }

            override fun canViewAny(): Boolean = false

            override fun canDelete(record: Any): Boolean = (record as AzPost).getAttribute("title") == "Live"
        }
        assertFalse(as_("admin") { resource.canViewAny() })
        assertTrue(as_("viewer") { resource.canDelete(published) })
        assertFalse(as_("admin") { resource.canDelete(draft) })
        assertTrue(as_("viewer") { resource.canView(draft) })
    }

    // ——— Pages ———

    @Test
    fun deniedPagesAnswer403AndKeepOutOfNavigation() {
        val guard = AzGuard()
        val router = mount(posts(), guard)
        guard.signIn("blocked")
        assertEquals(403, get(router, "/admin/posts/list").status)
        assertFalse(get(router, "/admin").body.contains("""href="/admin/posts/list""""))

        guard.signIn("viewer")
        assertEquals(200, get(router, "/admin/posts/list").status)
        assertTrue(get(router, "/admin").body.contains("""href="/admin/posts/list""""))
        assertEquals(403, get(router, "/admin/posts/create").status)
        assertEquals(403, get(router, "/admin/posts/edit", mapOf("id" to draft.getKey().toString())).status)
        assertEquals(200, get(router, "/admin/posts/view", mapOf("id" to draft.getKey().toString())).status)

        guard.signIn("editor")
        assertEquals(200, get(router, "/admin/posts/create").status)
        assertEquals(200, get(router, "/admin/posts/edit", mapOf("id" to draft.getKey().toString())).status)
        // No id or an unknown id falls back to viewAny (the form stays blank).
        assertEquals(200, get(router, "/admin/posts/edit").status)
        assertEquals(200, get(router, "/admin/posts/view", mapOf("id" to "999")).status)
        guard.signIn("blocked")
        assertEquals(403, get(router, "/admin/posts/edit", mapOf("id" to "999")).status)
    }

    @Test
    fun rowAndHeaderActionsFollowThePolicy() {
        val guard = AzGuard()
        val router = mount(posts(), guard)

        guard.signIn("viewer")
        val viewerList = get(router, "/admin/posts/list").body
        assertFalse(viewerList.contains("""data-ff-action="create""""))
        assertFalse(viewerList.contains("""data-ff-action="edit""""))
        assertFalse(viewerList.contains("""data-ff-action="delete""""))
        assertTrue(viewerList.contains("""data-ff-action="view""""))
        assertFalse(viewerList.contains("Delete selected"), "bulk delete needs deleteAny")
        assertFalse(viewerList.contains("data-ff-selectable data-ff-total"), "no bulk action, no checkboxes")

        guard.signIn("editor")
        val editorList = get(router, "/admin/posts/list").body
        assertTrue(editorList.contains("""data-ff-action="create""""))
        assertEquals(2, count(editorList, """data-ff-action="edit""""))
        assertEquals(1, count(editorList, """data-ff-action="delete""""), "only the draft row may be deleted")
        assertTrue(editorList.contains("Delete selected"))

        guard.signIn("admin")
        assertEquals(2, count(get(router, "/admin/posts/list").body, """data-ff-action="delete""""))

        // Edit page header: view / delete follow the record.
        val id = published.getKey().toString()
        guard.signIn("editor")
        val editPublished = get(router, "/admin/posts/edit", mapOf("id" to id)).body
        assertTrue(editPublished.contains("""data-ff-action="view""""))
        assertTrue(editPublished.contains("""ff-hidden" data-ff-path="delete""""))
        guard.signIn("admin")
        assertFalse(get(router, "/admin/posts/edit", mapOf("id" to id)).body.contains("""ff-hidden" data-ff-path="delete""""))

        // View page header: edit / delete.
        guard.signIn("viewer")
        val view = get(router, "/admin/posts/view", mapOf("id" to id)).body
        assertTrue(view.contains("""ff-hidden" data-ff-path="edit""""))
        assertTrue(view.contains("""ff-hidden" data-ff-path="delete""""))
        guard.signIn("admin")
        val adminView = get(router, "/admin/posts/view", mapOf("id" to id)).body
        assertFalse(adminView.contains("""ff-hidden" data-ff-path="edit""""))
        assertFalse(adminView.contains("""ff-hidden" data-ff-path="delete""""))
    }

    @Test
    fun simpleResourceDialogsAndCreateFollowThePolicy() {
        val guard = AzGuard()
        val router = mount(posts().simple(), guard)
        guard.signIn("viewer")
        val viewer = get(router, "/admin/posts/manage").body
        assertFalse(viewer.contains("""data-ff-action="create""""))
        assertFalse(viewer.contains("""data-ff-action="edit""""))
        assertFalse(viewer.contains("""data-ff-action="delete""""))
        assertTrue(viewer.contains("""data-ff-action="view""""))
        assertEquals(403, post(router, "/admin/posts/create", "title=Nope").status)
        assertEquals(403, post(router, "/admin/posts/edit", "id=${draft.getKey()}&title=Nope").status)

        guard.signIn("editor")
        val editor = get(router, "/admin/posts/manage").body
        assertTrue(editor.contains("""data-ff-action="create""""))
        assertEquals(2, count(editor, """data-ff-action="edit""""))
        assertEquals(1, count(editor, """data-ff-action="delete""""))
        assertEquals(302, post(router, "/admin/posts/edit", "id=${draft.getKey()}&title=Renamed").status)
        assertEquals("Renamed", AzPost().newQuery().where("id", draft.getKey()).first()!!.getAttribute("title"))
    }

    @Test
    fun customPagesHonourCanAccess() {
        val guard = AzGuard().signIn("viewer")
        val router = Router()
        val locked = object : CustomPage("locked") {
            override fun canAccess(): Boolean = role(FireflyAuth.user()) == "admin"
        }
        Panel().id("desk").path("admin").guard(guard).page(locked).page(CustomPage("open")).mount(router)
        assertEquals(403, get(router, "/admin/locked").status)
        assertEquals(200, get(router, "/admin/open").status)
        val dashboard = get(router, "/admin").body
        assertFalse(dashboard.contains("""href="/admin/locked""""))
        assertTrue(dashboard.contains("""href="/admin/open""""))
        guard.signIn("admin")
        assertEquals(200, get(router, "/admin/locked").status)
        assertTrue(get(router, "/admin").body.contains("""href="/admin/locked""""))
    }

    @Test
    fun aResourcePageWithCanAccessFalseIsDenied() {
        val guard = AzGuard().signIn("admin")
        val router = Router()
        val resource = object : Resource("posts") {
            init {
                model(AzPost::class.java)
            }

            override fun getPages(): Map<String, org.konaframework.firefly.widgets.Page> =
                mapOf("list" to object : ListPage("list") {
                    override fun canAccess(): Boolean = false
                })
        }
        Panel().id("desk").path("admin").guard(guard).resource(resource).mount(router)
        assertEquals(403, get(router, "/admin/posts/list").status)
    }

    @Test
    fun navigationItemsCanBeHidden() {
        val get = SchemaBridge.open(emptyMap())
        val item = NavigationItem("reports")
        assertTrue(item.isVisible(get))
        assertFalse(item.visible(false).isVisible(get))
        assertTrue(item.visible { true }.isVisible(get))
        assertFalse(item.visible { false }.isVisible(get))
        assertTrue(item.visible(true).isVisible(get))

        val router = Router()
        val panel = Panel().id("desk").path("admin").authenticate(false)
            .item(NavigationItem("shown").label("Shown").url("/admin/shown"))
            .item(NavigationItem("secret").label("Secret").url("/admin/secret").visible(false))
        panel.mount(router)
        val html = get(router, "/admin").body
        assertTrue(html.contains("Shown"))
        assertFalse(html.contains("Secret"))
    }

    @Test
    fun appNavigationSkipsHiddenRootsAndDashboardCards() {
        val router = Router()
        val panel = Panel().id("desk").path("admin").authenticate(false)
            .item(NavigationItem("alpha").label("Alpha").url("/admin/alpha").visible(false).child(NavigationItem("hiddenchild").label("HiddenChild").url("/admin/hc")))
            .item(NavigationItem("beta").label("Beta").url("/admin/beta").child(NavigationItem("kid").label("Kid").url("/admin/kid")))
        panel.mount(router)
        val html = get(router, "/admin").body
        assertTrue(html.contains("Kid"), "children of the first visible root")
        assertFalse(html.contains("HiddenChild"))
        assertFalse(html.contains("Alpha"))
        val hiddenOnly = Router()
        Panel().id("quiet").path("quiet").authenticate(false)
            .item(NavigationItem("gone").label("Gone").url("/quiet/gone").visible(false)).mount(hiddenOnly)
        assertFalse(get(hiddenOnly, "/quiet").body.contains("Gone"))
    }

    // ——— POST ———

    @Test
    fun postsAre403WhenTheAbilityIsDenied() {
        val guard = AzGuard()
        val router = mount(posts(), guard)
        val id = published.getKey().toString()

        guard.signIn("viewer")
        assertEquals(403, post(router, "/admin/posts/create", "title=Nope&status=draft").status)
        assertEquals(403, post(router, "/admin/posts/edit", "id=$id&title=Nope").status)
        assertEquals(403, post(router, "/admin/posts/delete", "id=$id").status)
        assertEquals(403, post(router, "/admin/posts/list", "_bulk_action=delete&_bulk_keys=$id").status)
        assertEquals("Live", AzPost().newQuery().where("id", published.getKey()).first()!!.getAttribute("title"))
        assertEquals(2, AzPost().newQuery().count())

        guard.signIn("editor")
        assertEquals(302, post(router, "/admin/posts/create", "title=Fresh&status=draft").status)
        assertEquals(3, AzPost().newQuery().count())
        assertEquals(302, post(router, "/admin/posts/edit", "id=$id&title=Edited").status)
        assertEquals("Edited", AzPost().newQuery().where("id", published.getKey()).first()!!.getAttribute("title"))
        assertEquals(403, post(router, "/admin/posts/delete", "id=$id").status)
        assertEquals(302, post(router, "/admin/posts/delete", "id=${draft.getKey()}").status)
        assertEquals(2, AzPost().newQuery().count())

        guard.signIn("blocked")
        assertEquals(403, post(router, "/admin/posts/list", "_bulk_action=other&_bulk_keys=$id").status)
    }

    @Test
    fun bulkDeleteChecksEveryRecord() {
        val guard = AzGuard().signIn("editor")
        val router = mount(posts(), guard)
        val keys = "${draft.getKey()},${published.getKey()}"
        assertEquals(302, post(router, "/admin/posts/list", "_bulk_action=delete&_bulk_keys=$keys", RmSession()).status)
        // The editor may delete the draft only; the published post counts as not authorized.
        assertEquals(listOf("Live"), AzPost().newQuery().get().map { it.getAttribute("title") })
        guard.signIn("admin")
        assertEquals(302, post(router, "/admin/posts/list", "_bulk_action=delete&_bulk_keys=${published.getKey()}", RmSession()).status)
        assertEquals(0, AzPost().newQuery().count())
    }

    @Test
    fun customBulkActionsOnAConfiguredTableAreLeftAlone() {
        val guard = AzGuard().signIn("viewer")
        val resource = object : Resource("posts") {
            init {
                model(AzPost::class.java)
            }

            override fun table(table: Table<*, *>): Table<*, *> =
                table.columns(TextColumn.make("title")).bulkActions(BulkActionGroup.make(BulkAction.make("touch")))
        }
        val router = mount(resource, guard)
        val html = get(router, "/admin/posts/list").body
        assertTrue(html.contains("""data-ff-bulk-run="touch""""))
        assertEquals(302, post(router, "/admin/posts/list", "_bulk_action=touch&_bulk_keys=${draft.getKey()}").status)
    }

    // ——— Panel access ———

    @Test
    fun canAccessGuardsEveryPanelRoute() {
        val guard = AzGuard().signIn("viewer")
        val router = Router()
        val panel = Panel().id("desk").path("admin").guard(guard)
            .canAccess { user -> role(user) == "admin" }
            .resource(posts())
        panel.mount(router)
        for (path in listOf("/admin", "/admin/posts/list", "/admin/posts/create", "/admin/profile", "/admin/widgets/x", "/admin/posts/form-options")) {
            assertEquals(403, get(router, path).status, path)
        }
        assertEquals(403, post(router, "/admin/posts/list", "_bulk_action=delete&_bulk_keys=1").status)
        assertEquals(403, post(router, "/admin/posts/delete", "id=1").status)
        assertEquals(403, post(router, "/admin/profile", "name=x").status)
        assertFalse(panel.allowsUpload(Request("POST", "/_firefly/upload")))
        assertEquals(2, AzPost().newQuery().count())

        // Sign-in, registration, reset, verify and sign-out stay reachable.
        assertEquals(200, get(router, "/admin/login").body.let { 200 }, "login form for a denied user")
        assertTrue(get(router, "/admin/login").body.contains("ff-login"))
        assertEquals(200, get(router, "/admin/register").status)
        assertEquals(200, get(router, "/admin/password-reset").status)
        assertEquals(200, get(router, "/admin/email-verify").status)
        assertEquals(302, post(router, "/admin/logout", "").status)
        assertFalse(guard.check())

        guard.signIn("admin")
        assertEquals(200, get(router, "/admin").status)
        assertEquals(200, get(router, "/admin/posts/list").status)
        assertEquals(302, get(router, "/admin/login").status, "an allowed user is sent home")
        assertTrue(panel.allowsUpload(Request("POST", "/_firefly/upload")))
        assertTrue(panel.permits(azUser("admin")))
        assertFalse(panel.permits(azUser("viewer")))
        assertFalse(panel.permits(null))
    }

    @Test
    fun theUserModelCanDecidePanelAccess() {
        val guard = AzGuard()
        val router = Router()
        val panel = Panel().id("desk").path("admin").guard(guard)
        panel.mount(router)
        guard.current = AzStaff(true)
        assertEquals(200, get(router, "/admin").status)
        guard.current = AzStaff(false)
        assertEquals(403, get(router, "/admin").status)
        assertTrue(panel.permits(AzStaff(true)))
        assertFalse(panel.permits(AzStaff(false)))
        assertTrue(Panel().id("other").permits(azUser("viewer")))
        assertFalse(Panel().id("other").permits(AzStaff(true)), "AzStaff only enters the desk panel")

        // The predicate and the user model must both allow.
        val both = Panel().canAccess { true }
        assertFalse(both.permits(AzStaff(false)))
        val none = Panel().canAccess { false }
        assertFalse(none.permits(AzStaff(true)))
    }

    @Test
    fun aDeniedAccountCannotSignIn() {
        val guard = AzGuard()
        guard.accounts["viewer@example.test"] = azUser("viewer")
        guard.accounts["admin@example.test"] = azUser("admin")
        val router = Router()
        Panel().id("desk").path("admin").guard(guard).canAccess { role(it) == "admin" }.mount(router)
        val denied = post(router, "/admin/login", "email=viewer@example.test&password=x")
        assertEquals(403, denied.status)
        assertTrue(denied.body.contains("You do not have access to this panel"))
        assertFalse(guard.check(), "the denied user is signed out again")
        assertEquals(302, post(router, "/admin/login", "email=admin@example.test&password=x").status)
        assertTrue(guard.check())
    }

    @Test
    fun guestsAreRedirectedBeforeTheAccessCheck() {
        val guard = AzGuard()
        val router = Router()
        Panel().id("desk").path("admin").guard(guard).canAccess { false }.mount(router)
        assertEquals(302, get(router, "/admin").status)
        // Without authentication the access rule only judges signed-in users.
        val open = Router()
        Panel().id("open").path("open").authenticate(false).canAccess { false }.mount(open)
        assertEquals(200, get(open, "/open").status)
    }

    @Test
    fun theMemoryGuardExposesItsAccountAsAUser() {
        val memory = MemoryPanelGuard()
        assertNull(memory.user())
        memory.register("Ada", "ada@example.com", "secret")
        assertTrue(memory.attempt("ada@example.com", "secret"))
        val user = memory.user()!!
        assertEquals("ada@example.com", user.getAuthIdentifier())
        assertEquals("Ada", (user as org.konaframework.auth.HasName).getName())
        // A guard that does not override user() has none.
        val bare = object : PanelGuard by memory {}
        assertNull(object : PanelGuard {
            override fun attempt(email: String, password: String) = false
            override fun check() = false
            override fun id(): String? = null
            override fun name(): String? = null
            override fun email(): String? = null
            override fun logout() {}
            override fun register(name: String, email: String, password: String): String? = null
            override fun requestReset(email: String): String? = null
            override fun verify(email: String): String? = null
        }.user())
        assertEquals("ada@example.com", bare.user()!!.getAuthIdentifier())
    }

    @Test
    fun theAppGuardHandsItsUserToTheGate() {
        val app = StubGuard(azUser("viewer"))
        val router = Router()
        Panel().id("desk").path("admin").useAuthGuard(app).resource(posts()).mount(router)
        assertEquals(200, get(router, "/admin/posts/list").status)
        assertEquals(403, get(router, "/admin/posts/create").status)
        app.user = azUser("editor")
        assertEquals(200, get(router, "/admin/posts/create").status)
        assertEquals("editor", AuthPanelGuard(app).user()!!.getAuthIdentifier())
    }

    // ——— Helpers ———

    private fun posts(): Resource = object : Resource("posts") {
        init {
            label("Post")
            model(AzPost::class.java)
        }

        override fun form(schema: Schema): Schema = schema.components(TextInput.make("title"), TextInput.make("status"))

        override fun table(table: Table<*, *>): Table<*, *> =
            table.columns(TextColumn.make("title"), TextColumn.make("status"))
    }

    private fun mount(resource: Resource, guard: AzGuard): Router {
        val router = Router()
        Panel().id("desk").path("admin").guard(guard).resource(resource).mount(router)
        return router
    }

    private fun <R> as_(role: String?, block: () -> R): R =
        FireflyAuth.actingAs(if (role == null) null else azUser(role), Supplier { block() })

    private fun count(haystack: String, needle: String): Int = haystack.split(needle).size - 1

    private fun get(router: Router, path: String, query: Map<String, String> = emptyMap()): Response {
        val match = router.find("GET", path) ?: throw AssertionError("missing GET $path")
        return match.route.action.handle(Request("GET", path, emptyMap(), query, ""))
    }

    private fun post(router: Router, path: String, body: String, session: RequestSession? = null): Response {
        val match = router.find("POST", path) ?: throw AssertionError("missing POST $path")
        val request = Request("POST", path, mapOf("Content-Type" to "application/x-www-form-urlencoded"), emptyMap(), body)
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
    }
}
