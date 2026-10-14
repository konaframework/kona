package dev.kona.crema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.http.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaCremaApiTest {
    @Test
    void renderAndRespondFromJava() {
        MapViewFinder finder = new MapViewFinder();
        finder.put("greeting", "<h1>{{ name }}</h1>");
        finder.put("payload", "@json($user)");
        CremaEngine engine = new CremaEngine(finder);
        assertEquals("<h1>Ada</h1>", engine.render("greeting", Map.of("name", "Ada")));
        View view = engine.make("greeting").with("name", "Grace");
        assertEquals("<h1>Grace</h1>", view.render());
        Response response = Crema.view(engine, "greeting", Map.of("name", "Lin"), 200);
        assertTrue(response.getBody().contains("Lin"));
        assertTrue(response.getHeaders().get("Content-Type").contains("html"));
        assertEquals("plain", engine.renderSource("plain"));
        assertEquals("<h1></h1>", Crema.view(engine, "greeting").getBody());
        assertEquals("{\"name\":\"Ada\"}", engine.render("payload", Map.of("user", Map.of("name", "Ada"))));
    }
}
