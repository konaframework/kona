package org.konaframework.firefly;

import org.junit.jupiter.api.Test;
import org.konaframework.firefly.forms.Select;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageFormJavaTest {
    @Test
    void javaPagesHandleTheirMountedFormAndOptions() {
        List<Map<String, String>> seen = new ArrayList<>();
        PanelPage settings = new PanelPage("settings")
            .mountForm(Schema.make().components(
                Select.make("status").options(Map.of("open", "Open")).searchable(),
                TextInput.make("title")))
            .onFormSubmit(input -> {
                seen.add(input);
                return "Saved from Java";
            });
        assertNotNull(settings.mountedForm());
        Router router = new Router();
        new Panel().path("admin").authenticate(false).page(settings).mount(router);

        Response posted = router.find("POST", "/admin/settings").getRoute().getAction()
            .handle(new Request("POST", "/admin/settings", Map.of(), Map.of(), "title=Java"));
        assertEquals(302, posted.getStatus());
        assertEquals(List.of(Map.of("title", "Java")), seen);

        Response options = router.find("GET", "/admin/settings/form-options").getRoute().getAction()
            .handle(new Request("GET", "/admin/settings/form-options", Map.of(), Map.of("field", "status", "q", "op"), ""));
        assertTrue(options.getBody().contains("Open"));
    }
}
