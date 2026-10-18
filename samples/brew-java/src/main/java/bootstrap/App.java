package bootstrap;

import org.konaframework.brew.BrewServiceProvider;
import org.konaframework.core.Application;
import org.konaframework.core.Env;
import org.konaframework.core.Kona;
import org.konaframework.crema.CremaServiceProvider;
import org.konaframework.http.HttpKernel;
import org.konaframework.http.HttpMiddleware;
import org.konaframework.http.HttpServiceProvider;
import org.konaframework.http.KonaHttpServer;
import org.konaframework.http.Router;
import org.konaframework.session.SessionManager;
import org.konaframework.session.SessionServiceProvider;
import org.konaframework.session.StartSession;
import org.konaframework.session.VerifyCsrfToken;
import org.konaframework.http.Middleware;
import java.util.List;
import java.util.function.Supplier;
import routes.WebRoutes;

public final class App {
    private App() {}

    public static Application create(String[] args) {
        Application app = Kona.create(args)
            .configure(config -> {
                config.set("app.key", Env.getOrDefault("APP_KEY", "brew-sample-java-key"));
                config.set("app.debug", true);
                config.set("session.driver", "cookie");
            })
            .providers(new HttpServiceProvider(), new SessionServiceProvider(), new CremaServiceProvider(), new BrewServiceProvider())
            .afterBoot(App::middleware)
            .boot();
        WebRoutes.register(app.resolve(Router.class));
        return app;
    }

    private static void middleware(Application app) {
        SessionManager sessions = app.resolve(SessionManager.class);
        new HttpMiddleware()
            .alias("start.session", (Supplier<Middleware>) () -> new StartSession(sessions))
            .alias("csrf", (Supplier<Middleware>) VerifyCsrfToken::new)
            .web(List.of("start.session", "csrf"))
            .apply(app);
    }

    public static void main(String[] args) {
        Application app = create(args);
        String host = Env.getOrDefault("HOST", "127.0.0.1");
        int port = Integer.parseInt(Env.getOrDefault("PORT", "4000"));
        new KonaHttpServer(app.resolve(HttpKernel.class), host, port).start();
    }
}
