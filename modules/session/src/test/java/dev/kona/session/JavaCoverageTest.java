package dev.kona.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Config;
import dev.kona.http.Request;
import dev.kona.http.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaCoverageTest {
    @Test
    void interfaceDefaultsAndStores(@TempDir Path base) throws Exception {
        ArraySessionStore store = new ArraySessionStore();
        StoreSession session = new StoreSession(SessionId.generate(), store);
        Session asInterface = session;
        asInterface.put("a", 1);
        assertEquals(1, asInterface.pull("a"));
        assertNull(asInterface.pull("missing"));
        asInterface.put("b", 2);
        asInterface.regenerate();
        asInterface.regenerate(false);

        Config config = new Config();
        config.set("session.driver", "cookie");
        SessionManager twoArg = new SessionManager(config, base);
        assertNotNull(twoArg.driver());
        RedisSessionStore redisStore = new RedisSessionStore(new dev.kona.redis.InMemoryRedisConnection());
        redisStore.write("id", Map.of("a", 1), 10);
        assertEquals(1, ((Number) redisStore.read("id").get("a")).intValue());
        SessionManager manager = SessionManager.fromConfig(config, base);
        StoreSession first = manager.start(Request.get("/"));
        first.put("x", "y");
        Response response = Response.text("ok");
        manager.save(first, response);
        String payload = response.getCookies().get(0).getValue();
        StoreSession again = manager.start(
            new Request("GET", "/", Map.of("Cookie", "kona_session=" + payload), Map.of(), "")
        );
        assertEquals("y", again.get("x"));

        Path dir = base.resolve("files");
        FileSessionStore files = new FileSessionStore(dir);
        String id = SessionId.generate();
        files.write(id, Map.of("k", 1), 60);
        assertEquals(1, ((Number) files.read(id).get("k")).intValue());
        Files.writeString(dir.resolve(id), "{\"expires_at\":1,\"data\":{\"k\":1}}");
        assertTrue(files.read(id).isEmpty());
        String id2 = SessionId.generate();
        Files.writeString(dir.resolve(id2), "{\"data\":{\"k\":2}}");
        assertEquals(2, ((Number) files.read(id2).get("k")).intValue());
        assertFalse(FileSessionStore.isValidId("nope"));

        VerifyCsrfToken csrf = new VerifyCsrfToken();
        Request post = Request.post("/x");
        post.setAttribute(StartSession.ATTRIBUTE, session);
        session.put(Csrf.TOKEN_KEY, 123);
        try {
            csrf.handle(post, r -> Response.text("n"));
            throw new AssertionError("expected mismatch");
        } catch (TokenMismatchException expected) {
            assertEquals(419, expected.getStatus());
        }
        assertNotNull(Csrf.generate());

        // Cookie payload with embedded id that fails SessionId.isValid.
        String badIdPayload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"id\":\"short\",\"data\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Config cookieCfg = new Config();
        cookieCfg.set("session.driver", "cookie");
        SessionManager cm = SessionManager.fromConfig(cookieCfg, base);
        StoreSession fromBad = cm.start(
            new Request("GET", "/", Map.of("Cookie", "kona_session=" + badIdPayload), Map.of(), "")
        );
        assertNotNull(fromBad.getId());
        assertTrue(SessionId.isValid(fromBad.getId()));

        // Immutable List flash bag branch.
        StoreSession flashSession = new StoreSession(SessionId.generate(), new ArraySessionStore());
        flashSession.put(StoreSession.FLASH_NEW, java.util.List.of("a"));
        flashSession.flash("b", 1);
        assertTrue(((java.util.List<?>) flashSession.get(StoreSession.FLASH_NEW)).contains("a"));
    }
}
