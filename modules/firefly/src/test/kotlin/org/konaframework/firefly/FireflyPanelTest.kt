package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.Guard
import org.konaframework.firefly.discovered.BoomPage
import org.konaframework.firefly.discovered.Desk
import org.konaframework.firefly.discovered.FoundPage
import org.konaframework.firefly.discovered.FoundResource
import org.konaframework.firefly.discovered.FoundWidget
import org.konaframework.firefly.discovered.JavaPage
import org.konaframework.firefly.discovered.JavaResource
import org.konaframework.firefly.discovered.NeedsArgPage
import org.konaframework.firefly.discovered.NotAPage
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.ChromeContext
import org.konaframework.firefly.support.DefaultLayout
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.Hook
import org.konaframework.firefly.support.PanelLayout
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.widgets.Widget
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.nio.file.Files

class FireflyPanelTest {
    @Test
    fun mountsAdminAndRootWithoutCatchingFrameworkPaths() {
        val router = Router()
        router.get("/welcome") { Response.html("hello-app") }.name("app.welcome")
        val idle = Panel().id("desk")
        assertEquals("firefly-desk", idle.guardName())
        assertFalse(idle.usesAppGuard())
        assertEquals("hello-app", send(router, "GET", "/welcome").body)

        idle.path("admin").authenticate(false).page(pageBundle("orders")).mount(router)
        assertEquals("hello-app", send(router, "GET", "/welcome").body)
        assertNull(router.find("POST", "/wire/update"))
        assertNull(router.find("GET", "/_firefly/assets"))
        val orders = send(router, "GET", "/admin/orders")
        assertTrue(orders.body.contains("ff-list"))
        assertTrue(orders.body.contains("FormBody"))
        assertTrue(orders.body.contains("InfoBody"))
        assertTrue(orders.body.contains("Ada"))
        assertTrue(orders.body.contains("Refresh"))

        val root = Router()
        root.get("/welcome") { Response.html("still-here") }.name("app.welcome")
        val rooted = Panel().id("root").path("").authenticate(false).page(PanelPage("")).page(ListPage("orders"))
        rooted.mount(root)
        assertEquals("still-here", send(root, "GET", "/welcome").body)
        assertTrue(send(root, "GET", "/orders").body.contains("ff-list"))
        assertTrue(send(root, "GET", "/login").body.contains("ff-login"))
        assertThrows<FireflyException> { Panel().path("").page(ListPage("_firefly")).mount(Router()) }
        assertThrows<FireflyException> {
            Panel().path("").resource(Resource("wire").page(ListPage("update"))).mount(Router())
        }
        assertThrows<FireflyException> {
            Panel().page(ListPage("orders")).page(ListPage("orders")).mount(Router())
        }
        Panel().widget(Widget.make("alone")).mount(Router())
    }

