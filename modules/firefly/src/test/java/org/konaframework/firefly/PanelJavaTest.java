package org.konaframework.firefly;

import org.junit.jupiter.api.Test;
import org.konaframework.firefly.support.FireflyException;
import org.konaframework.firefly.support.MaxWidth;
import org.konaframework.firefly.widgets.Page;
import org.konaframework.firefly.widgets.Widget;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertTrue(orders.getBody().contains("data-ff-panel-spa"));
        assertTrue(orders.getBody().contains("X-Brew-Navigate"));
        assertTrue(orders.getBody().contains("wire:navigate"));
        int spaStart = orders.getBody().indexOf("<script data-ff-panel-spa>");
        int spaEnd = orders.getBody().indexOf("</script>", spaStart);
        assertTrue(spaStart >= 0 && spaEnd > spaStart);
        String spaScript = orders.getBody().substring(spaStart, spaEnd);
        assertFalse(spaScript.contains("location.assign"));
        assertFalse(spaScript.contains("location.href="));
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
    void javaSpaFalseKeepsFullDocumentLinks() {
        Router router = new Router();
        Panel panel = new Panel();
        panel.path("desk").authenticate(false).spa(false);
        panel.page(new ListPage("orders"));
        panel.mount(router, Map.of());
        String html = router.find("GET", "/desk/orders").getRoute().getAction().handle(new Request("GET", "/desk/orders")).getBody();
        assertFalse(html.contains("data-ff-panel-spa"));
        assertFalse(html.contains(" wire:navigate"));
        assertTrue(html.contains("<a href=\"/desk/orders\">"));
        assertTrue(html.contains("window.location.assign"));
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

    @Test
    void javaSetsMaxContentWidthClasses() {
        assertEquals("max-w-[900px]", MaxWidth.utilityClass("[900px]"));
        assertEquals("container", MaxWidth.utilityClass("container"));
        assertEquals("container", MaxWidth.utilityClass("Container"));
        assertEquals("max-w-screen-sm", MaxWidth.utilityClass("screen-s"));
        assertEquals("max-w-screen-md", MaxWidth.utilityClass("screen-m"));
        assertEquals("max-w-screen-lg", MaxWidth.utilityClass("screen-l"));
        assertEquals("max-w-screen-xl", MaxWidth.utilityClass("screen-xl"));
        assertEquals("max-w-screen-xl", MaxWidth.utilityClass(MaxWidth.ScreenExtraLarge.getToken()));
        assertEquals("max-w-screen-2xl", MaxWidth.utilityClass("screen-2xl"));
        assertEquals(null, MaxWidth.utilityClass("prose"));
        assertEquals(null, MaxWidth.utilityClass(MaxWidth.Prose.getToken()));
        assertEquals(null, MaxWidth.utilityClass("full"));
        assertEquals("full", MaxWidth.requireKnown("full"));
        assertThrows(FireflyException.class, () -> MaxWidth.requireKnown("huge"));
        assertThrows(FireflyException.class, () -> new Panel().maxContentWidth("[900px] extra"));

        Router custom = new Router();
        Panel customPanel = new Panel();
        customPanel.path("wide").authenticate(false).maxContentWidth("[900px]").page(new ListPage("one"));
        customPanel.mount(custom);
        String customHtml = custom.find("GET", "/wide/one").getRoute().getAction().handle(new Request("GET", "/wide/one")).getBody();
        assertTrue(customHtml.contains("class=\"ff-panel max-w-[900px]\""));
        assertTrue(customHtml.contains("style=\"--ff-arbitrary-max: 900px\""));

        Router box = new Router();
        Panel boxPanel = new Panel();
        boxPanel.path("box").authenticate(false).maxContentWidth("container").page(new ListPage("one"));
        boxPanel.mount(box);
        String boxHtml = box.find("GET", "/box/one").getRoute().getAction().handle(new Request("GET", "/box/one")).getBody();
        String boxTag = boxHtml.substring(boxHtml.indexOf("<div class=\""), boxHtml.indexOf("\"", boxHtml.indexOf("<div class=\"") + 12));
        assertTrue(boxTag.contains("ff-panel container"));
        assertFalse(boxTag.contains("max-w-"));

        Router typed = new Router();
        Panel typedPanel = new Panel();
        typedPanel.path("typed").authenticate(false).maxContentWidth(MaxWidth.ScreenExtraLarge).page(new ListPage("one"));
        typedPanel.mount(typed);
        String typedHtml = typed.find("GET", "/typed/one").getRoute().getAction().handle(new Request("GET", "/typed/one")).getBody();
        assertTrue(typedHtml.contains("class=\"ff-panel max-w-screen-xl\""));

        String page = new Page().maxWidth(MaxWidth.ScreenLarge).render();
        assertTrue(page.contains("class=\"ff-page max-w-screen-lg\""));
        assertFalse(page.contains("data-ff-max-width"));
        assertTrue(new Page().maxContentWidth("screen-xl").render().contains("max-w-screen-xl"));
        assertTrue(new Page().maxContentWidth(MaxWidth.Container).render().contains("class=\"ff-page container\""));
        String length = new Page().maxWidth("[48rem]").render();
        assertTrue(length.contains("max-w-[48rem]"));
        assertTrue(new Page().maxWidth("xl").render().contains("data-ff-max-width=\"xl\""));

        Router desk = new Router();
        Panel deskPanel = new Panel();
        deskPanel.path("desk").authenticate(false).maxContentWidth("screen-xl").page(new DashboardPage());
        deskPanel.mount(desk);
        String deskHtml = desk.find("GET", "/desk").getRoute().getAction().handle(new Request("GET", "/desk")).getBody();
        assertTrue(deskHtml.contains("class=\"ff-panel max-w-screen-xl\""));
        String deskSection = openTag(deskHtml, "ff-page ff-dashboard");
        assertFalse(deskSection.contains("data-ff-max-width"));
        assertFalse(deskSection.contains("max-w-"));

        Router own = new Router();
        Panel ownPanel = new Panel();
        ownPanel.path("own").authenticate(false).maxContentWidth(MaxWidth.ScreenExtraLarge)
            .page(new DashboardPage().maxContentWidth("screen-s"));
        ownPanel.mount(own);
        String ownHtml = own.find("GET", "/own").getRoute().getAction().handle(new Request("GET", "/own")).getBody();
        assertTrue(openTag(ownHtml, "ff-page ff-dashboard").contains("max-w-screen-sm"));

        Router full = new Router();
        Panel fullPanel = new Panel();
        fullPanel.path("full").authenticate(false).maxContentWidth("full").page(new DashboardPage());
        fullPanel.mount(full);
        String fullHtml = full.find("GET", "/full").getRoute().getAction().handle(new Request("GET", "/full")).getBody();
        assertTrue(fullHtml.contains("data-ff-max-width=\"full\""));
        assertFalse(openTag(fullHtml, "ff-page ff-dashboard").contains("data-ff-max-width"));
    }

    private static String openTag(String html, String marker) {
        int idx = html.indexOf(marker);
        int start = html.lastIndexOf('<', idx);
        return html.substring(start, html.indexOf('>', start));
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
