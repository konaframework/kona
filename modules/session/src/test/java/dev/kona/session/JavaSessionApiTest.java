package dev.kona.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import dev.kona.http.DefaultExceptionHandler;
import dev.kona.http.HttpKernel;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.http.Router;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaSessionApiTest {
    @Test
    void sessionManagerMiddlewareAndCsrfFromJava(@TempDir Path base) {
        Application app = new Application(base);
        app.getConfig().set("session.driver", "array");
        app.getConfig().set("session.cookie", "kona_session");
        app.register(new SessionServiceProvider());
        app.boot();

        SessionManager manager = app.resolve(SessionManager.class);
        assertEquals("array", manager.driverName());
        assertNotNull(manager.driver());

        Router router = new Router();
        router.get("/me", request -> {
            Session session = (Session) request.getAttribute(StartSession.ATTRIBUTE);
            return Response.json(Map.of(
                "id", session.getId(),
                "user", String.valueOf(session.get("user"))
            ));
        });
        router.post("/login", request -> {
            Session session = (Session) request.getAttribute(StartSession.ATTRIBUTE);
            session.put("user", "ada");
            String token = Csrf.token(session);
            return Response.json(Map.of("token", token));
        });
        router.post("/action", request -> Response.text("done"));

        HttpKernel kernel = new HttpKernel(
            router,
            List.of(new StartSession(manager), new VerifyCsrfToken(List.of("/login"))),
            new DefaultExceptionHandler()
        );

        Response login = kernel.handle(Request.post("/login"));
        assertEquals(200, login.getStatus());
        assertFalse(login.getCookies().isEmpty());
        String sid = login.getCookies().get(0).getValue();
        String token = login.getBody().replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        Response ok = kernel.handle(new Request(
            "POST",
            "/action",
            Map.of("Cookie", "kona_session=" + sid, "X-CSRF-TOKEN", token),
            Map.of(),
            ""
        ));
        assertEquals("done", ok.getBody());

        Response me = kernel.handle(new Request(
            "GET",
            "/me",
            Map.of("Cookie", "kona_session=" + sid),
            Map.of(),
            ""
        ));
        assertTrue(me.getBody().contains("ada"));

        StoreSession storeSession = manager.start(Request.get("/"));
        storeSession.put("k", "v");
        assertTrue(storeSession.has("k"));
        storeSession.flash("f", "1");
        storeSession.regenerate();
        storeSession.regenerate(true);
        Map<String, Object> all = storeSession.all();
        assertTrue(all.containsKey("k"));
        storeSession.forget("k");
        storeSession.invalidate();
        assertTrue(storeSession.all().isEmpty());

        assertEquals(64, Csrf.generate().length());
        assertTrue(Csrf.tokensMatch("abc", "abc"));
        assertFalse(Csrf.tokensMatch("a", "b"));
        assertEquals(419, new TokenMismatchException().getStatus());
    }
}
