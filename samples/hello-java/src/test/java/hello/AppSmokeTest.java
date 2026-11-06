package hello;

import bootstrap.App;
import org.konaframework.core.Application;
import org.konaframework.core.Kona;
import org.konaframework.firefly.ListPage;
import org.konaframework.firefly.NavigationGroup;
import org.konaframework.firefly.NavigationType;
import org.konaframework.firefly.Panel;
import org.konaframework.http.HttpKernel;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;
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
    void topOmitsSidebarColumnAndInverseFlipsApp() {
        Router topRouter = new Router();
        new Panel().path("top").topNavigation().authenticate(false).page(new ListPage("one")).mount(topRouter);
        String top = topRouter.find("GET", "/top/one").getRoute().getAction().handle(Request.get("/top/one")).getBody();
        assertTrue(top.contains("class=\"ff-nav ff-nav-top\""), top);
        assertFalse(top.contains("class=\"ff-nav ff-nav-side\""), top);
        assertTrue(top.contains("ff-sidebar-offcanvas"), top);
        assertTrue(top.contains("ff-nav-drawer"), top);
        assertTrue(top.contains(".ff-panel > .ff-sidebar:has(> .ff-sidebar-offcanvas)"), top);

        Router inverse = new Router();
        new Panel().path("inv").authenticate(false).navigation(NavigationType.INVERSE)
            .navigationGroups(NavigationGroup.make("Sales"))
            .page(new ListPage("orders").navigationGroup("Sales").navigationLabel("Orders"))
            .page(new ListPage("quotes").navigationGroup("Sales").navigationLabel("Quotes"))
            .page(new ListPage("loose").navigationLabel("Loose"))
            .mount(inverse);
        String orders = inverse.find("GET", "/inv/orders").getRoute().getAction().handle(Request.get("/inv/orders")).getBody();
        String topNav = orders.substring(orders.indexOf("<nav class=\"ff-nav ff-nav-top\">"));
        topNav = topNav.substring(0, topNav.indexOf("</nav>"));
        String sideNav = orders.substring(orders.indexOf("<nav class=\"ff-nav ff-nav-side\">"));
        sideNav = sideNav.substring(0, sideNav.indexOf("</nav>"));
        assertTrue(orders.contains("<aside class=\"ff-sidebar\""), orders);
        assertTrue(topNav.contains(">Sales<"), topNav);
        assertFalse(topNav.contains(">Orders<"), topNav);
        assertTrue(sideNav.contains(">Orders<"), sideNav);
        assertTrue(sideNav.contains(">Quotes<"), sideNav);
        assertFalse(sideNav.contains(">Loose<"), sideNav);
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