    @Test
    fun authScreensUseASeparateGuard() {
        val router = Router()
        val panel = Panel().id("desk").path("admin")
        panel.page(PanelPage("")).page(CustomPage("note")).resource(Resource("box").page(PanelPage("")))
        panel.mount(router)
        assertEquals(302, send(router, "GET", "/admin/note").status)
        assertTrue(send(router, "GET", "/admin/login").body.contains("ff-login"))
        assertTrue(send(router, "GET", "/admin/register").body.contains("ff-register"))
        assertEquals(422, send(router, "POST", "/admin/login", "email=&password=").status)
        assertEquals(422, send(router, "POST", "/admin/login", "email=ada@example.com&password=nope").status)
        assertEquals(422, send(router, "POST", "/admin/register", "name=&email=&password=").status)
        assertEquals(302, send(router, "POST", "/admin/register", "name=Ada&email=ada@example.com&password=secret").status)
        assertEquals(422, send(router, "POST", "/admin/register", "name=Ada&email=ada@example.com&password=secret").status)
        assertEquals(302, send(router, "POST", "/admin/login", "email=ada@example.com&password=secret").status)
        assertEquals(302, send(router, "GET", "/admin/login").status)
        val profile = send(router, "GET", "/admin/profile")
        assertTrue(profile.body.contains("ada@example.com"))
        assertTrue(profile.body.contains("ff-profile"))
        val home = send(router, "GET", "/admin")
        assertTrue(home.body.contains("ff-page"))
        assertEquals(302, send(router, "POST", "/admin/profile").status)
        assertEquals(302, send(router, "GET", "/admin/profile").status)
        assertTrue(send(router, "GET", "/admin/password-reset").body.contains("ff-password-reset"))
        assertEquals(422, send(router, "POST", "/admin/password-reset", "email=").status)
        assertEquals(422, send(router, "POST", "/admin/password-reset", "email=missing@example.com").status)
        assertTrue(send(router, "POST", "/admin/password-reset", "email=ada@example.com").body.contains("Reset link sent"))
        assertTrue(send(router, "GET", "/admin/email-verify").body.contains("ff-email-verify"))
        assertEquals(422, send(router, "POST", "/admin/email-verify", "email=").status)
        assertEquals(422, send(router, "POST", "/admin/email-verify", "email=missing@example.com").status)
        assertTrue(send(router, "POST", "/admin/email-verify", "email=ada@example.com").body.contains("Email verified"))
        val guard = panelGuard(panel)
        assertTrue(guard.verified("ada@example.com"))
        assertFalse(guard.verified("missing@example.com"))
        assertThrows<FireflyException> { panel.mount(Router()) }
    }

    @Test
    fun navigationTypesNestThroughLevelThree() {
        val app = Router()
        val panel = Panel().path("admin").authenticate(false).spa(true).brand("Desk")
        val zeta = ListPage("zeta")
        zeta.title("Zeta")
        val alpha = ListPage("alpha")
        alpha.title("Alpha")
        panel.page(zeta)
        panel.page(alpha)
        panel.page(ListPage("orders").parent("alpha"))
        panel.userMenu(NavigationItem("Account").label { "Account" }.url { "/admin/profile" }.extraAttributes { mapOf("data-menu" to "1") })
        panel.hook(Hook.BODY_END, "WelcomeHook")
        panel.mount(app)
        val html = send(app, "GET", "/admin/alpha").body
        assertTrue(html.contains("ff-nav-top"))
        assertTrue(html.contains("ff-nav-side"))
        assertTrue(html.indexOf(">alpha<") < html.indexOf(">zeta<"))
        assertTrue(html.contains("ff-active"))
        assertTrue(html.contains("wire:navigate"))
        assertTrue(html.contains("data-menu=\"1\""))
        assertTrue(html.contains("ff-user-menu"))
        assertTrue(html.contains("WelcomeHook"))
        assertTrue(html.contains(">Desk<"))

        val side = Router()
        Panel().path("side").navigation(NavigationType.SIDE).authenticate(false).page(ListPage("one")).mount(side)
        val sideHtml = send(side, "GET", "/side/login").body
        assertFalse(sideHtml.contains("ff-nav-top"))
        assertTrue(sideHtml.contains("ff-nav-side"))

        val top = Router()
        Panel().path("top").topNavigation().authenticate(false).page(ListPage("one")).mount(top)
        val topHtml = send(top, "GET", "/top/login").body
        assertTrue(topHtml.contains("ff-nav-top"))
        assertFalse(topHtml.contains("ff-nav-side"))

        val deep = NavigationItem("Desk")
        val tree = NavigationItem("Sales").child(NavigationItem("Orders").child(NavigationItem("Open").child(deep)))
        val failure = assertThrows<FireflyException> { Panel().item(tree).mount(Router()) }
        assertTrue(failure.message!!.contains("Desk"))
        val nested = Panel().path("nest").authenticate(false)
        nested.page(ListPage("sales")).page(ListPage("orders").parent("sales")).page(ListPage("open").parent("orders"))
        nested.mount(Router())
        val tooDeep = Panel().path("deep")
        tooDeep.page(ListPage("sales")).page(ListPage("orders").parent("sales"))
        tooDeep.page(ListPage("open").parent("orders")).page(ListPage("desk").parent("open"))
        assertThrows<FireflyException> { tooDeep.mount(Router()) }
        panel.render(Request("GET", "/admin/alpha/extra"))
        val missing = assertThrows<FireflyException> {
            Panel().page(ListPage("orders").parent("missing")).mount(Router())
        }
        assertTrue(missing.message!!.contains("missing"))
        assertThrows<FireflyException> { NavigationItem(" ") }
        assertThrows<FireflyException> { NavigationItem("Ok").label(" ") }
        assertThrows<FireflyException> { NavigationItem("Ok").label { " " }.labelText(SchemaBridge.open(emptyMap())) }
        assertThrows<FireflyException> { NavigationItem("Ok").url { " " }.urlText(SchemaBridge.open(emptyMap())) }
    }

