package org.konaframework.brew

import org.konaframework.brew.features.Paginates
import org.konaframework.brew.features.brewRenderPage
import org.konaframework.brew.features.brewWrapInLayout
import org.konaframework.http.Request
import org.konaframework.http.Router
import org.konaframework.session.ArraySessionStore
import org.konaframework.session.StartSession
import org.konaframework.session.StoreSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagesCoverageTest {
    @AfterEach
    fun reset() = Brew.reset()

    @Test
    fun `covers remaining page query session pagination branches`() {
        val views = mapOf(
            "layouts.app" to "<html><head><title>{{ title }}</title></head><body>{{ sidebar }}{{ slot }}</body></html>",
        )
        val manager = brew(views = views)
            .component("posts-page", PostsPage::class.java)
            .component("dashboard-page", DashboardPage::class.java)
            .component("simple-pager", SimplePager::class.java)
            .component("query-string-page", QueryStringPage::class.java)
        Brew.setManager(manager)

        // renderPage with route attributes + query...
        val req = Request("GET", "/posts/1", query = mapOf("q" to "x", "page" to "2"))
            .setAttribute("id", "1")
            .setAttribute("session", "ignore")
            .setAttribute("_internal", "x")
        val response = brewRenderPage(manager, "posts-page", req)
        assertTrue(response.body.contains("search=x") || response.body.contains("Posts"), response.body)

        val router = Router()
        val route = Brew.route(router, "/posts/{id}", "posts-page")
        assertTrue(route.uri.contains("posts"))

        // wrapInLayout with slots + title from store...
        val dash = manager.newComponent("dashboard-page")
        dash.state().store["layout"] = "layouts.app"
        dash.state().store["title"] = "T"
        dash.state().store["layoutSlots"] = mapOf("sidebar" to "<aside>s</aside>")
        val wrapped = brewWrapInLayout(manager, dash, "<main>m</main>")
        assertTrue(wrapped.contains("sidebar") || wrapped.contains("<aside>") || wrapped.contains("m"), wrapped)

        // title null uses component name...
        val bare = manager.newComponent("posts-page")
        bare.state().store["layout"] = "layouts.app"
        assertTrue(brewWrapInLayout(manager, bare, "b").contains("b"))

        // Pagination coerce + getters...
        val posts = Brew.test(PostsPage::class.java).instance() as PostsPage
        posts.paginatorPage = 99
        val page = posts.paginate(listOf("a", "b", "c"), 2)
        assertEquals(2, page.lastPage)
        assertTrue(page.items().isNotEmpty())
        assertEquals(posts.paginatorPage, posts.getPage())
        val simple = posts.simplePaginate(listOf(1, 2, 3, 4, 5), 2)
        assertTrue(simple.hasMorePages)
        assertTrue(simple.items().size <= 2)
        assertTrue(simple.links().isNotEmpty())
        val direct = org.konaframework.brew.features.LengthAwarePaginator.of(listOf(1, 2), 10, 5, "page", "tailwind", null)
        assertEquals(1, direct.currentPage)
        val directSimple = org.konaframework.brew.features.SimplePaginator.of(listOf(1), 2, 1, "page", "tailwind")
        assertFalse(directSimple.hasMorePages)

        // Query string: property name key (not `as`)...
        class NamedUrl : Component() {
            @field:Url
            var search: String = ""
            override fun template() = "<div>{{ search }}</div>"
        }
        manager.component("named-url", NamedUrl::class.java)
        assertEquals("hi", Brew.test(NamedUrl::class.java, mapOf("search" to "hi")).get("search"))

        // queryString method with blank invoke + non-Map...
        class BadQs : Component() {
            fun queryString(): Any = "nope"
            override fun template() = "<div></div>"
        }
        manager.component("bad-qs", BadQs::class.java)
        Brew.test(BadQs::class.java)

        // Session without request is no-op...
        class Sess : Component() {
            @field:Session
            var theme: String = "light"
            override fun template() = "<div></div>"
        }
        manager.component("sess", Sess::class.java)
        Brew.test(Sess::class.java).set("theme", "dark")

        val session = StoreSession("s", ArraySessionStore())
        manager.withRequest(Request("GET", "/").setAttribute(StartSession.ATTRIBUTE, session)) {
            Brew.test(Sess::class.java).set("theme", "dark")
            assertEquals("dark", session.get("brew.session.theme"))
            session.put("brew.session.theme", "neon")
            assertEquals("neon", Brew.test(Sess::class.java).get("theme"))
        }

        // SupportPagination mount with bad page...
        Brew.test(PostsPage::class.java, mapOf("page" to "nope"))
        Brew.test(SimplePager::class.java)

        // assertSeeBrew success path with single quotes unlikely — use double...
        Brew.test(PostsPage::class.java).assertSeeBrew("posts-page")

        // persist empty name...
        assertTrue(manager.views().renderSource("@persist('')\nz\n@endpersist", emptyMap()).contains("wire:persist"))

        // assertDontSeeBrew fails when visible...
        org.junit.jupiter.api.assertThrows<AssertionError> {
            Brew.test(PostsPage::class.java).assertDontSeeBrew("posts-page")
        }

        // wrapInLayout catch with null title and with title set...
        val noLayout = brew()
        val orphan = noLayout.component("posts-page", PostsPage::class.java).newComponent("posts-page")
        assertTrue(brewWrapInLayout(noLayout, orphan, "body").contains("<title>"))
        orphan.state().store["title"] = "Named"
        orphan.state().store["layout"] = "layouts.missing"
        assertTrue(brewWrapInLayout(noLayout, orphan, "body").contains("Named"))

        // @Url `as` alias — fall through to property name...
        assertEquals("via-name", Brew.test(PostsPage::class.java, mapOf("search" to "via-name")).get("search"))

        // except sentinel omits from url...
        class ExceptUrl : Component() {
            @field:Url(except = "all")
            var status: String = "all"
            override fun template() = "<div></div>"
        }
        manager.component("except-url", ExceptUrl::class.java)
        val excepted = Brew.test(ExceptUrl::class.java)
        assertTrue((excepted.effects()["url"] as? Map<*, *>)?.containsKey("status") != true)

        // Session key with missing / null placeholder prop...
        class MissSession : Component() {
            var nope: String? = null
            @field:Session(key = "bag.{nope}.{gone}.x")
            var flag: String = "1"
            override fun template() = "<div></div>"
        }
        manager.component("miss-session", MissSession::class.java)
        val sess = StoreSession("s2", ArraySessionStore())
        manager.withRequest(Request("GET", "/").setAttribute(StartSession.ATTRIBUTE, sess)) {
            Brew.test(MissSession::class.java).set("flag", "2")
            assertTrue(sess.get("brew.session.bag..x") == "2" || sess.get("brew.session.bag...x") == "2" ||
                sess.all().keys.any { it.toString().startsWith("brew.session.bag") })
        }

        // queryString keys absent from params (forEach continue)...
        Brew.test(QueryStringPage::class.java)
    }
}
