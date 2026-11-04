package org.konaframework.firefly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.auth.authorization.Gate
import org.konaframework.auth.authorization.Policy
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Cell
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.net.URLEncoder
import java.util.function.Supplier

class RelationAuthorizationTest {
    private lateinit var post: AzPost
    private lateinit var keep: AzNote
    private lateinit var spare: AzNote

    @BeforeEach
    fun seed() {
        azDatabase()
        Firefly.reset()
        Firefly.gate(Gate().policy(AzPost::class.java, AzPostPolicy()).policy(AzNote::class.java, AzNotePolicy()))
        post = AzPost().fill(mapOf("title" to "Post", "status" to "draft")).also { it.save() }
        keep = AzNote().fill(mapOf("post_id" to post.getKey(), "body" to "keep")).also { it.save() }
        spare = AzNote().fill(mapOf("post_id" to post.getKey(), "body" to "spare")).also { it.save() }
    }

    @AfterEach
    fun cleanup() {
        Firefly.reset()
    }

    @Test
    fun theRelatedPolicyDecidesWhatTheManagerShows() {
        val guard = AzGuard()
        val router = mount(posts(), guard)

        guard.signIn("admin")
        val admin = section(edit(router).body)
        assertTrue(admin.contains("""data-ff-relation="notes""""))
        assertTrue(admin.contains(">New note<"), "create")
        assertEquals(2, count(admin, """data-ff-action="edit""""), "update on both rows")
        assertEquals(1, count(admin, """data-ff-action="delete""""), "the keep note is protected by the policy")

        guard.signIn("editor")
        val editor = section(edit(router).body)
        assertTrue(editor.contains("""data-ff-relation="notes""""), "editors may view notes")
        assertFalse(editor.contains(">New note<"))
        assertEquals(0, count(editor, """data-ff-action="edit""""))
        assertEquals(0, count(editor, """data-ff-action="delete""""))
        assertEquals(2, count(editor, """data-ff-action="view""""))

        guard.signIn("blocked")
        assertEquals(403, edit(router).status, "blocked users cannot open the post")
    }

    @Test
    fun aManagerWithoutViewAnyOnTheRelatedModelIsHidden() {
        Firefly.gate(Gate().policy(AzPost::class.java, AzPostPolicy()).policy(AzNote::class.java, object : Policy {
            fun viewAny(user: org.konaframework.auth.Authenticatable?): Boolean = false

            override fun authorize(user: org.konaframework.auth.Authenticatable?, ability: String, model: Any): Boolean = false
        }))
        val guard = AzGuard().signIn("admin")
        val router = mount(posts(), guard)
        assertFalse(edit(router).body.contains("""data-ff-relation="notes""""))
        // The predicate hides or shows it regardless of the policy.
        val shown = mount(posts(posts = RelationManager("notes").related(AzNote::class.java).canViewForRecord { true }), guard)
        assertTrue(edit(shown).body.contains("""data-ff-relation="notes""""))
    }

    @Test
    fun relationPostsAreCheckedAgainstThePolicy() {
        val guard = AzGuard()
        val router = mount(posts(), guard)

        guard.signIn("editor")
        assertEquals(403, relationPost(router, "create", "body=Nope").status)
        assertEquals(403, relationPost(router, "edit", "id=${spare.getKey()}&body=Nope").status)
        assertEquals(403, relationPost(router, "delete", "id=${spare.getKey()}").status)
        assertEquals(2, AzNote().newQuery().count())

        guard.signIn("admin")
        assertEquals(302, relationPost(router, "create", "body=Fresh").status)
        assertEquals(3, AzNote().newQuery().count())
        assertEquals(403, relationPost(router, "delete", "id=${keep.getKey()}").status, "record-level policy")
        assertEquals(302, relationPost(router, "delete", "id=${spare.getKey()}").status)
        assertEquals(2, AzNote().newQuery().count())

        // Writing to the owner's relation needs update on the owner (edit page) or view (view page).
        guard.signIn("viewer")
        assertEquals(403, relationPost(router, "create", "body=Nope").status)
        assertEquals(403, relationPost(router, "create", "body=Nope", page = "view").status)
        guard.signIn("blocked")
        assertEquals(403, relationPost(router, "create", "body=Nope", page = "view").status)
    }

    @Test
    fun explicitPredicatesStillOverrideThePolicy() {
        val guard = AzGuard().signIn("editor")
        val manager = notes().canCreate { true }.canEdit { false }
        val router = mount(posts(manager), guard)
        assertEquals(302, relationPost(router, "create", "body=Allowed").status)
        assertEquals(403, relationPost(router, "edit", "id=${spare.getKey()}&body=Nope").status)
    }

    @Test
    fun relationPoliciesAskTheRightAbilities() {
        val manager = RelationManager("notes")
        fun context(operation: RelationOperation, record: Any?, related: Class<*>? = AzNote::class.java) =
            RelationContext(manager, post, record, operation, "edit", false, related)
        fun allows(role: String, operation: RelationOperation, record: Any?, strict: Boolean? = null, related: Class<*>? = AzNote::class.java) =
            FireflyAuth.actingAs(azUser(role), Supplier { RelationPolicies.allows(operation, context(operation, record, related), strict) })
        val row = RowRecord(Cell { path -> if (path == "key") spare.getKey().toString() else null })

        // Row level: a loaded model, a table row, or no record (viewAny).
        assertTrue(allows("admin", RelationOperation.EDIT, spare))
        assertTrue(allows("admin", RelationOperation.EDIT, row))
        assertFalse(allows("editor", RelationOperation.EDIT, row))
        assertFalse(allows("admin", RelationOperation.DELETE, keep))
        assertTrue(allows("admin", RelationOperation.DELETE, row))
        assertTrue(allows("editor", RelationOperation.VIEW, row))
        assertTrue(allows("editor", RelationOperation.VIEW, null))
        assertFalse(allows("blocked", RelationOperation.VIEW, null))
        assertFalse(allows("blocked", RelationOperation.EDIT, mapOf("x" to 1)), "no model to ask about falls back to viewAny")
        val missing = RowRecord(Cell { path -> if (path == "key") "999" else null })
        assertFalse(allows("blocked", RelationOperation.VIEW, missing), "a vanished row falls back to viewAny")
        assertTrue(allows("editor", RelationOperation.VIEW, missing))

        // Class level.
        assertTrue(allows("admin", RelationOperation.CREATE, null))
        assertFalse(allows("editor", RelationOperation.CREATE, spare), "create ignores the created record")
        assertTrue(allows("admin", RelationOperation.REORDER, null))
        assertFalse(allows("editor", RelationOperation.REORDER, null))
        assertTrue(allows("admin", RelationOperation.BULK, null))
        assertFalse(allows("editor", RelationOperation.BULK, null))

        // Linking abilities are asked only when the policy declares them.
        assertTrue(allows("admin", RelationOperation.ATTACH, null))
        assertFalse(allows("editor", RelationOperation.ATTACH, null))
        assertTrue(allows("editor", RelationOperation.ASSOCIATE, null), "associate is not declared")
        assertTrue(allows("editor", RelationOperation.DISSOCIATE, null))
        assertTrue(allows("editor", RelationOperation.DETACH, null))
        assertFalse(allows("admin", RelationOperation.RESTORE, row), "the policy does not declare restore")
        assertFalse(allows("admin", RelationOperation.FORCE_DELETE, row), "nor forceDelete")

        // No related class, or no policy: the switch decides.
        assertTrue(allows("editor", RelationOperation.CREATE, null, related = null))
        assertTrue(allows("editor", RelationOperation.CREATE, null, related = AzPlain::class.java))
        assertFalse(allows("editor", RelationOperation.CREATE, null, strict = false, related = AzPlain::class.java))
        Firefly.authorizeWithoutPolicy(false)
        assertFalse(allows("editor", RelationOperation.CREATE, null, related = AzPlain::class.java))
        assertTrue(allows("editor", RelationOperation.CREATE, null, strict = true, related = AzPlain::class.java))

        // A related type that has no record store cannot be loaded; the row falls back to viewAny.
        Firefly.gate(Gate().policy(String::class.java, object : Policy {
            fun viewAny(user: org.konaframework.auth.Authenticatable?): Boolean = true

            override fun authorize(user: org.konaframework.auth.Authenticatable?, ability: String, model: Any): Boolean = false
        }))
        assertTrue(allows("viewer", RelationOperation.EDIT, row, related = String::class.java))
    }

    @Test
    fun theManagerSwitchControlsRelatedModelsWithoutAPolicy() {
        val plain = RelationManager("plain").authorizeWithoutPolicy(false)
        val context = RelationContext(plain, post, null, RelationOperation.CREATE, "edit", false, AzPlain::class.java)
        assertFalse(plain.allows(RelationOperation.CREATE, context))
        assertTrue(RelationManager("plain").allows(RelationOperation.CREATE, context))
        // A read-only relation never mutates.
        val readOnly = RelationContext(plain, post, null, RelationOperation.CREATE, "view", true, AzNote::class.java)
        assertFalse(RelationManager("plain").allows(RelationOperation.CREATE, readOnly))
    }

    // ——— Fixtures ———

    private fun notes(): RelationManager = RelationManager("notes")
        .recordTitleAttribute("body")
        .form(Schema.make().components(TextInput.make("body").required()))
        .table { table -> table.columns(TextColumn.make("body")) }

    private fun posts(vararg managers: RelationManager, posts: RelationManager? = null): Resource = object : Resource("posts") {
        init {
            label("Post")
            model(AzPost::class.java)
            val all = if (posts != null) listOf(posts) else if (managers.isEmpty()) listOf(notes()) else managers.toList()
            for (manager in all) relation(manager)
        }

        override fun form(schema: Schema): Schema = schema.components(TextInput.make("title"))

        override fun infolist(schema: Schema): Schema = schema.components(TextEntry.make("title"))
    }

    private fun mount(resource: Resource, guard: AzGuard): Router {
        val router = Router()
        Panel().id("desk").path("admin").guard(guard).resource(resource).mount(router)
        return router
    }

    /** The relation manager markup, without the owner's own header actions. */
    private fun section(html: String): String = html.substring(html.indexOf("""data-ff-relation="notes""""))

    private fun count(haystack: String, needle: String): Int = haystack.split(needle).size - 1

    private fun edit(router: Router, page: String = "edit"): Response {
        val path = "/admin/posts/$page"
        val match = router.find("GET", path) ?: throw AssertionError("missing $path")
        return match.route.action.handle(Request("GET", path, emptyMap(), mapOf("id" to post.getKey().toString()), ""))
    }

    private fun relationPost(router: Router, operation: String, body: String, page: String = "edit"): Response {
        val path = "/admin/posts/relations/$operation"
        val match = router.find("POST", path) ?: throw AssertionError("missing $path")
        val query = mapOf("relation" to "notes", "owner" to post.getKey().toString(), "page" to page)
        val encoded = body.split('&').joinToString("&") { pair ->
            "${URLEncoder.encode(pair.substringBefore('='), Charsets.UTF_8)}=${URLEncoder.encode(pair.substringAfter('=', ""), Charsets.UTF_8)}"
        }
        return match.route.action.handle(
            Request("POST", path, mapOf("Content-Type" to "application/x-www-form-urlencoded"), query, encoded),
        )
    }
}
