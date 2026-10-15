package bootstrap;

import app.providers.AppServiceProvider;
import dev.kona.auth.AuthServiceProvider;
import dev.kona.core.Application;
import dev.kona.core.Env;
import dev.kona.core.Kona;
import dev.kona.extract.ExtractServiceProvider;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.KonaHttpServer;
import dev.kona.http.Router;
import dev.kona.mail.MailServiceProvider;
import dev.kona.queue.QueueServiceProvider;
import dev.kona.redis.RedisServiceProvider;
import dev.kona.schedule.ScheduleServiceProvider;
import dev.kona.session.SessionServiceProvider;
import routes.ApiRoutes;
import routes.WebRoutes;

/**
 * Application entry. {@code grind serve --in-process} calls {@link #create}; {@code ./gradlew run} calls {@link #main}.
 */
public final class App {
    public static Application create(String[] args) {
        Application app = Kona.create(args)
            .providers(
                new HttpServiceProvider(),
                new ExtractServiceProvider(),
                new RedisServiceProvider(),
                new SessionServiceProvider(),
                new AuthServiceProvider(),
                new QueueServiceProvider(),
                new ScheduleServiceProvider(),
                new MailServiceProvider(),
                new AppServiceProvider()
            )
            .afterBoot(Middleware::configure)
            .boot();
        new WebRoutes().register(app.resolve(Router.class));
        new ApiRoutes().register(app.resolve(Router.class));
        return app;
    }

    public static void main(String[] args) {
        Application app = create(args);
        String host = Env.getOrDefault("HOST", "127.0.0.1");
        int port = Integer.parseInt(Env.getOrDefault("PORT", "8080"));
        new KonaHttpServer(app.resolve(HttpKernel.class), host, port).start();
    }

    private App() {
    }
}
