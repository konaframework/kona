package routes;

import dev.kona.http.Response;
import dev.kona.http.Router;
import java.util.Map;

public class ApiRoutes {
    public void register(Router router) {
        router.prefix("/api").name("api.").group(api -> {
            api.get("/health", request -> Response.json(Map.of("status", "ok"))).name("health");
        });
    }
}
