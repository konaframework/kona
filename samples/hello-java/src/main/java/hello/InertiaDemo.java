package hello;

import dev.kona.core.Kona;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.http.Router;
import dev.kona.inertia.HandleInertiaRequests;
import dev.kona.inertia.Inertia;
import dev.kona.inertia.InertiaServiceProvider;
import dev.kona.inertia.assets.Vite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class InertiaDemo {
    public static List<String> run(String[] args, Path publicDir) throws Exception {
        Files.createDirectories(publicDir.resolve("build"));
        Files.writeString(
            publicDir.resolve("build/manifest.json"),
            "{\"resources/js/app.js\":{\"file\":\"assets/app.js\",\"isEntry\":true}}"
        );
        try {
            var app = Kona.create(args)
                .configure(cfg -> {
                    cfg.set("inertia.version", "demo");
                    cfg.set("inertia.public_path", publicDir.toString());
                })
                .providers(new HttpServiceProvider(), new InertiaServiceProvider())
                .boot();
            app.resolve(HttpKernel.class).pushMiddleware(new HandleInertiaRequests() {
                @Override
                public Map<String, Object> share(Request request) {
                    return Map.of("shared", true);
                }
            });
            app.resolve(Router.class).get("/", (Request request) ->
                Inertia.render("Pages/Demo", Map.of("hello", "kona"))
            );
            HttpKernel kernel = app.resolve(HttpKernel.class);
            Response html = kernel.handle(Request.get("/"));
            Response json = kernel.handle(
                new Request("GET", "/", Map.of("X-Inertia", "true", "X-Inertia-Version", "demo"), Map.of(), "")
            );
            Response conflict = kernel.handle(
                new Request("GET", "/", Map.of("X-Inertia", "true", "X-Inertia-Version", "old"), Map.of(), "")
            );
            String tags = Vite.tags("resources/js/app.js");
            return List.of(
                "html=" + html.getBody().contains("data-page="),
                "json=" + "true".equals(json.getHeaders().get("X-Inertia")),
                "conflict=" + conflict.getStatus(),
                "vite=" + tags.contains("app.js")
            );
        } finally {
            Inertia.reset();
            Vite.reset();
        }
    }

    private InertiaDemo() {
    }
}
