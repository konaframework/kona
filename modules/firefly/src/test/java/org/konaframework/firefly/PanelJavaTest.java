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
}
