package routes;

import org.konaframework.http.Response;
import org.konaframework.http.Router;
import java.util.Map;

public class ApiRoutes {
    public void register(Router router) {
        router.prefix("/api").name("api.").group(api -> {
            api.get("/health", request -> Response.json(Map.of("status", "ok"))).name("health");
        });
    }
}
