package hello;

import dev.kona.auth.AuthManager;
import dev.kona.auth.AuthServiceProvider;
import dev.kona.auth.Hash;
import dev.kona.auth.InMemoryUserProvider;
import dev.kona.auth.SimpleUser;
import dev.kona.core.Application;
import dev.kona.core.Kona;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.http.Router;
import dev.kona.session.Session;
import dev.kona.session.SessionManager;
import dev.kona.session.SessionServiceProvider;
import dev.kona.session.StartSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal session flash + auth attempt demo for M3. */
public final class SecurityDemo {
    public static Application boot(String[] args) {
        return Kona.create(args)
            .providers(new HttpServiceProvider(), new SessionServiceProvider(), new AuthServiceProvider())
            .configure(cfg -> cfg.set("session.driver", "array"))
            .boot();
    }

    public static List<String> run(String[] args) {
        Kona.reset();
        Application app = boot(args);
        InMemoryUserProvider users = app.resolve(InMemoryUserProvider.class);
        users.add(new SimpleUser(1, Hash.make("secret"), "dev@example.com"));

        SessionManager sessions = app.resolve(SessionManager.class);
        HttpKernel kernel = app.resolve(HttpKernel.class);
        kernel.pushMiddleware(new StartSession(sessions));

        AuthManager auth = app.resolve(AuthManager.class);
        Router router = app.resolve(Router.class);
        router.get("/flash", request -> {
            Session session = (Session) request.getAttribute(StartSession.ATTRIBUTE);
            Object prior = session.get("notice");
            session.flash("notice", "saved");
            return Response.json(Map.of("prior", prior == null ? "" : prior.toString()));
        });
        router.post("/login", request -> {
            auth.setRequest(request);
            boolean ok = auth.attempt(Map.of(
                "email", request.input("email", null),
                "password", request.input("password", null)
            ));
            if (!ok) {
                return Response.json(Map.of("ok", false), 401);
            }
            return Response.json(Map.of("ok", true, "id", auth.id()));
        });

        Response first = kernel.handle(Request.get("/flash"));
        String sid = first.getCookies().get(0).getValue();
        String cookieHeader = sessions.cookieName() + "=" + sid;
        Response second = kernel.handle(new Request(
            "GET",
            "/flash",
            Map.of("Cookie", cookieHeader),
            Map.of(),
            "",
            new HashMap<>()
        ));
        Response login = kernel.handle(new Request(
            "POST",
            "/login",
            Map.of(
                "Cookie", cookieHeader,
                "Content-Type", "application/x-www-form-urlencoded"
            ),
            Map.of(),
            "email=dev%40example.com&password=secret",
            new HashMap<>()
        ));
        return List.of(
            "flash1=" + first.getStatus() + ":" + first.getBody(),
            "flash2=" + second.getStatus() + ":" + second.getBody(),
            "login=" + login.getStatus() + ":" + login.getBody()
        );
    }

    private SecurityDemo() {
    }
}