    @Test
    fun clustersThemesDomainsAndClosures() {
        val router = Router()
        val panel = Panel()
            .id { "ops" }
            .path { "staff" }
            .brand { "Ops" }
            .domain { "ops.example" }
            .navigation { NavigationType.APP }
            .theme(HarborTheme())
            .layout(WrapLayout())
            .spa(true)
            .authenticate(false)
        panel.cluster("Catalog").child(NavigationItem("Books").label("Books").url("/staff/books").extraAttributes(mapOf("data-nav" to "books")))
        panel.page(CreatePage("create")).page(EditPage("edit")).page(ViewPage("view")).page(ManagePage("manage")).page(CustomPage("kinds"))
        panel.widget(Widget.make("pulse").heading("Pulse"))
        panel.mount(router)
        assertEquals("firefly-ops", panel.guardName())
        val books = send(router, "GET", "/staff/create", headers = mapOf("Host" to "ops.example:8080"))
        assertTrue(books.body.contains("ff-harbor"))
        assertTrue(books.body.contains("#0F6E6E"))
        assertTrue(books.body.contains("ff-harbor-mark"))
        assertTrue(books.body.contains("ff-custom-layout"))
        assertTrue(books.body.contains("ff-cluster"))
        assertTrue(books.body.contains("data-nav=\"books\""))
        assertTrue(books.body.contains("Pulse"))
        assertTrue(books.body.contains("ff-create"))
        assertTrue(send(router, "GET", "/staff/edit", headers = mapOf("Host" to "ops.example")).body.contains("ff-edit"))
        assertTrue(send(router, "GET", "/staff/view", headers = mapOf("Host" to "ops.example")).body.contains("ff-view"))
        assertTrue(send(router, "GET", "/staff/manage", headers = mapOf("Host" to "ops.example")).body.contains("ff-manage"))
        assertTrue(send(router, "GET", "/staff/kinds", headers = mapOf("Host" to "ops.example")).body.contains("ff-custom"))
        assertEquals(404, send(router, "GET", "/staff/login").status)
        assertEquals(404, send(router, "GET", "/staff/login", headers = mapOf("Host" to "other.example")).status)
        assertNull(HarborTheme().partial("topbar"))
        assertThrows<FireflyException> { Panel().id(" ") }
        assertThrows<FireflyException> { Panel().id { " " }.mount(Router()) }
        assertThrows<FireflyException> { Panel().brand(" ") }
        assertThrows<FireflyException> { Panel().brand { " " }.mount(Router()) }
        val blankBrand = Panel().brand { " " }
        assertThrows<FireflyException> { blankBrand.render(Request("GET", "/")) }
        assertThrows<FireflyException> { Panel().domain(" ") }
        assertThrows<FireflyException> { Panel().domain { " " }.mount(Router()) }
        assertThrows<FireflyException> { Panel().reuseAppGuard(" ") }
        val reused = Panel().reuseAppGuard("web")
        assertTrue(reused.usesAppGuard())
        assertEquals("web", reused.guardName())
        val taken = Router()
        taken.get("/admin/login") { Response.html("app-login") }.name("app.login")
        val collision = assertThrows<FireflyException> { Panel().mount(taken) }
        assertTrue(collision.message!!.contains("app.login"))
        assertTrue(collision.message!!.contains("firefly.admin.login"))
        assertEquals("app-login", send(taken, "GET", "/admin/login").body)
    }

