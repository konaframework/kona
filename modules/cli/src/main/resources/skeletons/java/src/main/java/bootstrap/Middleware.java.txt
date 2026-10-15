package bootstrap;

import dev.kona.core.Application;
import dev.kona.filesystem.FilesystemManager;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpMiddleware;
import dev.kona.http.ServePublicStorage;
import dev.kona.session.SessionManager;
import dev.kona.session.StartSession;
import dev.kona.session.VerifyCsrfToken;
import java.util.List;
import java.util.function.Supplier;

/**
 * HTTP middleware registration (Almasix / Laravel bootstrap shape).
 *
 * Web stack: session + CSRF (API routes under /api are CSRF-excepted).
 * Public disk files are served under the /storage URL prefix before the web stack.
 */
public final class Middleware {
    public static void configure(Application app) {
        SessionManager sessions = app.resolve(SessionManager.class);
        FilesystemManager files = app.resolve(FilesystemManager.class);
        app.resolve(HttpKernel.class)
            .prependMiddleware(new ServePublicStorage(files.disk("public")));
        new HttpMiddleware()
            .alias("start.session", (Supplier<dev.kona.http.Middleware>) () -> new StartSession(sessions))
            .alias("csrf", (Supplier<dev.kona.http.Middleware>) () -> new VerifyCsrfToken(List.of("/api/*", "/brew/*")))
            .web(List.of("start.session", "csrf"))
            // Behind a load balancer / ingress:
            // .trustProxies(java.util.List.of("*"))
            // .trustHosts(java.util.List.of("example.com", "*.example.com"))
            .apply(app);
    }

    private Middleware() {
    }
}
