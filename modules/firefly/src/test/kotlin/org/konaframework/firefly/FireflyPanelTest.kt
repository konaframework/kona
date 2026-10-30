package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.auth.Auth
import org.konaframework.auth.AuthManager
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.Guard
import org.konaframework.auth.HasCredentials
import org.konaframework.auth.HasEmail
import org.konaframework.auth.HasName
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
import org.konaframework.firefly.widgets.Page
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
            Panel().path("").resource(object : Resource("wire") {
                override fun getPages(): Map<String, Page> = mapOf("update" to ListPage("update"))
            }).mount(Router())
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
        panel.page(PanelPage("")).page(CustomPage("note")).resource(object : Resource("box") {
            override fun getPages(): Map<String, Page> = mapOf("" to PanelPage(""))
        })
        panel.mount(router)
        assertEquals(302, send(router, "GET", "/admin/note").status)
        val loginHtml = send(router, "GET", "/admin/login").body
        assertTrue(loginHtml.contains("ff-login"))
        assertTrue(loginHtml.contains("ff-auth-shell"))
        assertTrue(loginHtml.contains("ff-auth-form"))
        assertFalse(loginHtml.contains("""class="ff-sidebar""""))
        assertFalse(loginHtml.contains("""class="ff-topbar""""))
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
        assertEquals(302, send(router, "POST", "/admin/logout").status)
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
        // APP: L1 roots (alpha, zeta) in the sidebar; orders (child of alpha) in the top bar.
        val sideStart = html.indexOf("""class="ff-nav ff-nav-side"""")
        val topStart = html.indexOf("""class="ff-nav ff-nav-top"""")
        assertTrue(sideStart >= 0 && topStart >= 0)
        val sideChunk = html.substring(sideStart, html.indexOf("</nav>", sideStart))
        val topChunk = html.substring(topStart, html.indexOf("</nav>", topStart))
        assertTrue(sideChunk.contains(">alpha<"))
        assertTrue(sideChunk.contains(">zeta<"))
        assertTrue(sideChunk.indexOf(">alpha<") < sideChunk.indexOf(">zeta<"))
        assertFalse(sideChunk.contains(">orders<"))
        assertTrue(topChunk.contains(">orders<"))
        assertFalse(topChunk.contains(">zeta<"))
        assertTrue(html.contains("ff-active"))
        assertTrue(html.contains("wire:navigate"))
        assertTrue(html.contains("data-menu=\"1\""))
        assertTrue(html.contains("ff-user-menu"))
        assertTrue(html.contains("WelcomeHook"))
        assertTrue(html.contains(">Desk<"))
        // Leaf L1 with no children → top bar has no secondary nav.
        val zetaHtml = send(app, "GET", "/admin/zeta").body
        assertFalse(zetaHtml.contains("""class="ff-nav ff-nav-top""""))
        assertTrue(zetaHtml.contains("""class="ff-nav ff-nav-side""""))
        // Descendant URL still selects the owning L1 for the top bar.
        val ordersHtml = send(app, "GET", "/admin/orders").body
        assertTrue(ordersHtml.contains("""class="ff-nav ff-nav-top""""))
        assertTrue(
            ordersHtml.substring(
                ordersHtml.indexOf("""class="ff-nav ff-nav-top""""),
                ordersHtml.indexOf("</nav>", ordersHtml.indexOf("""class="ff-nav ff-nav-top"""")),
            ).contains(">orders<"),
        )

        val side = Router()
        Panel().path("side").brand("SideDesk").navigation(NavigationType.SIDE).authenticate(false).page(ListPage("one")).mount(side)
        val sideHtml = send(side, "GET", "/side/one").body
        assertFalse(sideHtml.contains("""class="ff-nav ff-nav-top""""))
        assertTrue(sideHtml.contains("""class="ff-nav ff-nav-side""""))
        assertTrue(sideHtml.contains("ff-sidebar-brand"))
        assertTrue(sideHtml.contains(">SideDesk<"))
        assertTrue(sideHtml.contains("ff-column"))
        assertTrue(sideHtml.contains("ff-scroll"))
        val sideLogin = send(side, "GET", "/side/login").body
        assertTrue(sideLogin.contains("ff-auth-shell"))
        assertTrue(sideLogin.contains("ff-auth-panel"))
        assertFalse(sideLogin.contains("""class="ff-sidebar""""))
        assertFalse(sideLogin.contains("""class="ff-topbar""""))

        val top = Router()
        Panel().path("top").topNavigation().authenticate(false).page(ListPage("one")).mount(top)
        val topHtml = send(top, "GET", "/top/one").body
        assertTrue(topHtml.contains("""class="ff-nav ff-nav-top""""))
        assertFalse(topHtml.contains("""class="ff-nav ff-nav-side""""))

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
        assertTrue(books.body.contains("ff-create"))
        val home = send(router, "GET", "/staff", headers = mapOf("Host" to "ops.example:8080"))
        assertTrue(home.body.contains("Pulse"))
        assertTrue(home.body.contains("ff-dashboard"))
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
        val custom = Panel().reuseAppGuard("web").guard(MemoryPanelGuard())
        assertFalse(custom.usesAppGuard())
        assertEquals("firefly-admin", custom.guardName())
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
        // Grouped resources/{model}/Resource + pages/ stubs: discover Resource only (#327).
        val grouped = root.resolve("grouped")
        Files.createDirectories(grouped.resolve("resources/note/pages"))
        Files.writeString(
            grouped.resolve("resources/note/NestedNoteResource.kt"),
            "package org.konaframework.firefly.discovered.note\nclass NestedNoteResource : Resource(\"notes\")\n",
        )
        Files.writeString(
            grouped.resolve("resources/note/pages/ManageNote.kt"),
            "package org.konaframework.firefly.discovered.note.pages\nclass ManageNote\n",
        )
        val nested = PanelDiscovery.resources(grouped, javaClass.classLoader)
        assertEquals(1, nested.size)
        assertEquals("notes", nested.single().slug())
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
        assertNull(memory.name())
        assertNull(memory.email())
        assertFalse(memory.attempt("ada@example.com", "nope"))
        assertTrue(memory.attempt("ada@example.com", "secret"))
        assertTrue(memory.check())
        assertEquals("Ada", memory.name())
        assertEquals("ada@example.com", memory.email())
        memory.logout()
        assertFalse(memory.check())
        assertNull(memory.name())
        assertNull(memory.email())
        assertEquals("Email is blank", memory.register("Ada", " ", "secret"))
        assertEquals("Password is blank", memory.register("Ada", "ada@example.com", " "))
    }

    @Test
    fun reuseAppGuardAuthenticatesThroughAuthManager() {
        val fake = FakeGuard()
        fake.checked = false
        fake.allow = false
        fake.current = null
        Auth.setManager(AuthManager().extend("web", fake))
        try {
            val router = Router()
            Panel().path("admin").reuseAppGuard().page(PanelPage("")).mount(router)
            assertEquals(302, send(router, "GET", "/admin").status)
            assertEquals(422, send(router, "POST", "/admin/login", "email=demo@kona.test&password=password").status)
            fake.allow = true
            fake.checked = true
            fake.current = "1"
            assertEquals(302, send(router, "POST", "/admin/login", "email=demo@kona.test&password=password").status)
            assertEquals(200, send(router, "GET", "/admin").status)
            assertTrue(send(router, "GET", "/admin").body.contains("ff-user"))
            assertTrue(send(router, "GET", "/admin/profile").body.contains("1"))
            assertEquals(302, send(router, "POST", "/admin/logout").status)
            assertFalse(fake.checked)
        } finally {
            Auth.clearManager()
        }
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
        assertEquals(404, send(router, "POST", "/ops/logout").status)
        assertEquals(404, send(router, "POST", "/ops/login").status)
        assertEquals(404, send(router, "GET", "/ops").status)
        val hosted = send(router, "GET", "/ops", headers = mapOf("Host" to "ops.example"))
        assertTrue(hosted.body.contains("HomeWidget"))
        val plain = Router()
        Panel().path("plain").authenticate(false).brand("A & B <ok> \"go\"").page(ListPage("one")).mount(plain)
        assertTrue(send(plain, "GET", "/plain/one").body.contains("A &amp; B &lt;ok&gt; &quot;go&quot;"))
        val wide = Router()
        Panel().path("wide").authenticate(false).maxContentWidth("full").page(ListPage("one")).mount(wide)
        val wideHtml = send(wide, "GET", "/wide/one").body
        assertTrue(wideHtml.contains("""data-ff-max-width="full""""))
        assertTrue(wideHtml.contains("ff-list"))
        assertThrows<FireflyException> { Panel().maxWidth("huge") }
        val pageWide = Router()
        Panel().path("pagewide").authenticate(false).page(ListPage("one").maxWidth("xl")).mount(pageWide)
        assertTrue(send(pageWide, "GET", "/pagewide/one").body.contains("""data-ff-max-width="xl""""))
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

    @Test
    fun emptyPanelGetsADashboardHomeAndCsrfOnAuth() {
        val router = Router()
        Panel().path("admin").authenticate(false).brand("Desk").mount(router)
        val home = send(router, "GET", "/admin")
        assertEquals(200, home.status)
        assertTrue(home.body.contains("ff-dashboard"))
        assertTrue(home.body.contains("Welcome"))
        assertFalse(home.body.contains("""name="csrf-token""""))

        val bare = DashboardPage()
        assertTrue(bare.render().contains("ff-dashboard"))
        assertTrue(bare.render().contains(">Dashboard<"))
        assertEquals("", FireflyCsrf.field(null))
        assertEquals("", FireflyCsrf.field(""))
        assertEquals("", FireflyCsrf.field("   "))
        assertTrue(FireflyCsrf.field("abc").contains("abc"))

        val session = object : org.konaframework.http.RequestSession {
            private val data = linkedMapOf<String, Any?>("_token" to "   ")
            override fun get(key: String): Any? = data[key]
            override fun put(key: String, value: Any?): org.konaframework.http.RequestSession {
                data[key] = value
                return this
            }
            override fun flash(key: String, value: Any?): org.konaframework.http.RequestSession = this
            override fun flashInput(input: Map<String, String>): org.konaframework.http.RequestSession = this
            override fun old(key: String, default: String?): String? = default
            override fun oldInput(): Map<String, String> = emptyMap()
            override fun previousUrl(): String? = null
            override fun setPreviousUrl(url: String): org.konaframework.http.RequestSession = this
        }
        val login = sendWithSession(router, "GET", "/admin/login", session)
        assertTrue(login.body.contains("""name="csrf-token""""))
        assertTrue(login.body.contains("""name="_token""""))
        val token = session.get("_token") as String
        assertTrue(token.isNotBlank())
        assertTrue(token != "   ")
        assertTrue(login.body.contains(token))
        val page = sendWithSession(router, "GET", "/admin", session)
        assertTrue(page.body.contains("""name="csrf-token""""))
        assertTrue(page.body.contains(token))
        val again = sendWithSession(router, "GET", "/admin/login", session)
        assertTrue(again.body.contains(token))

        session.put("_token", 42)
        val regenerated = sendWithSession(router, "GET", "/admin/login", session)
        val next = session.get("_token") as String
        assertTrue(next.isNotBlank())
        assertTrue(regenerated.body.contains(next))
        assertTrue(next != "42")

        val rooted = Router()
        Panel().path("root").authenticate(false).item(
            NavigationItem("home").label("Home").url("/").icon("home"),
        ).page(ListPage("one")).mount(rooted)
        val rootNav = send(rooted, "GET", "/root/one").body
        assertTrue(rootNav.contains("ff-nav"))
        assertTrue(rootNav.contains("ff-nav-icon"))
        assertTrue(rootNav.contains("ff-nav-label"))
        assertFalse(activeItem("/root/one", "/"))
        assertTrue(activeItem("/root/one", "/root/one"))
        assertFalse(activeItem("/root/one", "/other"))
        assertFalse(activeItem("/root/one", ""))
        assertTrue(activeItem("/root/one/extra", "/root/one"))
        assertTrue(activeItem("/", "/"))

        val beforeMount = Panel().path("admin").authenticate(false).navigation(NavigationType.APP)
        assertTrue(beforeMount.render(Request("GET", "/admin")).contains("ff-panel"))

        val dashRouter = Router()
        val kpis = org.konaframework.firefly.widgets.StatsOverviewWidget.make("kpis").apply {
            lazy(false)
            pollingInterval(null)
            stats(org.konaframework.firefly.widgets.Stat().label("Orders").value("3"))
        }
        val dash = DashboardPage().apply {
            title("Home")
            icon("home")
            columns(3)
            filtersForm(Schema().components(Text.make("period")))
            widgets(kpis)
        }
        Panel().path("desk").authenticate(false).page(dash).mount(dashRouter)
        val desk = send(dashRouter, "GET", "/desk").body
        assertTrue(desk.contains("Orders"))
        assertTrue(desk.contains("data-ff-dashboard-columns"))
        assertTrue(desk.contains("ff-nav-icon"))
        assertTrue(desk.contains("period") || desk.contains("ff-filters") || desk.contains("ff-dashboard-filters"))
        val fragment = send(dashRouter, "GET", "/desk/widgets/kpis").body
        assertTrue(fragment.contains("Orders"))
        assertTrue(fragment.contains("ff-stat") || fragment.contains("ff-dashboard-widget"))
        assertEquals(404, send(dashRouter, "GET", "/desk/widgets/missing").status)

        // Route attribute + query input for widgetId; blank id → 422; auth redirect; domain gate.
        val match = dashRouter.find("GET", "/desk/widgets/kpis")!!
        val withRoute = Request("GET", "/desk/widgets/kpis").also { it.attributes["widgetId"] = "kpis" }
        assertTrue(match.route.action.handle(withRoute).body.contains("Orders"))
        val withInput = Request("GET", "/desk/widgets/x", emptyMap(), mapOf("widgetId" to "kpis"), "")
        assertTrue(match.route.action.handle(withInput).body.contains("Orders"))
        // Blank route attr falls through; trailing slash /widgets/ → null id → 422.
        val blankId = Request("GET", "/desk/widgets/").also { it.attributes["widgetId"] = "  " }
        assertEquals(422, match.route.action.handle(blankId).status)
        val noMarker = Request("GET", "/desk/home")
        assertEquals(422, match.route.action.handle(noMarker).status)

        val authWidgets = Router()
        val lockedPanel = Panel().path("locked").page(
            DashboardPage().apply {
                widgets(
                    Widget.make("pulse").heading("Pulse").apply {
                        lazy(false)
                        pollingInterval(null)
                    },
                )
            },
        )
        lockedPanel.mount(authWidgets)
        assertEquals(302, send(authWidgets, "GET", "/locked/widgets/pulse").status)
        assertEquals(302, send(authWidgets, "POST", "/locked/register", "name=Ada&email=ada-w@example.com&password=secret").status)
        assertEquals(302, send(authWidgets, "POST", "/locked/login", "email=ada-w@example.com&password=secret").status)
        val unlocked = send(authWidgets, "GET", "/locked/widgets/pulse")
        assertEquals(200, unlocked.status)
        assertTrue(unlocked.body.contains("Pulse") || unlocked.body.contains("ff-widget"))

        val gated = Router()
        Panel().path("ops").domain("ops.example").authenticate(false).page(
            DashboardPage().apply {
                widgets(Widget.make("g").heading("G").apply { lazy(false); pollingInterval(null) })
            },
        ).mount(gated)
        assertEquals(404, send(gated, "GET", "/ops/widgets/g").status)
        assertEquals(
            200,
            send(gated, "GET", "/ops/widgets/g", headers = mapOf("Host" to "ops.example")).status,
        )

        // Clear dashboard pages so showWidget returns "Dashboard not found".
        val cleared = Router()
        val panelCleared = Panel().path("cleared").authenticate(false).page(
            DashboardPage().apply {
                widgets(Widget.make("gone").apply { lazy(false); pollingInterval(null) })
            },
        )
        panelCleared.mount(cleared)
        val pagesField = Panel::class.java.getDeclaredField("pages")
        pagesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (pagesField.get(panelCleared) as MutableList<*>).clear()
        val goneMatch = cleared.find("GET", "/cleared/widgets/gone")!!
        val goneResponse = goneMatch.route.action.handle(Request("GET", "/cleared/widgets/gone"))
        assertEquals(404, goneResponse.status)
        assertTrue(goneResponse.body.contains("Dashboard not found"))

        // Resource + page nav icons; NavigationItem icon provider / missing icon fallback.
        val iconRouter = Router()
        val projects = object : Resource("projects") {
            init {
                icon("folder")
                simple()
            }
        }
        Panel().path("icons").authenticate(false)
            .resource(projects)
            .page(ListPage("reports").icon("chart-bar"))
            .mount(iconRouter)
        val iconHtml = send(iconRouter, "GET", "/icons/reports").body
        assertTrue(iconHtml.contains("ff-nav-icon"))
        assertTrue(iconHtml.contains("Projects") || iconHtml.contains("projects"))

        val get = SchemaBridge.open(emptyMap())
        val viaProvider = NavigationItem("dyn").label("Dyn").url("/dyn").icon { "users" }
        assertTrue(viaProvider.iconName(get).contains("users"))
        assertTrue(NavigationItem("blank").icon { "  " }.iconName(get).contains("squares-2x2"))
        val missingIcon = NavigationItem("bad").label("Bad").url("/bad").icon("not-a-real-icon-zzzzzz")
        val navHtml = renderItems(listOf(missingIcon), "/bad", false, get, false)
        assertTrue(navHtml.contains("ff-nav-icon"))
    }

    @Test
    fun brandingLogosOverridesAndProfilePage() {
        val branded = Router()
        Panel().path("brand")
            .authenticate(false)
            .branding(
                org.konaframework.firefly.support.PanelBranding(
                    name = "Acme",
                    logo = "/branding/logo.svg",
                    logoDark = "/branding/logo-dark.svg",
                    brandDisplay = org.konaframework.firefly.support.BrandDisplay.BOTH,
                    copyright = "© Acme",
                    primaryColor = "#112233",
                ),
            )
            .brandLogoAndName()
            .page(ListPage("one"))
            .mount(branded)
        val html = send(branded, "GET", "/brand/one").body
        assertTrue(html.contains("ff-brand-logo--light"))
        assertTrue(html.contains("/branding/logo.svg"))
        assertTrue(html.contains("Acme"))
        assertTrue(html.contains("© Acme") || html.contains("ff-sidebar-copyright"))
        assertTrue(html.contains("--ff-primary: #112233") || html.contains("#112233"))
        assertTrue(html.contains("""action="/brand/logout"""") || html.contains("/brand/logout") || !html.contains("Sign out"))

        val overrideRouter = Router()
        Panel().path("ov")
            .authenticate(false)
            .brand("Base")
            .logo("/base.svg")
            .resolveBrandingOverrides {
                org.konaframework.firefly.support.PanelBranding(name = "Live", logo = "/live.svg")
            }
            .page(ListPage("one"))
            .mount(overrideRouter)
        val ov = send(overrideRouter, "GET", "/ov/one").body
        assertTrue(ov.contains("Live"))
        assertTrue(ov.contains("/live.svg"))

        val auth = Router()
        val panel = Panel().path("acct").page(PanelPage(""))
        panel.mount(auth)
        assertEquals(302, send(auth, "POST", "/acct/register", "name=Ada&email=ada@acct.test&password=secret").status)
        assertEquals(302, send(auth, "POST", "/acct/login", "email=ada@acct.test&password=secret").status)
        val profile = send(auth, "GET", "/acct/profile")
        assertEquals(200, profile.status)
        assertTrue(profile.body.contains("ff-profile"))
        assertTrue(profile.body.contains("name") || profile.body.contains("Ada"))
        val saved = send(
            auth,
            "POST",
            "/acct/profile",
            "name=Ada Lovelace&email=ada@acct.test&password=&password_confirmation=",
        )
        assertEquals(302, saved.status)
        val again = send(auth, "GET", "/acct/profile").body
        assertTrue(again.contains("Ada Lovelace") || again.contains("Ada"))
        assertEquals(302, send(auth, "POST", "/acct/logout").status)

        class CustomProfile : ProfilePage() {
            override fun formSchema() =
                org.konaframework.firefly.schemas.Schema().components(
                    org.konaframework.firefly.forms.TextInput.make("name").label("Display name"),
                )
        }
        Firefly.defaultProfilePage(CustomProfile())
        try {
            val global = Router()
            Panel().path("glob").authenticate(false).guard(
                MemoryPanelGuard().also {
                    it.register("Bob", "bob@g.test", "secret")
                    it.attempt("bob@g.test", "secret")
                },
            ).mount(global)
            // authenticate false still allows profile when guard checks; ensure signed-in via guard already
            assertTrue(send(global, "GET", "/glob/profile").body.contains("Display name") ||
                send(global, "GET", "/glob/profile").status == 302)
        } finally {
            Firefly.reset()
        }

        val custom = Router()
        Panel().path("cust")
            .authenticate(false)
            .profilePage(CustomProfile())
            .brandLogoOnly()
            .brandNameOnly()
            .brandDisplay(org.konaframework.firefly.support.BrandDisplay.BOTH)
            .logoDark("/dark.svg")
            .logoHeight(48)
            .guard(
                MemoryPanelGuard().also {
                    it.register("Cara", "cara@c.test", "secret")
                    it.attempt("cara@c.test", "secret")
                },
            )
            .mount(custom)
        val custHtml = send(custom, "GET", "/cust/profile").body
        assertTrue(custHtml.contains("Display name") || custHtml.contains("ff-profile"))

        val settings = SettingsPage().canSave { true }
        assertTrue(settings.canSave(MemoryPanelGuard()))
        assertTrue(settings.render().contains("ff-settings") || settings.render().contains("Settings"))
        val settingsDeny = SettingsPage().canSave { false }
        assertFalse(settingsDeny.canSave(MemoryPanelGuard()))
        assertTrue(SettingsPage().canSave(MemoryPanelGuard()))

        val mem = MemoryPanelGuard()
        mem.register("Pat", "pat@t.test", "secret")
        mem.attempt("pat@t.test", "secret")
        assertNull(mem.updateProfile("Pat Two", "pat2@t.test", "newsecret"))
        assertTrue(mem.attempt("pat2@t.test", "newsecret"))
        assertEquals("Email is already registered", run {
            mem.register("Other", "other@t.test", "x")
            mem.attempt("pat2@t.test", "newsecret")
            mem.updateProfile("Pat", "other@t.test", null)
        })
        assertEquals("Not signed in", MemoryPanelGuard().updateProfile("a", "b", null))
        assertEquals("Name is blank", run {
            val g = MemoryPanelGuard()
            g.register("N", "n@t.test", "s")
            g.attempt("n@t.test", "s")
            g.updateProfile(" ", "n@t.test", null)
        })
        assertEquals("Email is blank", run {
            val g = MemoryPanelGuard()
            g.register("N", "n2@t.test", "s")
            g.attempt("n2@t.test", "s")
            g.updateProfile("N", " ", null)
        })

        val errRouter = Router()
        val errPanel = Panel().path("err").page(PanelPage(""))
        errPanel.mount(errRouter)
        send(errRouter, "POST", "/err/register", "name=E&email=e@e.test&password=secret")
        send(errRouter, "POST", "/err/login", "email=e@e.test&password=secret")
        val mismatch = send(
            errRouter,
            "POST",
            "/err/profile",
            "name=E&email=e@e.test&password=one&password_confirmation=two",
        )
        assertEquals(422, mismatch.status)

        Firefly.defaultProfilePage { CustomProfile() }
        try {
            assertNotNull(Firefly.resolveDefaultProfilePage())
        } finally {
            Firefly.reset()
        }

        assertThrows<FireflyException> {
            ProfilePage().save(AuthPanelGuard(FakeGuard()), mapOf("name" to "A", "email" to "a@a.test"))
        }
        assertThrows<FireflyException> {
            ProfilePage().save(
                MemoryPanelGuard().also { it.register("Z", "z@z.test", "s"); it.attempt("z@z.test", "s") },
                mapOf("name" to "", "email" to "z@z.test"),
            )
        }
        assertThrows<FireflyException> {
            ProfilePage().save(
                MemoryPanelGuard().also { it.register("Z", "z2@z.test", "s"); it.attempt("z2@z.test", "s") },
                mapOf("name" to "Z", "email" to ""),
            )
        }
        assertThrows<FireflyException> {
            ProfilePage().save(
                MemoryPanelGuard().also { it.register("Z", "z3@z.test", "s"); it.attempt("z3@z.test", "s") },
                emptyMap(),
            )
        }
        val pwdGuard = MemoryPanelGuard().also {
            it.register("Z", "z4@z.test", "s")
            it.attempt("z4@z.test", "s")
            it.register("Taken", "taken@z.test", "s")
        }
        assertEquals(
            "Profile updated",
            ProfilePage().save(
                pwdGuard,
                mapOf(
                    "name" to "Zed",
                    "email" to "z4@z.test",
                    "password" to "newer",
                    "password_confirmation" to "newer",
                ),
            ),
        )
        assertTrue(pwdGuard.attempt("z4@z.test", "newer"))
        assertThrows<FireflyException> {
            ProfilePage().save(
                pwdGuard,
                mapOf("name" to "Zed", "email" to "taken@z.test"),
            )
        }
        assertEquals(
            "Profile updated",
            ProfilePage().save(
                pwdGuard,
                mapOf("name" to "Zed", "email" to "z4@z.test", "password" to "", "password_confirmation" to ""),
            ),
        )
        assertNull(pwdGuard.updateProfile("Zed", "z4@z.test", ""))
        assertNull(pwdGuard.updateProfile("Zed", "z4@z.test", null))
        val orphan = MemoryPanelGuard().also {
            it.register("O", "o@o.test", "s")
            it.attempt("o@o.test", "s")
        }
        val currentField = MemoryPanelGuard::class.java.getDeclaredField("current")
        currentField.isAccessible = true
        currentField.set(orphan, "ghost@o.test")
        assertEquals("Not signed in", orphan.updateProfile("O", "o@o.test", null))

        assertTrue(SettingsPage().render().contains("Settings") || SettingsPage().render().contains("ff-settings"))
        assertTrue(ProfilePage().render().contains("Profile") || ProfilePage().render().contains("ff-profile"))
        assertTrue(SettingsPage(null).render().contains("ff-settings"))

        Panel().branding(org.konaframework.firefly.support.PanelBranding(logo = "/only.svg"))
            .branding(org.konaframework.firefly.support.PanelBranding(name = "  "))
            .branding(org.konaframework.firefly.support.PanelBranding(name = "Named", copyright = "© A & B <C> \"D\""))

        val copyRouter = Router()
        Panel().path("copy")
            .authenticate(false)
            .branding(
                org.konaframework.firefly.support.PanelBranding(
                    name = "Amp",
                    copyright = "© A & B <C> \"D\"",
                ),
            )
            .page(ListPage("one"))
            .mount(copyRouter)
        val copyHtml = send(copyRouter, "GET", "/copy/one").body
        assertTrue(copyHtml.contains("&amp;") || copyHtml.contains("ff-sidebar-copyright"))

        val csrfSession = object : org.konaframework.http.RequestSession {
            private val data = linkedMapOf<String, Any?>("_token" to "tok-profile")
            override fun get(key: String): Any? = data[key]
            override fun put(key: String, value: Any?): org.konaframework.http.RequestSession {
                data[key] = value
                return this
            }
            override fun flash(key: String, value: Any?): org.konaframework.http.RequestSession = this
            override fun flashInput(input: Map<String, String>): org.konaframework.http.RequestSession = this
            override fun old(key: String, default: String?): String? = default
            override fun oldInput(): Map<String, String> = emptyMap()
            override fun previousUrl(): String? = null
            override fun setPreviousUrl(url: String): org.konaframework.http.RequestSession = this
        }
        val csrfRouter = Router()
        val csrfPanel = Panel().path("csrfprof").page(PanelPage(""))
        csrfPanel.mount(csrfRouter)
        send(csrfRouter, "POST", "/csrfprof/register", "name=C&email=c@c.test&password=secret")
        send(csrfRouter, "POST", "/csrfprof/login", "email=c@c.test&password=secret")
        assertEquals(
            419,
            sendWithSession(
                csrfRouter,
                "POST",
                "/csrfprof/profile",
                csrfSession,
                "name=C&email=c@c.test&_token=wrong",
            ).status,
        )
        assertEquals(
            419,
            sendWithSession(
                csrfRouter,
                "POST",
                "/csrfprof/logout",
                csrfSession,
                "_token=wrong",
            ).status,
        )
        assertEquals(
            302,
            sendWithSession(
                csrfRouter,
                "POST",
                "/csrfprof/profile",
                csrfSession,
                "name=Cee&email=c@c.test&password=&password_confirmation=&_token=tok-profile&_extra=1",
            ).status,
        )
        val profileMatch = csrfRouter.find("POST", "/csrfprof/profile")
            ?: throw AssertionError("missing profile post")
        val mixed = Request(
            "POST",
            "/csrfprof/profile",
            emptyMap(),
            mapOf("ref" to "from-query"),
            "name=Cee&email=c@c.test&password=&password_confirmation=&_token=tok-profile&_skip=1&ref=body",
        )
        mixed.setAttribute(Request.SESSION_ATTRIBUTE, csrfSession)
        assertEquals(302, profileMatch.route.action.handle(mixed).status)
        assertEquals(302, send(csrfRouter, "POST", "/csrfprof/logout").status)
        assertEquals(302, send(csrfRouter, "POST", "/csrfprof/profile", "name=C&email=c@c.test").status)
        assertEquals(302, send(csrfRouter, "GET", "/csrfprof/profile").status)
    }

    @Test
    fun chromeToolsThemeNotificationsAndSignedIn() {
        // Guest panel (no user id): sidebar footer reads "Guest" and the user menu
        // omits the sign-out form. Theme toggle, notifications, drawer, and the
        // no-flash scripts are all present.
        val guestRouter = Router()
        Panel().path("g").navigation(NavigationType.SIDE).authenticate(false).brand("Guesty")
            .page(ListPage("one")).mount(guestRouter)
        val guest = send(guestRouter, "GET", "/g/one").body
        assertTrue(guest.contains("ff-sidebar-foot"))
        assertTrue(guest.contains("Signed in as <span class=\"ff-signed-in\">Guest</span>"))
        assertTrue(guest.contains("""class="ff-icon-btn ff-theme-toggle""""))
        assertTrue(guest.contains("""data-ff-theme-toggle"""))
        assertTrue(guest.contains("""data-ff-theme="system""""))
        assertTrue(guest.contains("ff-theme-icon--light"))
        assertTrue(guest.contains("ff-theme-icon--dark"))
        assertTrue(guest.contains("ff-theme-icon--system"))
        assertTrue(guest.contains("ff-notifications"))
        assertTrue(guest.contains("You&#39;re all caught up.") || guest.contains("You're all caught up."))
        assertTrue(guest.contains("""<button type="button" class="ff-hamburger""""))
        assertTrue(guest.contains("data-ff-sidebar-toggle"))
        assertTrue(guest.contains("data-ff-sidebar-close"))
        assertTrue(guest.contains("""<div class="ff-backdrop" data-ff-backdrop"""))
        assertTrue(guest.contains("""<span class="ff-avatar" aria-hidden="true">G</span>"""))
        assertTrue(guest.contains("""<span class="ff-menu-name">Guest</span>"""))
        assertFalse(guest.contains("ff-user-name"))
        assertTrue(guest.contains("classList.toggle('ff-dark'"))
        assertTrue(guest.contains("localStorage.setItem('ff-theme'"))
        assertFalse(guest.contains(">Sign out<"))

        // Signed-in panel: avatar falls back to "?" when the id has no letter/digit,
        // the footer and menu show the id, and the sign-out form appears. User-menu
        // links keep their url/active state and render without an href when url-less.
        val signed = object : PanelGuard by MemoryPanelGuard() {
            override fun check(): Boolean = true
            override fun id(): String? = "@@@"
        }
        val signedRouter = Router()
        Panel().path("s").navigation(NavigationType.SIDE).guard(signed)
            .userMenu(
                NavigationItem("Current").label("Current").url("/s/one"),
                NavigationItem("Docs").label("Docs").url("/s/docs"),
                NavigationItem("Here").label("Here"),
            )
            .page(ListPage("one")).mount(signedRouter)
        val body = send(signedRouter, "GET", "/s/one").body
        assertTrue(body.contains(">?<"))
        assertTrue(body.contains("Signed in as <span class=\"ff-signed-in\">@@@</span>"))
        assertTrue(body.contains("ff-menu-form"))
        assertTrue(body.contains(">Sign out<"))
        assertTrue(body.contains("""class="ff-menu-link ff-active""""))
        assertTrue(body.contains("""href="/s/docs""""))
        assertTrue(body.contains(">Here<"))

        // TOP navigation keeps the brand in the bar and never mounts a sidebar/drawer.
        val topRouter = Router()
        Panel().path("t").topNavigation().authenticate(false).brand("Toply")
            .page(ListPage("one")).mount(topRouter)
        val top = send(topRouter, "GET", "/t/one").body
        assertFalse(top.contains("""<button type="button" class="ff-hamburger""""))
        assertFalse(top.contains("""<div class="ff-sidebar-foot">"""))
        assertTrue(top.contains("""class="ff-brand">Toply<"""))
        assertTrue(top.contains("ff-theme-toggle"))
    }

    @Test
    fun chromeGreetsWithNameAndEmailNotId() {
        // Full identity: the trigger is avatar-only, the dropdown carries name + email,
        // and the numeric id never surfaces as a label.
        val full = object : PanelGuard by MemoryPanelGuard() {
            override fun check(): Boolean = true
            override fun id(): String? = "7"
            override fun name(): String? = "Demo User"
            override fun email(): String? = "demo@kona.test"
        }
        val fullBody = renderPanel(full)
        assertTrue(fullBody.contains("""<span class="ff-avatar" aria-hidden="true">D</span>"""))
        assertFalse(fullBody.contains("ff-user-name"))
        assertTrue(fullBody.contains("""<span class="ff-menu-name">Demo User</span>"""))
        assertTrue(fullBody.contains("""<span class="ff-menu-sub">demo@kona.test</span>"""))
        assertTrue(fullBody.contains("Signed in as <span class=\"ff-signed-in\">Demo User</span>"))
        assertFalse(fullBody.contains(">7<"))
        assertTrue(fullBody.contains(">Sign out<"))

        // Email-only: the email becomes the dropdown headline and the avatar initial, and the
        // sub-line falls back to the "Signed in as …" phrase instead of repeating the email.
        val emailOnly = object : PanelGuard by MemoryPanelGuard() {
            override fun check(): Boolean = true
            override fun id(): String? = "7"
            override fun name(): String? = null
            override fun email(): String? = "demo@kona.test"
        }
        val emailBody = renderPanel(emailOnly)
        assertTrue(emailBody.contains("""<span class="ff-avatar" aria-hidden="true">D</span>"""))
        assertFalse(emailBody.contains("ff-user-name"))
        assertTrue(emailBody.contains("""<span class="ff-menu-name">demo@kona.test</span>"""))
        assertTrue(emailBody.contains("""<span class="ff-menu-sub">Signed in as demo@kona.test</span>"""))
        assertFalse(emailBody.contains(">7<"))

        // Blank fields are treated as absent: chrome falls back to "Guest" and drops sign-out.
        val blank = object : PanelGuard by MemoryPanelGuard() {
            override fun id(): String? = "   "
            override fun name(): String? = " "
            override fun email(): String? = ""
        }
        val blankBody = renderPanel(blank, authenticate = false)
        assertTrue(blankBody.contains("""<span class="ff-avatar" aria-hidden="true">G</span>"""))
        assertTrue(blankBody.contains("""<span class="ff-menu-name">Guest</span>"""))
        assertFalse(blankBody.contains("ff-user-name"))
        assertTrue(blankBody.contains("Signed in as <span class=\"ff-signed-in\">Guest</span>"))
        assertFalse(blankBody.contains(">Sign out<"))
    }

    @Test
    fun authGuardResolvesNameAndEmailForChrome() {
        val fake = FakeGuard()
        val adapter = AuthPanelGuard(fake)

        // No user bound: chrome has nothing to greet with.
        fake.userValue = null
        assertNull(adapter.name())
        assertNull(adapter.email())

        // Contract-first: HasName / HasEmail win.
        fake.userValue = NamedUser()
        assertEquals("Demo User", adapter.name())
        assertEquals("demo@kona.test", adapter.email())

        // Contracts present but empty: fall back to the credential bag.
        fake.userValue = NullContractUser()
        assertEquals("Creds Name", adapter.name())
        assertEquals("creds@kona.test", adapter.email())

        // Credential bag only (no name/email contracts).
        fake.userValue = CredsOnlyUser()
        assertEquals("Only Creds", adapter.name())
        assertEquals("only@kona.test", adapter.email())

        // Everything empty: nothing to show.
        fake.userValue = NullAllUser()
        assertNull(adapter.name())
        assertNull(adapter.email())

        // Plain authenticatable exposes neither contract nor credentials.
        fake.userValue = PlainUser()
        assertNull(adapter.name())
        assertNull(adapter.email())
    }

    private fun renderPanel(guard: PanelGuard, authenticate: Boolean = true): String {
        val router = Router()
        Panel().path("x").navigation(NavigationType.SIDE).authenticate(authenticate)
            .guard(guard).page(ListPage("one")).mount(router)
        return send(router, "GET", "/x/one").body
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

    private fun sendWithSession(
        router: Router,
        method: String,
        path: String,
        session: org.konaframework.http.RequestSession,
        body: String = "",
    ): Response {
        val match = router.find(method, path) ?: throw AssertionError("missing $method $path")
        val request = Request(method, path, emptyMap(), emptyMap(), body)
        request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
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
    var userValue: Authenticatable? = null

    override fun attempt(credentials: Map<String, Any?>, remember: Boolean): Boolean = allow

    override fun login(user: Authenticatable, remember: Boolean) {
        current = user.getAuthIdentifier().toString()
    }

    override fun logout() {
        checked = false
    }

    override fun check(): Boolean = checked

    override fun id(): Any? = current

    override fun user(): Authenticatable? = userValue

    override fun setRequest(request: Request): Guard = this

    override fun validate(credentials: Map<String, Any?>): Boolean = allow

    override fun loginUsingId(id: Any, remember: Boolean): Authenticatable? = null

    override fun once(credentials: Map<String, Any?>): Boolean = allow

    override fun onceUsingId(id: Any): Boolean = allow
}

private class NamedUser : Authenticatable, HasName, HasEmail {
    override fun getAuthIdentifier(): Any = 7
    override fun getAuthPassword(): String = ""
    override fun getName(): String? = "Demo User"
    override fun getEmail(): String? = "demo@kona.test"
}

private class NullContractUser : Authenticatable, HasName, HasEmail, HasCredentials {
    override fun getAuthIdentifier(): Any = 7
    override fun getAuthPassword(): String = ""
    override fun getName(): String? = null
    override fun getEmail(): String? = null
    override fun getCredential(key: String): Any? = when (key) {
        "name" -> "Creds Name"
        "email" -> "creds@kona.test"
        else -> null
    }
}

private class NullAllUser : Authenticatable, HasName, HasEmail, HasCredentials {
    override fun getAuthIdentifier(): Any = 7
    override fun getAuthPassword(): String = ""
    override fun getName(): String? = null
    override fun getEmail(): String? = null
    override fun getCredential(key: String): Any? = null
}

private class CredsOnlyUser : Authenticatable, HasCredentials {
    override fun getAuthIdentifier(): Any = 7
    override fun getAuthPassword(): String = ""
    override fun getCredential(key: String): Any? = when (key) {
        "name" -> "Only Creds"
        "email" -> "only@kona.test"
        else -> null
    }
}

private class PlainUser : Authenticatable {
    override fun getAuthIdentifier(): Any = 7
    override fun getAuthPassword(): String = ""
}
