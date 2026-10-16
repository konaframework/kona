package bootstrap;

import io.konaframework.core.Application;
import io.konaframework.filesystem.FilesystemManager;
import io.konaframework.http.HttpKernel;
import io.konaframework.http.HttpMiddleware;
import io.konaframework.http.ServePublicStorage;
import io.konaframework.session.SessionManager;
import io.konaframework.session.StartSession;
import io.konaframework.session.VerifyCsrfToken;
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
            .alias("start.session", (Supplier<io.konaframework.http.Middleware>) () -> new StartSession(sessions))
            .alias("csrf", (Supplier<io.konaframework.http.Middleware>) () -> new VerifyCsrfToken(List.of("/api/*", "/brew/*")))
            .web(List.of("start.session", "csrf"))
            // Behind a load balancer / ingress:
            // .trustProxies(java.util.List.of("*"))
            // .trustHosts(java.util.List.of("example.com", "*.example.com"))
            .apply(app);
    }

    private Middleware() {
    }
}
