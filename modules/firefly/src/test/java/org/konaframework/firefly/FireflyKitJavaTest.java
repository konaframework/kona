package org.konaframework.firefly;

import org.junit.jupiter.api.Test;
import org.konaframework.firefly.testing.Firefly;
import org.konaframework.http.Request;
import org.konaframework.http.Router;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireflyKitJavaTest {
    @Test
    void javaCallsTenancyPluginsAndAssertions() {
        Panel panel = new Panel().path("admin").authenticate(false).tenant(new Tenant("acme").label("Acme"));
        FireflyPlugin.configureUsing(plugin -> plugin.view("plugins/java.crema.html"));
        panel.plugin(new FireflyPlugin());
        FireflyPlugin.configureUsing(null);
        panel.page(new CustomPage("desk"));
        Router router = new Router();
        panel.mount(router);
        var match = router.find("GET", "/admin/desk");
        String html = match.getRoute().getAction().handle(
            new Request("GET", "/admin/desk", Map.of(), Map.of("tenant", "acme"), "")
        ).getBody();
        assertTrue(html.contains("ff-tenant ff-active"));
        assertEquals("acme", FireflyTenant.use("acme", (Supplier<String>) () -> FireflyTenant.id()));
        assertTrue(Firefly.test("<p>Java</p>").assertSee("Java") != null);
    }
}
