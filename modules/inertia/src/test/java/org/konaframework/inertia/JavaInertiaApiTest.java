package org.konaframework.inertia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.core.Application;
import org.konaframework.http.HttpKernel;
import org.konaframework.http.HttpServiceProvider;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;
import org.konaframework.inertia.assets.Vite;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.konaframework.crema.CremaEngine;
import org.konaframework.crema.MapViewFinder;
import org.konaframework.crema.assets.AssetManifest;
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

    @Test
    void spaDevAndProdScriptUrls() throws Exception {
        Path publicDir = temp.resolve("public");
        Files.createDirectories(publicDir);
        String entry = "src/main/resources/js/app.js";
        Files.writeString(publicDir.resolve("hot"), "http://127.0.0.1:5173\n");
        Vite.setManifest(new AssetManifest(publicDir, new ObjectMapper()));
        String dev = Vite.tags(entry);
        assertTrue(dev.contains("http://127.0.0.1:5173/@vite/client"));
        assertTrue(dev.contains("http://127.0.0.1:5173/" + entry));
        assertFalse(dev.contains("/build/" + entry));

        Files.delete(publicDir.resolve("hot"));
        Files.createDirectories(publicDir.resolve("build/.vite"));
        Files.writeString(
            publicDir.resolve("build/.vite/manifest.json"),
            "{\"" + entry + "\":{\"file\":\"assets/app-Ab12.js\",\"css\":[\"assets/app-Cd34.css\"],\"isEntry\":true}}"
        );
        String prod = Vite.tags(entry);
        assertTrue(prod.contains("/build/assets/app-Ab12.js"));
        assertTrue(prod.contains("/build/assets/app-Cd34.css"));
        assertFalse(prod.contains("/build/" + entry));
        assertEquals("/build/assets/app-Ab12.js", Vite.asset(entry));

        MapViewFinder finder = new MapViewFinder();
        finder.put("app", "<head>{!! vite !!}</head>");
        InertiaManager manager = new InertiaManager();
        manager.setRootView("app");
        manager.setCremaEngine(new CremaEngine(finder));
        manager.setViteEntries(java.util.List.of(entry));
        Inertia.setManager(manager);
        String html = manager.render(Request.get("/"), "Pages/Welcome").getBody();
        assertTrue(html.contains("/build/assets/app-Ab12.js"));
        assertFalse(html.contains("/build/" + entry));

        Path asset = publicDir.resolve("build/assets/app-Ab12.js");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "console.log('spa')");
        Application app = new Application(temp);
        app.getConfig().set("inertia.public_path", publicDir.toString());
        app.register(new HttpServiceProvider());
        app.register(new InertiaServiceProvider());
        app.boot();
        Response served = app.resolve(HttpKernel.class).handle(Request.get("/build/assets/app-Ab12.js"));
        assertEquals(200, served.getStatus());
        assertEquals("console.log('spa')", new String(served.bodyBytes(), StandardCharsets.UTF_8));
        assertEquals(404, app.resolve(HttpKernel.class).handle(Request.get("/build/" + entry)).getStatus());
    }
}
