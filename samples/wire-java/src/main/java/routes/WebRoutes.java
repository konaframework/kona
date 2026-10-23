package routes;

import org.konaframework.core.Kona;
import org.konaframework.crema.CremaEngine;
import org.konaframework.http.Response;
import org.konaframework.http.Router;
import java.util.Map;

public final class WebRoutes {
    private WebRoutes() {}

    public static void register(Router router) {
        router.get("/", request -> {
            CremaEngine crema = Kona.app().resolve(CremaEngine.class);
            return Response.html(crema.render("welcome", Map.of("title", "Wire sample (Java)")));
        }).name("welcome");
    }
}
