package hello

import bootstrap.App
import org.konaframework.core.Kona
import org.konaframework.http.HttpKernel
import org.konaframework.http.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AppSmokeTest {
    @AfterEach
    fun reset() = Kona.reset()

    private fun sampleBase(): Path {
        val sample = Path.of("samples/hello-kotlin").toAbsolutePath().normalize()
        return if (Files.isDirectory(Path.of("src/main/kotlin/bootstrap"))) {
            Path.of("").toAbsolutePath().normalize()
        } else {
            sample
        }
    }

    @Test
    fun welcomeAndHealth() {
        val base = sampleBase()
        val app = App.create(arrayOf("--base=$base"))
        val kernel = app.resolve(HttpKernel::class.java)
        val home = kernel.handle(Request.get("/"))
        val health = kernel.handle(Request.get("/api/health"))
        assertEquals(200, home.status)
        assertTrue(home.body.contains("<!DOCTYPE html>", ignoreCase = true), home.body)
        assertTrue(home.body.contains("Welcome", ignoreCase = true), home.body)
        assertEquals(200, health.status)
        assertEquals("{\"status\":\"ok\"}", health.body)
        val desk = kernel.handle(Request.get("/admin/desk"))
        val notes = kernel.handle(Request.get("/admin/notes/manage"))
        val tasks = kernel.handle(Request.get("/admin/tasks/manage"))
        val projects = kernel.handle(Request.get("/admin/projects/manage"))
        val orders = kernel.handle(Request.get("/admin/orders/edit"))
        assertEquals(200, desk.status)
        assertTrue(desk.body.contains("ff-combobox"), desk.body)
        assertTrue(desk.body.contains("ff-picker"), desk.body)
        assertTrue(desk.body.contains("ff-dialog"), desk.body)
        assertTrue(desk.body.contains("ff-desk-widget"), desk.body)
        assertTrue(notes.body.contains("ff-manage"), notes.body)
        assertTrue(notes.body.contains("ff-dialog"), notes.body)
        assertTrue(notes.body.contains("Ada"), notes.body)
        assertEquals(200, tasks.status)
        assertTrue(tasks.body.contains("ff-combobox"), tasks.body)
        assertTrue(tasks.body.contains("data-ff-combobox"), tasks.body)
        assertTrue(tasks.body.contains("""data-ff-relationship="project""""), tasks.body)
        assertTrue(tasks.body.contains("Desk"), tasks.body)
        assertTrue(tasks.body.contains("Lab"), tasks.body)
        assertTrue(tasks.body.contains("Wire combobox"), tasks.body)
        assertEquals(200, projects.status)
        assertTrue(projects.body.contains("Desk"), projects.body)
        // Toggle.default(true): a new project starts active (the create dialog), stored rows keep their value.
        val createDialog = projects.body.substringAfter("""data-ff-action="create"""").substringBefore("</form>")
        assertTrue(createDialog.contains("""aria-pressed="true""""), createDialog)
        assertTrue(orders.body.contains("ff-relation"), orders.body)
        assertTrue(orders.body.contains("/admin/orders/actions/rename"), orders.body)
        // app.policies.OrderPolicy forbids deleting orders: no bulk delete, so no row checkboxes.
        val orderList = kernel.handle(Request.get("/admin/orders/list"))
        assertEquals(200, orderList.status)
        assertFalse(orderList.body.contains("data-ff-selectable data-ff-total"), orderList.body)
        // Navigation groups: Work (Projects, Tasks, Notes by sort) then Sales (collapsible, Orders badge).
        val nav = orderList.body.substringAfter("<nav class=\"ff-nav ff-nav-side\">")
        assertTrue(nav.contains("data-ff-nav-group=\"Work\""), nav)
        assertTrue(nav.contains("<details class=\"ff-nav-group ff-nav-group-collapsible\" data-ff-nav-group=\"Sales\" open>"), nav)
        assertTrue(nav.indexOf("Projects") < nav.indexOf("Tasks") && nav.indexOf("Tasks") < nav.indexOf("Notes"), nav)
        assertTrue(nav.indexOf("data-ff-nav-group=\"Work\"") < nav.indexOf("data-ff-nav-group=\"Sales\""), nav)
        assertTrue(nav.contains("class=\"ff-nav-badge\" data-ff-color=\"info\""), nav)
    }

    @Test
    fun sampleTreeUsesSrcMainLayout() {
        val base = sampleBase()
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/bootstrap/Middleware.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/routes/ApiRoutes.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/config/Paths.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/config/Session.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/config/Cache.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/config/Auth.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/kotlin/config/Redis.kt")))
        assertTrue(Files.isRegularFile(base.resolve("src/main/resources/views/welcome.crema.html")))
        assertTrue(Files.isDirectory(base.resolve("storage")))
    }
}
