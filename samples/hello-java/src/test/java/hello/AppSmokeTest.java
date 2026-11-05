package hello;

import bootstrap.App;
import org.konaframework.core.Application;
import org.konaframework.core.Kona;
import org.konaframework.http.HttpKernel;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSmokeTest {
    @AfterEach
    void reset() {
        Kona.reset();
    }

    private Path sampleBase() {
        Path sample = Path.of("samples/hello-java").toAbsolutePath().normalize();
        return Files.isDirectory(Path.of("src/main/java/bootstrap"))
            ? Path.of("").toAbsolutePath().normalize()
            : sample;
    }

    @Test
    void welcomeAndHealth() {
        Path base = sampleBase();
        Application app = App.create(new String[]{"--base=" + base});
        HttpKernel kernel = app.resolve(HttpKernel.class);
        Response home = kernel.handle(Request.get("/"));
        Response health = kernel.handle(Request.get("/api/health"));
        assertEquals(200, home.getStatus());
        assertTrue(home.getBody().toLowerCase().contains("<!doctype html>"), home.getBody());
        assertTrue(home.getBody().toLowerCase().contains("welcome"), home.getBody());
        assertEquals(200, health.getStatus());
        assertEquals("{\"status\":\"ok\"}", health.getBody());
        Response desk = kernel.handle(Request.get("/admin/desk"));
        Response notes = kernel.handle(Request.get("/admin/notes/manage"));
        Response tasks = kernel.handle(Request.get("/admin/tasks/manage"));
        Response projects = kernel.handle(Request.get("/admin/projects/manage"));
        Response orders = kernel.handle(Request.get("/admin/orders/edit"));
        assertEquals(200, desk.getStatus());
        assertTrue(desk.getBody().contains("ff-combobox"), desk.getBody());
        assertTrue(desk.getBody().contains("ff-picker"), desk.getBody());
        assertTrue(desk.getBody().contains("ff-dialog"), desk.getBody());
        assertTrue(desk.getBody().contains("ff-desk-widget"), desk.getBody());
        assertTrue(notes.getBody().contains("ff-manage"), notes.getBody());
        assertTrue(notes.getBody().contains("Ada"), notes.getBody());
        assertEquals(200, tasks.getStatus());
        assertTrue(tasks.getBody().contains("ff-combobox"), tasks.getBody());
        assertTrue(tasks.getBody().contains("data-ff-combobox"), tasks.getBody());
        assertTrue(tasks.getBody().contains("data-ff-relationship=\"project\""), tasks.getBody());
        assertTrue(tasks.getBody().contains("Desk"), tasks.getBody());
        assertTrue(tasks.getBody().contains("Lab"), tasks.getBody());
        assertTrue(tasks.getBody().contains("Wire combobox"), tasks.getBody());
        assertEquals(200, projects.getStatus());
        assertTrue(projects.getBody().contains("Desk"), projects.getBody());
        // Toggle.defaultOnCreate(true): a new project starts active (the create dialog).
        String createDialog = projects.getBody().split("data-ff-action=\"create\"", 2)[1].split("</form>", 2)[0];
        assertTrue(createDialog.contains("aria-pressed=\"true\""), createDialog);
        assertTrue(orders.getBody().contains("ff-relation"), orders.getBody());
        assertTrue(orders.getBody().contains("/admin/orders/actions/rename"), orders.getBody());
        // app.policies.OrderPolicy forbids deleting orders: no bulk delete, so no row checkboxes.
        Response orderList = kernel.handle(Request.get("/admin/orders/list"));
        assertEquals(200, orderList.getStatus());
        assertFalse(orderList.getBody().contains("data-ff-selectable data-ff-total"), orderList.getBody());
        // APP chrome: groups are L1 sidebar clusters; the active group's links sit in the top bar.
        String side = orderList.getBody().substring(orderList.getBody().indexOf("<nav class=\"ff-nav ff-nav-side\">"));
        side = side.substring(0, side.indexOf("</nav>"));
        assertFalse(side.contains("data-ff-nav-group="), side);
        assertTrue(side.contains(">Dashboard<"), side);
        assertTrue(side.contains(">Sales<"), side);
        assertTrue(side.contains(">Work<"), side);
        assertTrue(side.indexOf(">Dashboard<") < side.indexOf(">Sales<") && side.indexOf(">Sales<") < side.indexOf(">Work<"), side);
        assertTrue(side.contains("ff-nav-item ff-cluster ff-active"), side);
        assertTrue(side.contains("href=\"/admin/orders/list\""), side);
        String top = orderList.getBody().substring(orderList.getBody().indexOf("<nav class=\"ff-nav ff-nav-top\">"));
        top = top.substring(0, top.indexOf("</nav>"));
        assertTrue(top.contains(">Orders<"), top);
        assertTrue(top.contains("class=\"ff-nav-badge\" data-ff-color=\"info\""), top);
        assertFalse(top.contains(">Projects<"), top);
        String workTop = projects.getBody().substring(projects.getBody().indexOf("<nav class=\"ff-nav ff-nav-top\">"));
        workTop = workTop.substring(0, workTop.indexOf("</nav>"));
        assertTrue(workTop.indexOf(">Projects<") < workTop.indexOf(">Tasks<") && workTop.indexOf(">Tasks<") < workTop.indexOf(">Notes<"), workTop);
    }

    @Test
    void sampleTreeUsesSrcMainLayout() {
        Path base = sampleBase();
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/bootstrap/Middleware.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/routes/ApiRoutes.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/config/Paths.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/config/Session.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/config/Cache.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/config/Auth.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/config/Redis.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/resources/views/welcome.crema.html")));
        assertTrue(Files.isDirectory(base.resolve("storage")));
    }
}
