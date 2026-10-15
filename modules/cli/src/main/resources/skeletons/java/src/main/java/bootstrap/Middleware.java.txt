package bootstrap;

import dev.kona.core.Application;
import dev.kona.http.HttpMiddleware;

/**
 * HTTP middleware registration (Almasix / Laravel bootstrap shape).
 */
public final class Middleware {
    public static void configure(Application app) {
        new HttpMiddleware()
            // Behind a load balancer / ingress:
            // .trustProxies(java.util.List.of("*"))
            // .trustHosts(java.util.List.of("example.com", "*.example.com"))
            .apply(app);
    }

    private Middleware() {
    }
}
