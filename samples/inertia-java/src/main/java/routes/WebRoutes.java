package routes;

import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;
import org.konaframework.inertia.Inertia;
import java.util.List;
import java.util.Map;

public final class WebRoutes {
    private WebRoutes() {}

    public static void register(Router router) {
        router.get("/", (Request request) ->
            Inertia.render("Pages/Welcome", Map.of(
                "message", "Welcome to Kona Inertia",
                "stats", Inertia.optional(() -> Map.of("visits", 1)),
                "sidebar", Inertia.defer(() -> List.of("Getting started", "Protocol"), "aside"),
                "events", Inertia.merge(List.of(Map.of("id", 1, "title", "Launch")))
            ))
        );
        router.get("/about", (Request request) ->
            Inertia.render("Pages/About", Map.of("title", "About"))
        );
        router.put("/profile", (Request request) ->
            Response.redirect("/about").with("success", "Profile updated")
        );
        router.get("/leave", (Request request) -> Inertia.location("https://example.com"));
    }
}
