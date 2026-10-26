package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.cadence.ModelBuilder
import org.konaframework.core.Config
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.Hook
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.testing.Firefly
import org.konaframework.http.Request
import org.konaframework.http.Router
import org.konaframework.queue.Bus
import org.konaframework.queue.QueueManager
import java.nio.file.Files

class FireflyKitTest {
    @Test
    fun tenantMenuAndQueryFollowTheRequest() {
        database()
        val acme = Tenant("acme").label("Acme")
        val beta = Tenant("beta").label { "Beta" }
        assertEquals("acme", Tenant("acme").label())
        assertEquals("Beta", beta.label())
        assertThrows<FireflyException> { Tenant(" ") }
        assertThrows<FireflyException> { Tenant("acme").label(" ") }
        assertThrows<FireflyException> { Tenant("acme").label { " " }.label() }
        val panel = Panel().path("admin").authenticate(false)
            .tenant(acme)
            .tenant(beta)
            .page(CustomPage("desk"))
        val router = Router()
        panel.mount(router)
        val idle = send(router, "/admin/desk", emptyMap())
        assertTrue(idle.contains("ff-tenants"))
        assertTrue(idle.contains("Acme"))
        assertTrue(idle.contains("Beta"))
        assertTrue(!idle.contains("ff-tenant ff-active"))
        val current = send(router, "/admin/desk", mapOf("tenant" to "acme"))
        assertTrue(current.contains("ff-tenant ff-active"))
        send(router, "/admin/desk", mapOf("tenant" to " "))
        assertThrows<FireflyException> { send(router, "/admin/desk", mapOf("tenant" to "nope")) }
        val shelf = Resource("shelf").model(Shelf::class.java).tenant("tenant_id")
        val open = shelf.query() as ModelBuilder<*>
        assertTrue(!open.toSql().contains("tenant_id"))
        val scoped = FireflyTenant.use("acme") { shelf.query() as ModelBuilder<*> }
        assertTrue(scoped.toSql().contains("tenant_id"))
        assertNull(FireflyTenant.id())
        assertThrows<FireflyException> { Resource("shelf").tenant(" ") }
    }

    @Test
    fun pluginUsesThePanelApi() {
        FireflyPlugin.configureUsing(null)
        Panel().plugin(FireflyPlugin())
        val plain = FireflyPlugin()
        assertEquals("fireflyplugin", plain.id())
        assertNull(plain.view())
        plain.register(Panel())
        plain.boot(Panel())
        FireflyPlugin.configureUsing { it.view("plugins/banner.crema.html") }
        val banner = BannerPlugin()
        val panel = Panel().path("admin").authenticate(false).plugin(banner)
        assertEquals("plugins/banner.crema.html", banner.view())
        assertThrows<FireflyException> { panel.plugin(BannerPlugin()) }
        assertThrows<FireflyException> { FireflyPlugin().view(" ") }
        val router = Router()
        panel.mount(router)
        val html = send(router, "/admin/banner", emptyMap())
        assertTrue(html.contains("Banner"))
        assertTrue(html.contains("ff-custom"))
        FireflyPlugin.configureUsing(null)
    }

    @Test
    fun importAndExportRunOnTheQueue() {
        val dir = Files.createTempDirectory("ff-transfer")
        val file = dir.resolve("rows.csv")
        val rows = listOf(
            mapOf("name" to "Ada", "city" to "Ada, Jr", "note" to "say \"hi\""),
            mapOf("name" to "Bea"),
        )
        assertEquals("sync", FireflyTransfer.export(rows, file, null))
        val imported = FireflyTransfer.import(file, null)
        assertEquals("Ada, Jr", imported[0]["city"])
        assertEquals("say \"hi\"", imported[0]["note"])
        assertEquals("", imported[1]["city"])
        val bus = Bus(QueueManager(Config().also { it.set("queue.default", "sync") }))
        val queued = dir.resolve("queued.csv")
        val id = FireflyTransfer.export(rows, queued, bus)
        assertTrue(id.isNotEmpty())
        assertEquals("Ada", FireflyTransfer.import(queued, bus)[0]["name"])
        assertThrows<FireflyException> { FireflyTransfer.export(emptyList(), file, null) }
        assertThrows<FireflyException> { encodeCsv(emptyList()) }
        assertThrows<FireflyException> { FireflyTransfer.import(dir.resolve("missing.csv"), null) }
        Files.writeString(dir.resolve("empty.csv"), "")
        assertThrows<FireflyException> { decodeCsv("") }
        assertTrue(decodeCsv("name").isEmpty())
        assertEquals("", decodeCsv("name,city\nAda")[0]["city"])
        val rootName = "ff-export-" + System.nanoTime() + ".csv"
        ExportRows(listOf(mapOf("name" to "Ada")), rootName).handle()
        Files.deleteIfExists(java.nio.file.Path.of(rootName))
        val page = CustomPage("desk")
        page.headerActions(FireflyTransfer.exportAction(), FireflyTransfer.importAction())
        page.mountForm(Schema.make().components(SkuField(), TextInput.make("name")))
        page.table(Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name")))
        Firefly.test(page).assertSee("Ada").assertFormField("sku").assertTable("Ada").assertDialog("Export")
        Firefly.test("<p>Hi</p>").assertSee("Hi")
        assertThrows<AssertionError> { Firefly.test(page).assertSee("missing-token") }
        assertThrows<AssertionError> { Firefly.test(page).assertFormField("absent") }
        assertThrows<AssertionError> { Firefly.test("<p>Hi</p>").assertTable("Ada") }
        assertThrows<AssertionError> { Firefly.test("<p>Hi</p>").assertDialog("Export") }
        assertThrows<AssertionError> { Firefly.test(page).assertTable("missing-row") }
        assertThrows<AssertionError> { Firefly.test(page).assertDialog("Missing title") }
    }

    private fun send(router: Router, path: String, query: Map<String, String>): String {
        val match = router.find("GET", path) ?: throw AssertionError("missing $path")
        return match.route.action.handle(Request("GET", path, emptyMap(), query, "")).body
    }
}

private class BannerPlugin : FireflyPlugin() {
    override fun id(): String = "banner"

    override fun register(panel: Panel) {
        panel.hook(Hook.BODY_END, "<p>Banner</p>")
    }

    override fun boot(panel: Panel) {
        panel.page(CustomPage("banner"))
    }
}

class SkuField : org.konaframework.firefly.forms.TextInput("sku")

private fun database() {
    val url = "jdbc:h2:mem:ffkit${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = org.konaframework.cadence.Database().addConnection(
        "default",
        org.konaframework.cadence.ConnectionConfig(url, "sa", "", poolSize = 2),
    )
    database.connection().statement("create table ff_shelves (id bigint auto_increment primary key, name varchar(100), tenant_id varchar(40))")
    database.setAsGlobal()
}
