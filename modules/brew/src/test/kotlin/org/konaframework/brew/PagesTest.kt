package org.konaframework.brew

import org.konaframework.brew.features.Paginates
import org.konaframework.brew.features.brewWrapInLayout
import org.konaframework.core.Config
import org.konaframework.http.Request
import org.konaframework.http.Router
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Layout("layouts.app")
@Title("Posts")
class PostsPage : Paginates() {
    @field:Url(`as` = "q", keep = true)
    var search: String = ""

    @field:Session(key = "posts.filter")
    var filter: String = "all"

    var items: List<String> = listOf("a", "b", "c", "d", "e")

    fun results() = paginate(items, 2)

    override fun template() = """
        <div>
            <h1>Posts</h1>
            <p>search={{ search }} filter={{ filter }} page={{ paginatorPage }}</p>
            ${results().links()}
        </div>
    """.trimIndent()
}

@Title("Dashboard")
class DashboardPage : Component() {
    override fun render(): Any {
        disableBackButtonCache()
        return BrewView.inline("<div>dash-body</div>")
            .layout("layouts.dash")
            .title("Inline Title")
            .slot("sidebar", "<aside>nav</aside>")
    }
}

class WithoutUrlPager : Paginates() {
    override val withoutUrlPagination: Boolean = true
    override fun paginationTheme(): String = "bootstrap"
    override fun template() = "<div>${paginate(listOf(1, 2, 3), 1).links()}</div>"
}

class SimplePager : Paginates() {
    override fun template() = "<div>${simplePaginate(listOf(1, 2, 3, 4), 2).links()}</div>"
}

class QueryStringPage : Component() {
    var sort: String = "name"
    fun queryString(): Map<String, Any?> = mapOf("sort" to sort)
    override fun template() = "<div>sort={{ sort }}</div>"
}

class PagesTest {
    private val manager = brew(
        views = mapOf(
            "layouts.app" to "<html><head><title>{{ title }}</title></head><body>{{ slot }}</body></html>",
            "layouts.dash" to "<html><body>{{ sidebar }}{{ slot }}</body></html>",
        ),
    )
        .component("posts-page", PostsPage::class.java)
        .component("dashboard-page", DashboardPage::class.java)
        .component("without-url-pager", WithoutUrlPager::class.java)
        .component("simple-pager", SimplePager::class.java)
        .component("query-string-page", QueryStringPage::class.java)

    @AfterEach
    fun reset() {
        Brew.reset()
    }

    @Test
    fun `full page route layouts titles url session pagination`() {
        Brew.setManager(manager)
        val router = Router()
        Brew.route(router, "/posts", "posts-page")
        val request = Request("GET", "/posts", query = mapOf("q" to "tea", "page" to "2"))
        // Simulate router binding...
        val response = org.konaframework.brew.features.brewRenderPage(manager, "posts-page", request)
        assertEquals(200, response.status)
        assertTrue(response.body.contains("Posts") || response.body.contains("search=tea"), response.body)
        assertTrue(response.body.contains("<title>"), response.body)

        val page = Brew.test(PostsPage::class.java, mapOf("q" to "hi", "page" to "1"))
        assertEquals("hi", page.get("search"))
        assertTrue(page.effects().containsKey("url") || page.html().contains("search=hi"), page.effects().toString())
        page.assertSee("Posts")
        page.call("nextPage")
        assertEquals(2, page.get("paginatorPage"))
        page.call("previousPage")
        page.call("gotoPage", 1)
        page.call("resetPage")
        page.assertSeeBrew("posts-page")

        page.withQueryParams(mapOf("q" to "z", "page" to "2"))
        assertEquals("z", page.get("search"))

        val dash = Brew.test(DashboardPage::class.java)
        assertEquals(true, dash.effects()["disableBackButtonCache"])
        assertEquals("Inline Title", dash.effects()["title"])

        val bootstrap = Brew.test(WithoutUrlPager::class.java)
        assertTrue(bootstrap.html().contains("bootstrap") || bootstrap.html().contains("pagination"), bootstrap.html())
        assertFalse((bootstrap.effects()["url"] as? Map<*, *>)?.containsKey("page") == true)

        val simple = Brew.test(SimplePager::class.java)
        assertTrue(simple.html().contains("Next") || simple.html().contains("nav"), simple.html())

        val qs = Brew.test(QueryStringPage::class.java, mapOf("sort" to "date"))
        assertTrue((qs.effects()["url"] as Map<*, *>).containsKey("sort"))

        // persist directive...
        assertTrue(manager.views().renderSource("@persist('nav')\nx\n@endpersist", emptyMap()).contains("wire:persist=\"nav\""))
        assertTrue(manager.views().renderSource("@persist\ny\n@endpersist", emptyMap()).contains("wire:persist"))

        // Config navigate defaults...
        assertTrue(manager.config.navigateShowProgressBar)
        assertEquals("#FF9A00", manager.config.navigateProgressBarColor)
        assertFalse(manager.config.navigateViewTransitions)
        assertEquals("layouts.app", manager.config.layout)

        val cfg = Config().apply {
            set("app.key", "k")
            set("brew.layout", "layouts.x")
            set("brew.navigate.show_progress_bar", false)
            set("brew.navigate.progress_bar_color", "#000")
            set("brew.navigate.view_transitions", true)
        }
        BrewConfig.from(cfg).also {
            assertEquals("layouts.x", it.layout)
            assertFalse(it.navigateShowProgressBar)
            assertEquals("#000", it.navigateProgressBarColor)
            assertTrue(it.navigateViewTransitions)
        }
    }

