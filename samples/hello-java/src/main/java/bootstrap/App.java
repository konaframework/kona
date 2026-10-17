package bootstrap;

import app.providers.AppServiceProvider;
import config.Database;
import config.Filesystems;
import config.Http;
import config.Paths;
import config.Session;
import org.konaframework.auth.AuthServiceProvider;
import org.konaframework.core.Application;
import org.konaframework.core.Env;
import org.konaframework.core.Kona;
import org.konaframework.crema.CremaServiceProvider;
import org.konaframework.cadence.CadenceServiceProvider;
import org.konaframework.filesystem.FilesystemServiceProvider;
import org.konaframework.http.HttpKernel;
import org.konaframework.http.HttpServiceProvider;
import org.konaframework.http.KonaHttpServer;
import org.konaframework.http.Router;
import org.konaframework.mail.MailServiceProvider;
import org.konaframework.queue.QueueServiceProvider;
import org.konaframework.redis.RedisServiceProvider;
import org.konaframework.schedule.ScheduleServiceProvider;
import org.konaframework.session.SessionServiceProvider;
import routes.ApiRoutes;
import routes.WebRoutes;

public final class App {
    public static Application create(String[] args) {
        Application app = Kona.create(args)
            .configs(
                new config.App(),
                new Database(),
                new Session(),
                new Filesystems(),
                new Http(),
                new Paths()
            )
            .providers(
                new HttpServiceProvider(),
                new CadenceServiceProvider(),
                new FilesystemServiceProvider(),
                new RedisServiceProvider(),
                new SessionServiceProvider(),
                new AuthServiceProvider(),
                new QueueServiceProvider(),
                new ScheduleServiceProvider(),
                new MailServiceProvider(),
                new CremaServiceProvider(),
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
        int port = Integer.parseInt(Env.getOrDefault("PORT", "4000"));
        new KonaHttpServer(app.resolve(HttpKernel.class), host, port).start();
    }

    private App() {
    }
}