    @Test
    fun discoversThePanelDirectoryAndRendersResourcePages() {
        val root = Files.createTempDirectory("firefly-panel")
        assertEquals(emptyList<PanelPage>(), PanelDiscovery.pages(root, javaClass.classLoader))
        assertEquals(emptyList<Resource>(), PanelDiscovery.resources(root, javaClass.classLoader))
        val blocked = root.resolve("blocked")
        Files.createDirectories(blocked)
        Files.writeString(blocked.resolve("pages"), "nope")
        assertThrows<FireflyException> { PanelDiscovery.pages(blocked, javaClass.classLoader) }
        val panelDir = root.resolve("admin")
        writePage(panelDir, "FoundPage.kt", "package org.konaframework.firefly.discovered\nclass FoundPage : PanelPage(\"found\")\n")
        writePage(panelDir, "JavaPage.java", "package org.konaframework.firefly.discovered;\npublic class JavaPage extends CustomPage {}\n")
        writePage(panelDir, "SkipPage.kt", "package org.konaframework.firefly.discovered\nabstract class SkipPage : PanelPage(\"skip\")\n")
        writePage(panelDir, "NotAPage.kt", "package org.konaframework.firefly.discovered\nclass NotAPage\n")
        writePage(panelDir, "notes.txt", "package org.konaframework.firefly.discovered\nclass Notes\n")
        Files.createDirectories(panelDir.resolve("resources"))
        Files.createDirectories(panelDir.resolve("widgets"))
        Files.writeString(panelDir.resolve("resources/FoundResource.kt"), sourceResource("FoundResource"))
        Files.writeString(panelDir.resolve("resources/JavaResource.java"), "package org.konaframework.firefly.discovered;\npublic class JavaResource extends Resource {}\n")
        Files.writeString(panelDir.resolve("resources/SkipResource.kt"), "package org.konaframework.firefly.discovered\nabstract class SkipResource : Resource(\"skip\")\n")
        Files.writeString(panelDir.resolve("widgets/FoundWidget.kt"), "package org.konaframework.firefly.discovered\nclass FoundWidget : Widget(\"found\")\n")
        val pages = PanelDiscovery.pages(panelDir, javaClass.classLoader)
        assertEquals(2, pages.size)
        assertEquals(2, PanelDiscovery.resources(panelDir, javaClass.classLoader).size)
        assertEquals(1, PanelDiscovery.widgets(panelDir).size)
        val router = Router()
        val panel = Panel().path("admin").authenticate(false).discover(panelDir)
        panel.mount(router)
        assertTrue(send(router, "GET", "/admin/found").body.contains("ff-page"))
        assertTrue(send(router, "GET", "/admin/java").body.contains("ff-custom"))
        assertTrue(send(router, "GET", "/admin/notes/all").body.contains("ff-list"))
        assertTrue(send(router, "GET", "/admin/java-notes/all").body.contains("ff-list"))
        assertThrows<FireflyException> { lonePage("NeedsArgPage.kt", "package org.konaframework.firefly.discovered\nclass NeedsArgPage(val n: Int) : PanelPage(\"needs\")\n") }
        assertThrows<FireflyException> { lonePage("BoomPage.kt", "package org.konaframework.firefly.discovered\nclass BoomPage : PanelPage(\"boom\")\n") }
        assertThrows<FireflyException> { lonePage("Loose.kt", "class Loose\n") }
        assertThrows<FireflyException> { lonePage("NoClass.kt", "package org.konaframework.firefly.discovered\n") }
        assertThrows<FireflyException> { lonePage("Mismatch.kt", "package org.konaframework.firefly.discovered\nclass Other\n") }
        assertThrows<FireflyException> { lonePage("Ghost.kt", "package org.missing\nclass Ghost\n") }
        assertEquals("panel", PanelPage().slug())
        assertEquals("desk", Desk().slug())
        assertEquals("Dashboard", PanelPage("").navigationLabel())
        assertEquals("orders", ListPage("orders").navigationLabel())
        val resource = Resource().label("Notes")
        assertEquals("Notes", resource.label())
        assertEquals("resource", Resource().label())
        assertTrue(FoundPage().slug().isNotEmpty())
        assertTrue(FoundResource().pages().isNotEmpty())
        assertTrue(JavaPage().slug().isNotEmpty())
        assertTrue(JavaResource().pages().isNotEmpty())
        assertTrue(FoundWidget().render().contains("ff-widget"))
    }