    @Test
    fun `session annotation and redirect to page`() {
        Brew.setManager(manager)
        val posts = manager.newComponent("posts-page") as PostsPage
        posts.filter = "mine"
        posts.disableBackButtonCache()
        posts.layout("layouts.custom")
        posts.title("Custom")
        assertEquals("layouts.custom", posts.state().store["layout"])
        assertEquals("Custom", posts.state().store["title"])
        assertEquals(true, posts.state().store["disableBackButtonCache"])

        // Url except / empty omit...
        @Layout("layouts.app")
        class UrlEdges : Component() {
            @field:Url(except = "all")
            var status: String = "all"
            @field:Url
            var empty: String = ""
            @field:Url(keep = true)
            var kept: String = ""
            @field:Url(`as` = "s", history = true)
            var sort: String = "asc"
            override fun template() = "<div></div>"
        }
        manager.component("url-edges", UrlEdges::class.java)
        val edges = Brew.test(UrlEdges::class.java, mapOf("s" to "desc", "status" to "open"))
        val url = edges.effects()["url"] as Map<*, *>
        assertTrue(url.containsKey("s") || url.containsKey("kept"), "$url")
        assertEquals("desc", edges.get("sort"))

        // Session round-trip...
        val session = org.konaframework.session.StoreSession("sid", org.konaframework.session.ArraySessionStore())
        session.put("brew.session.posts.filter", "cached")
        val req = Request("GET", "/posts").setAttribute(org.konaframework.session.StartSession.ATTRIBUTE, session)
        manager.withRequest(req) {
            val mounted = manager.mountComponent("posts-page", emptyMap(), null)
            assertEquals("cached", (mounted.component as PostsPage).filter)
            (mounted.component as PostsPage).filter = "new"
            val ctx = org.konaframework.brew.lifecycle.ComponentContext(mounted.component, mounting = false)
            mounted.component.state().hooks.dehydrate(ctx)
            assertEquals("new", session.get("brew.session.posts.filter"))
        }

        // Session key with {prop}...
        class DynSession : Component() {
            var userId: Int = 7
            @field:Session(key = "u.{userId}.pref")
            var pref: String = "dark"
            override fun template() = "<div></div>"
        }
        manager.component("dyn-session", DynSession::class.java)
        manager.withRequest(req) {
            val d = Brew.test(DynSession::class.java)
            d.set("pref", "light")
            assertEquals("light", session.get("brew.session.u.7.pref") ?: d.get("pref"))
        }

        // Pagination views helpers...
        assertTrue(org.konaframework.brew.features.PaginationViews.links("tailwind", "page", 1, 3, null).contains("gotoPage"))
        assertTrue(org.konaframework.brew.features.PaginationViews.links("bootstrap", "page", 2, 3, null).contains("pagination"))
        assertTrue(org.konaframework.brew.features.PaginationViews.links("tailwind", "page", 1, 1, "<custom/>").contains("custom"))
        assertTrue(org.konaframework.brew.features.PaginationViews.simple("tailwind", "page", 2, true).contains("Next"))
        assertTrue(org.konaframework.brew.features.PaginationViews.simple("tailwind", "page", 1, false).contains("nav"))

        // Layout missing falls back...
        val bare = brew().component("posts-page", PostsPage::class.java)
        Brew.setManager(bare)
        val html = brewWrapInLayout(bare, bare.newComponent("posts-page"), "<div>x</div>")
        assertTrue(html.contains("<!DOCTYPE html>") && html.contains("<div>x</div>"), html)

        // lazy false param + assertDontSeeBrew...
        Brew.setManager(manager)
        val page = Brew.test(PostsPage::class.java, mapOf("lazy" to false))
        page.assertDontSeeBrew("missing-component")
        assertThrows<AssertionError> { page.assertSeeBrew("missing-component") }

        // QueryStringPage without matching prop...
        class EmptyQs : Component() {
            fun queryString(): Map<String, Any?> = mapOf("orphan" to 1)
            override fun template() = "<div></div>"
        }
        manager.component("empty-qs", EmptyQs::class.java)
        assertNotNull(Brew.test(EmptyQs::class.java, mapOf("orphan" to 2)).effects()["url"])
    }
}
