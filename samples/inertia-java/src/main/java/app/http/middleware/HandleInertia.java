package app.http.middleware;

import org.konaframework.http.Request;
import org.konaframework.inertia.HandleInertiaRequests;
import java.util.Map;

public class HandleInertia extends HandleInertiaRequests {
    @Override
    public Map<String, Object> share(Request request) {
        return Map.of("appName", "Inertia sample (Java)");
    }

    @Override
    public String version(Request request) {
        return "1";
    }
}
