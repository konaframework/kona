package bootstrap;

import org.konaframework.core.Application;
import org.konaframework.filesystem.FilesystemManager;
import org.konaframework.http.HttpKernel;
import org.konaframework.http.HttpMiddleware;
import org.konaframework.http.ServePublicStorage;
import org.konaframework.session.SessionManager;
import org.konaframework.session.StartSession;
import org.konaframework.session.VerifyCsrfToken;
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
            .alias("start.session", (Supplier<org.konaframework.http.Middleware>) () -> new StartSession(sessions))
            .alias("csrf", (Supplier<org.konaframework.http.Middleware>) () -> new VerifyCsrfToken(List.of("/api/*", "/brew/*")))
            .web(List.of("start.session", "csrf"))
            // Behind a load balancer / ingress:
            // .trustProxies(java.util.List.of("*"))
            // .trustHosts(java.util.List.of("example.com", "*.example.com"))
            .apply(app);
    }

    private Middleware() {
    }
}
