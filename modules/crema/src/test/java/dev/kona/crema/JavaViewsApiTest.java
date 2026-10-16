package dev.kona.crema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.http.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JavaViewsApiTest {
    @AfterEach
    void tearDown() {
        Views.clearEngine();
    }

    @Test
    void makeShareFirstAndResponseFromJava() {
        MapViewFinder finder = new MapViewFinder();
        finder.put("welcome", "<p>{{ app }} {{ name }}</p>");
        finder.put("posts/show", "Post");
        CremaEngine engine = new CremaEngine(finder);
        Views.setEngine(engine);

        Views.share("app", "Kona");
        View view = Views.make("welcome").with("name", "Ada");
        assertEquals("<p>Kona Ada</p>", view.render());
        assertEquals(Map.of("name", "Ada"), view.data());

        assertTrue(Views.exists("posts.show"));
        assertTrue(Views.exists("posts/show"));
        assertEquals("Post", Views.first(List.of("missing", "posts/show")).render());
        // Hit @JvmOverloads single-arg bridges (Kotlin default-args call the full signature).
        assertEquals("Post", engine.first(List.of("posts.show")).render());
        assertEquals("<p>Kona Ada</p>", Views.make("welcome").with("name", "Ada").render());
        assertEquals("<p>Kona </p>", Views.render("welcome"));
        assertEquals("<p>Kona </p>", Views.response("welcome").getBody());
        assertEquals("Post", Views.first(List.of("posts.show")).render());

        Response response = Views.response("welcome", Map.of("name", "Lin"), 201);
        assertEquals(201, response.getStatus());
        assertTrue(response.getBody().contains("Lin"));

        Response bound = Crema.view("welcome", Map.of("name", "Zed"), 200);
        assertTrue(bound.getBody().contains("Zed"));

        Views.flushShared();
        assertTrue(Views.shared().isEmpty());
        assertFalse(Views.exists("nope"));
        assertThrows(ViewNotFoundException.class, () -> Views.first(List.of("nope")));
    }
}
