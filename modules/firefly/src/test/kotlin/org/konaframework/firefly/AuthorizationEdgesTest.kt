package org.konaframework.firefly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.auth.authorization.Gate
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.util.function.Supplier

/** Less common paths of resource authorization: unloadable rows, tenants and overridden hooks. */
class AuthorizationEdgesTest {
    @BeforeEach
    fun setUp() {
        azDatabase()
        Firefly.reset()
        Firefly.gate(Gate().policy(AzPost::class.java, AzPostPolicy()))
    }

    @AfterEach
    fun tearDown() {
        Firefly.reset()
    }

    @Test
    fun aRowWhoseRecordCannotBeLoadedIsHidden() {
        val guard = AzGuard().signIn("admin")
        val resource = object : Resource("posts") {
            init {
                model(AzPost::class.java)
            }

            override fun table(table: Table<*, *>): Table<*, *> =
                Table.memory(mapOf("id" to "99", "title" to "Ghost")).columns(TextColumn.make("title"))
        }
        val html = get(mount(resource, guard), "/admin/posts/list").body
        assertTrue(html.contains("Ghost"))
        assertFalse(html.contains("""data-ff-action="edit""""), "no record, no edit")
        assertFalse(html.contains("""data-ff-action="delete""""))
    }

    @Test
    fun aKeyTheDatabaseRejectsLoadsNothing() {
        val resource = posts()
        assertNull(resource.recordForAuth("not-a-number"))
        assertNull(resource.recordForAuth("12345"))
    }

    @Test
    fun theRecordMustBelongToTheCurrentTenant() {
        val post = AzPost().fill(mapOf("title" to "Mine", "status" to "draft", "team" to "a")).also { it.save() }
        val resource = posts().tenant("team")
        val key = post.getKey().toString()
        assertNotNull(FireflyTenant.use("a", Supplier { resource.recordForAuth(key) }))
        assertNull(FireflyTenant.use("b", Supplier { resource.recordForAuth(key) }))
        assertNotNull(resource.recordForAuth(key), "no tenant selected")
        assertNotNull(posts().recordForAuth(key), "a resource without a tenant column")
        val unowned = AzPost().fill(mapOf("title" to "Nobody", "status" to "draft")).also { it.save() }
        assertNull(FireflyTenant.use("a", Supplier { resource.recordForAuth(unowned.getKey().toString()) }), "no owner")
    }

    @Test
    fun editPagesCopeWithMissingRecordsAndHiddenViews() {
        val post = AzPost().fill(mapOf("title" to "Draft", "status" to "draft")).also { it.save() }
        val guard = AzGuard().signIn("admin")
        val router = mount(posts(), guard)
        assertEquals(200, get(router, "/admin/posts/edit").status, "no record id: a blank form")
        assertEquals(200, get(router, "/admin/posts/edit", mapOf("id" to "999")).status, "an unknown record id")

        val blind = object : Resource("posts") {
            init {
                model(AzPost::class.java)
            }

            override fun canView(record: Any): Boolean = false
        }
        val html = get(mount(blind, guard), "/admin/posts/edit", mapOf("id" to post.getKey().toString())).body
        assertTrue(html.contains("""data-ff-path="view""""))
        assertTrue(html.contains("ff-hidden"))
    }

    @Test
    fun anOverriddenRowHookDecidesWithoutAPolicy() {
        AzPlain().fill(mapOf("name" to "Plain")).save()
        val resource = object : Resource("plain") {
            init {
                model(AzPlain::class.java)
            }

            override fun canEdit(record: Any): Boolean = false

            override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name"))
        }
        val html = get(mount(resource, AzGuard().signIn("admin")), "/admin/plain/list").body
        assertTrue(html.contains("Plain"))
        assertFalse(html.contains("""data-ff-action="edit""""))
        assertTrue(html.contains("""data-ff-action="delete""""), "no policy: delete stays allowed")
    }

    @Test
    fun aResourceWithoutAModelAllowsItsRows() {
        val resource = object : Resource("memo") {
            override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

            override fun table(table: Table<*, *>): Table<*, *> =
                Table.memory(mapOf("id" to "1", "name" to "Note")).columns(TextColumn.make("name"))
        }
        val html = get(mount(resource, AzGuard().signIn("viewer")), "/admin/memo/list").body
        assertTrue(html.contains("Note"))
    }

    private fun posts(): Resource = object : Resource("posts") {
        init {
            label("Post")
            model(AzPost::class.java)
        }

        override fun form(schema: Schema): Schema = schema.components(TextInput.make("title"))

        override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("title"))
    }

    private fun mount(resource: Resource, guard: AzGuard): Router {
        val router = Router()
        Panel().id("desk").path("admin").guard(guard).resource(resource).mount(router)
        return router
    }

    private fun get(router: Router, path: String, query: Map<String, String> = emptyMap()): Response {
        val match = router.find("GET", path) ?: throw AssertionError("missing GET $path")
        return match.route.action.handle(Request("GET", path, emptyMap(), query, ""))
    }
}
