package dev.kona.inertia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.http.Router;
import dev.kona.inertia.assets.Vite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Java-callable Inertia + Vite API (Law 8). */
public class JavaInertiaApiTest {
    @TempDir
    Path temp;

    @AfterEach
    void reset() {
        Inertia.reset();
        Vite.reset();
    }

    @Test
    void renderShareAndViteFromJava() throws Exception {
        Path publicDir = temp.resolve("public/build");
        Files.createDirectories(publicDir);
        Files.writeString(
            publicDir.resolve("manifest.json"),
            "{\"resources/js/app.js\":{\"file\":\"assets/app-xyz.js\",\"isEntry\":true}}"
        );

        Application app = new Application(temp);
        app.getConfig().set("inertia.public_path", temp.resolve("public").toString());
        app.getConfig().set("inertia.version", "java-v");
        app.register(new HttpServiceProvider());
        app.register(new InertiaServiceProvider());
        app.boot();

        app.resolve(HttpKernel.class).pushMiddleware(new HandleInertiaRequests() {
            @Override
            public Map<String, Object> share(Request request) {
                return Map.of("fromJava", true);
            }
        });
        app.resolve(Router.class).get("/java", (Request request) ->
            Inertia.render(request, "Pages/Java", Map.of("hello", "world"))
        );

        Response first = app.resolve(HttpKernel.class).handle(Request.get("/java"));
        assertEquals(200, first.getStatus());
        assertTrue(first.getBody().contains("data-page="));
        assertTrue(first.getBody().contains("Pages/Java"));

        Response json = app.resolve(HttpKernel.class).handle(
            new Request("GET", "/java", Map.of("X-Inertia", "true", "X-Inertia-Version", "java-v"), Map.of(), "")
        );
        assertEquals("true", json.getHeaders().get("X-Inertia"));
        assertTrue(json.getBody().contains("\"hello\":\"world\""));
        assertTrue(json.getBody().contains("\"fromJava\":true"));

        Inertia.share("global", "yes");
        Inertia.version("java-v2");
        assertEquals("/build/assets/app-xyz.js", Vite.asset("resources/js/app.js"));
        assertTrue(Vite.tags("resources/js/app.js").contains("app-xyz.js"));

        InertiaManager manager = Inertia.manager();
        manager.setRequest(Request.get("/java"));
        Response oneArg = Inertia.render("Pages/One");
        assertTrue(oneArg.getBody().contains("Pages/One"));
        Response twoArg = Inertia.render(Request.get("/two"), "Pages/Two");
        assertTrue(twoArg.getBody().contains("Pages/Two"));
        assertTrue(manager.render("Pages/Mgr").getBody().contains("Pages/Mgr"));
        manager.setRequest(null);
    }
}
