package routes;

import org.konaframework.http.Request;
import org.konaframework.http.Router;
import org.konaframework.inertia.Inertia;
import java.util.Map;

public final class WebRoutes {
    private WebRoutes() {}

    public static void register(Router router) {
        router.get("/", (Request request) ->
            Inertia.render("Pages/Welcome", Map.of("message", "Welcome to Kona Inertia"))
        );
        router.get("/about", (Request request) ->
            Inertia.render("Pages/About", Map.of("title", "About"))
        );
        router.get("/leave", (Request request) -> Inertia.location("https://example.com"));
    }
}