    @Test
    fun authGuardAdapterAndCustomProfile() {
        val fake = FakeGuard()
        val adapter = AuthPanelGuard(fake)
        assertFalse(adapter.attempt("a", "b"))
        fake.allow = true
        fake.current = null
        assertTrue(adapter.attempt("a", "b"))
        assertNull(adapter.id())
        fake.current = "ada"
        assertEquals("ada", adapter.id())
        assertTrue(adapter.check())
        adapter.logout()
        assertFalse(fake.checked)
        assertEquals("Application guard does not register panel users", adapter.register("A", "a@b.c", "p"))
        assertEquals("Application guard does not reset panel passwords", adapter.requestReset("a@b.c"))
        assertEquals("Application guard does not verify panel email", adapter.verify("a@b.c"))
        val router = Router()
        Panel().path("app").useAuthGuard(fake).authenticate(false).page(ListPage("one")).mount(router)
        assertTrue(send(router, "POST", "/app/register", "name=A&email=a@b.c&password=p").body.contains("does not register"))
        val open = object : PanelGuard by MemoryPanelGuard() {
            override fun check(): Boolean = true
            override fun id(): String? = null
        }
        val profile = Router()
        Panel().path("me").guard(open).mount(profile)
        assertTrue(send(profile, "GET", "/me/profile").body.contains("ff-profile"))
        val memory = MemoryPanelGuard()
        assertFalse(memory.attempt("missing@example.com", "x"))
        memory.register("Ada", "ada@example.com", "secret")
        assertFalse(memory.attempt("ada@example.com", "nope"))
        assertTrue(memory.attempt("ada@example.com", "secret"))
        assertTrue(memory.check())
        memory.logout()
        assertFalse(memory.check())
        assertEquals("Email is blank", memory.register("Ada", " ", "secret"))
        assertEquals("Password is blank", memory.register("Ada", "ada@example.com", " "))
    }

    @Test
    fun coversAuthGatesAndHtmlEscapes() {
        assertEquals("harbor", HarborTheme().id())
        val router = Router()
        Panel().path("ops").domain("ops.example").authenticate(false).page(PanelPage("")).widget(Widget.make("home").heading("HomeWidget")).mount(router)
        assertEquals(404, send(router, "GET", "/ops/register").status)
        assertEquals(404, send(router, "POST", "/ops/register").status)
        assertEquals(404, send(router, "GET", "/ops/password-reset").status)
        assertEquals(404, send(router, "POST", "/ops/password-reset").status)
        assertEquals(404, send(router, "GET", "/ops/email-verify").status)
        assertEquals(404, send(router, "POST", "/ops/email-verify").status)
        assertEquals(404, send(router, "GET", "/ops/profile").status)
        assertEquals(404, send(router, "POST", "/ops/profile").status)
        assertEquals(404, send(router, "POST", "/ops/login").status)
        assertEquals(404, send(router, "GET", "/ops").status)
        val hosted = send(router, "GET", "/ops", headers = mapOf("Host" to "ops.example"))
        assertTrue(hosted.body.contains("HomeWidget"))
        val plain = Router()
        Panel().path("plain").authenticate(false).brand("A & B <ok> \"go\"").page(ListPage("one")).mount(plain)
        assertTrue(send(plain, "GET", "/plain/one").body.contains("A &amp; B &lt;ok&gt; &quot;go&quot;"))
        assertEquals(422, send(plain, "POST", "/plain/login").status)
        assertEquals(422, send(plain, "POST", "/plain/login", "email=ada@example.com").status)
        assertEquals(422, send(plain, "POST", "/plain/login", "email=ada@example.com&password=").status)
        assertEquals(422, send(plain, "POST", "/plain/register").status)
        assertEquals(422, send(plain, "POST", "/plain/password-reset").status)
        assertEquals(422, send(plain, "POST", "/plain/email-verify").status)
        val unnamed = Router()
        unnamed.get("/admin/login") { Response.html("taken") }
        val collision = assertThrows<FireflyException> { Panel().mount(unnamed) }
        assertTrue(collision.message!!.contains("taken") || collision.message!!.contains("login"))
        val blankName = Router()
        blankName.get("/admin/login") { Response.html("taken") }.name("")
        assertThrows<FireflyException> { Panel().mount(blankName) }
        assertThrows<FireflyException> { Panel().path("").page(ListPage("_firefly/assets")).mount(Router()) }
        assertThrows<FireflyException> { Panel().path("").page(ListPage("wire/update/extra")).mount(Router()) }
        assertThrows<FireflyException> { Resource().label(" ") }
        val sales = NavigationItem("sales").child(NavigationItem("orders")).child(NavigationItem("holds"))
        assertNull(sales.find("missing"))
        assertEquals("holds", sales.find("holds")!!.name)
        val boom = Files.createTempDirectory("firefly-boom")
        Files.createDirectories(boom.resolve("pages"))
        Files.writeString(
            boom.resolve("pages/BoomPage.kt"),
            "package org.konaframework.firefly.discovered\nclass BoomPage : PanelPage(\"boom\")\n",
        )
        val exploded = assertThrows<FireflyException> { PanelDiscovery.pages(boom, javaClass.classLoader) }
        assertTrue(exploded.message!!.contains("BoomPage"))
    }

