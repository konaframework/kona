package org.konaframework.firefly;

import org.junit.jupiter.api.Test;
import org.konaframework.firefly.widgets.Widget;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PanelJavaTest {
    @Test
    void javaMountsAPanel() throws Exception {
        Router router = new Router();
        router.get("/welcome", request -> Response.html("java-app")).name("app.welcome");
        Panel panel = new Panel();
        panel.id(get -> "java");
        panel.path("");
        panel.brand(get -> "Java");
        panel.navigation(NavigationType.SIDE);
        panel.authenticate(false);
        panel.spa(true);
        panel.maxContentWidth("full");
        panel.page(new ListPage("orders"));
        panel.userMenu(new NavigationItem("Account").label("Account").url("/profile"));
        panel.mount(router, Map.of());
        assertEquals("firefly-java", panel.guardName());
        Response welcome = router.find("GET", "/welcome").getRoute().getAction().handle(new Request("GET", "/welcome"));
        assertEquals("java-app", welcome.getBody());
        Response orders = router.find("GET", "/orders").getRoute().getAction().handle(new Request("GET", "/orders"));
        assertTrue(orders.getBody().contains("ff-list"));
        assertTrue(orders.getBody().contains("data-ff-max-width=\"full\""));
        assertTrue(panel.render(new Request("GET", "/orders"), Map.of("brand", "Java")).contains("Java"));
        assertTrue(panel.render(new Request("GET", "/orders")).contains("ff-panel"));
        assertTrue(panel.render(new Request("GET", "/orders")).contains("data-ff-max-width=\"full\""));
        var dir = Files.createTempDirectory("firefly-java");
        assertEquals(0, PanelDiscovery.pages(dir).size());
        assertEquals(0, PanelDiscovery.pages(dir, Thread.currentThread().getContextClassLoader()).size());
        assertEquals(0, PanelDiscovery.resources(dir).size());
        assertEquals(0, PanelDiscovery.resources(dir, Thread.currentThread().getContextClassLoader()).size());
        assertEquals(0, PanelDiscovery.widgets(dir).size());
        Widget.make("java");
    }

    @Test
    void javaDeclaresNavigationGroups() {
        Resource orders = new Resource("orders") {
            @Override
            public Map<String, org.konaframework.firefly.widgets.Page> getPages() {
                return Map.of("list", new ListPage("list"));
            }
        };
        orders.navigationGroup("Sales").navigationSort(1).navigationLabel("Sales orders")
            .navigationBadge("danger", () -> 3).shouldRegisterNavigation(true);
        PanelPage reports = new PanelPage("reports");
        reports.navigationGroup("Accounting").navigationSort(2).navigationBadge(() -> "new")
            .navigationBadgeColor("success");
        Router router = new Router();
        Panel panel = new Panel();
        panel.path("admin").authenticate(false).navigation(NavigationType.SIDE);
        panel.navigationGroups(
            NavigationGroup.make("Sales").icon("shopping-bag").collapsible(),
            NavigationGroup.make("Accounting")
        );
        panel.resource(orders);
        panel.page(reports);
        panel.mount(router, Map.of());
        String html = panel.render(new Request("GET", "/admin"));
        assertTrue(html.contains("data-ff-nav-group=\"Sales\""));
        assertTrue(html.contains("data-ff-nav-group=\"Accounting\""));
        assertTrue(html.contains("<span class=\"ff-nav-badge\" data-ff-color=\"danger\">3</span>"));
        assertTrue(html.contains("<span class=\"ff-nav-badge\" data-ff-color=\"success\">new</span>"));
        assertTrue(html.contains("Sales orders"));
        assertEquals("Sales", orders.navigationGroup());
        assertEquals(Integer.valueOf(1), orders.navigationSort());
        assertEquals("3", orders.navigationBadge().getText());
        NavigationItem item = new NavigationItem("calc").group("Tools").sort(1).badge(new NavigationBadge("9"));
        assertEquals("Tools", item.groupName());
    }

    @Test
    void javaConfiguresThroughPanelHook() {
        Router router = new Router();
        DeskPanel panel = new DeskPanel();
        panel.mount(router);
        assertEquals("firefly-desk", panel.guardName());
        Response orders = router.find("GET", "/staff/orders").getRoute().getAction().handle(new Request("GET", "/staff/orders"));
        assertTrue(orders.getBody().contains("Desk"));
        assertTrue(orders.getBody().contains("ff-list"));
        panel.render(new Request("GET", "/staff/orders"));
        assertEquals(1, DeskPanel.calls);
    }

    public static class DeskPanel extends Panel {
        static int calls;

        @Override
        public Panel panel(Panel panel) {
            calls += 1;
            return panel.id("desk").path("staff").brand("Desk").authenticate(false).page(new ListPage("orders"));
        }
    }
}
