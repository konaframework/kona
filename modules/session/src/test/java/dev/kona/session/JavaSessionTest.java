package dev.kona.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import dev.kona.core.Config;
import dev.kona.http.Request;
import dev.kona.http.Response;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaSessionTest {
    @TempDir Path base;

    @Test
    void sessionApiFromJava() {
        Config config = new Config();
        config.set("session.driver", "array");
        config.set("session.lifetime", 60);
        SessionManager manager = SessionManager.fromConfig(config, base);

        StartSession start = new StartSession(manager);
        Response response = start.handle(Request.get("/"), request -> {
            Session session = (Session) request.getAttribute(StartSession.ATTRIBUTE);
            assertNotNull(session);
            session.put("lang", "java");
            session.flash("notice", "hi");
            assertEquals("hi", session.pull("notice", null));
            String token = Csrf.token(session);
            assertEquals(token, session.get("_token"));
            session.regenerate(false);
            return Response.text("ok");
        });
        assertEquals(1, response.getCookies().size());
        assertTrue(SessionId.isValid(response.getCookies().get(0).getValue()));

        StoreSession storeSession = new StoreSession(SessionId.generate(), new ArraySessionStore(), new java.util.LinkedHashMap<>());
        storeSession.start(false);
        storeSession.put("a", 1);
        assertEquals(1, storeSession.all().get("a"));
        storeSession.flashInput(Map.of("email", "j@example.com"));
        assertEquals("j@example.com", storeSession.old("email"));
        assertEquals("j@example.com", storeSession.old("email", "x"));
        storeSession.setPreviousUrl("/java");
        assertEquals("/java", storeSession.previousUrl());
        storeSession.ageFlashData();
        storeSession.invalidate();

        VerifyCsrfToken csrf = new VerifyCsrfToken();
        VerifyCsrfToken csrfExcept = new VerifyCsrfToken(List.of("/skip"));
        assertEquals("ok", csrfExcept.handle(Request.post("/skip"), r -> Response.text("ok")).getBody());

        Application app = Application.builder(base)
            .withProvider(new SessionServiceProvider())
            .boot();
        assertNotNull(app.make(SessionManager.class));

        CookieSessionStore cookies = new CookieSessionStore();
        String id = SessionId.generate();
        cookies.write(id, Map.of("j", true), 10);
        cookies.setInboundPayload(cookies.getOutboundPayload());
        assertEquals(true, cookies.read(id).get("j"));
        assertEquals(id, cookies.readId(cookies.getOutboundPayload()));

        FileSessionStore files = new FileSessionStore(base.resolve("sessions"));
        files.write(id, Map.of("f", 2), 10);
        assertEquals(2, files.read(id).get("f"));
        files.destroy(id);

        ArraySessionStore array = new ArraySessionStore();
        array.write(id, Map.of("x", "y"), 1);
        assertEquals(1, array.size());
        array.clear();

        // Keep csrf reference used for coverage of @JvmOverloads ctor.
        assertNotNull(csrf);
    }
}