    private fun pageBundle(slug: String): ListPage {
        val page = ListPage(slug)
        page.title("Orders")
        page.headerActions(org.konaframework.firefly.actions.Action.make("refresh").label("Refresh"))
        page.mountForm(Schema().components(TextInput.make("title")))
        page.mountInfolist(Infolist.make().components(Text.make("InfoBody")))
        page.schema(Schema().components(Text.make("FormBody")))
        page.table(
            Table.memory(listOf(mapOf("id" to "1", "name" to "Ada"))).columns(TextColumn.make("name")),
        )
        return page
    }

    private fun panelGuard(panel: Panel): MemoryPanelGuard {
        val field = Panel::class.java.getDeclaredField("panelGuard")
        field.isAccessible = true
        return field.get(panel) as MemoryPanelGuard
    }

    private fun send(
        router: Router,
        method: String,
        path: String,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val match = router.find(method, path) ?: throw AssertionError("missing $method $path")
        return match.route.action.handle(Request(method, path, headers, emptyMap(), body))
    }

    private fun writePage(dir: java.nio.file.Path, name: String, text: String) {
        val file = dir.resolve("pages").resolve(name)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun sourceResource(name: String): String =
        "package org.konaframework.firefly.discovered\nclass $name : Resource(\"$name\")\n"

    private fun lonePage(name: String, text: String) {
        val dir = Files.createTempDirectory("firefly-one")
        writePage(dir, name, text)
        PanelDiscovery.pages(dir, javaClass.classLoader)
    }
}

private class WrapLayout : PanelLayout {
    override fun render(context: ChromeContext): String =
        """<div class="ff-custom-layout">${DefaultLayout().render(context)}</div>"""
}

private class FakeGuard : Guard {
    var allow: Boolean = false
    var current: String? = "ada"
    var checked: Boolean = true

    override fun attempt(credentials: Map<String, Any?>, remember: Boolean): Boolean = allow

    override fun login(user: Authenticatable, remember: Boolean) {
        current = user.getAuthIdentifier().toString()
    }

    override fun logout() {
        checked = false
    }

    override fun check(): Boolean = checked

    override fun id(): Any? = current

    override fun user(): Authenticatable? = null

    override fun setRequest(request: Request): Guard = this

    override fun validate(credentials: Map<String, Any?>): Boolean = allow

    override fun loginUsingId(id: Any, remember: Boolean): Authenticatable? = null

    override fun once(credentials: Map<String, Any?>): Boolean = allow

    override fun onceUsingId(id: Any): Boolean = allow
}
