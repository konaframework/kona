package dev.kona.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.auth.hashing.BcryptHasher;
import dev.kona.auth.hashing.HashManager;
import dev.kona.auth.middleware.Authenticate;
import dev.kona.auth.middleware.AuthenticateSession;
import dev.kona.auth.middleware.Authorize;
import dev.kona.auth.middleware.RedirectIfAuthenticated;
import dev.kona.auth.authorization.Gate;
import dev.kona.auth.session.SessionAttributes;
import dev.kona.auth.tokens.PersonalAccessTokenManager;
import dev.kona.core.Config;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.security.CorsMiddleware;
import dev.kona.security.Encrypter;
import dev.kona.security.RateLimit;
import dev.kona.security.RateLimiter;
import dev.kona.security.SecurityHeadersConfig;
import dev.kona.security.SecurityHeadersMiddleware;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaCoverageBoostTest {
    @Test
    void jvmOverloadsAndAdapters() {
        BcryptHasher hasher = new BcryptHasher();
        BcryptHasher hasher4 = new BcryptHasher(4);
        assertTrue(hasher4.check("pw", hasher4.make("pw")));
        assertNull(BcryptHasher.parseCost("$2a$"));

        HashManager hm = new HashManager();
        assertNotNull(hm.driver());

        AuthManager auth = new AuthManager();
        InMemoryUserProvider users = new InMemoryUserProvider(hasher4);
        SimpleUser user = new SimpleUser(1, hasher4.make("secret"), "a@b.c");
        users.add(user);
        auth.provider("users", users);
        auth.extend("web", new SessionGuard("web", users));
        Request request = Request.get("/");
        SessionAttributes.ensure(request);
        auth.setRequest(request);
        auth.login(user);
        auth.login(user, true);
        assertTrue(auth.check());
        assertEquals(1, auth.id());

        Auth.setManager(auth);
        Auth.login(user);
        Auth.login(user, false);
        assertEquals("/", Authenticate.pullIntended(request));
        assertEquals("/home", Authenticate.pullIntended(request, "/home"));
        new Authenticate(auth).handle(request, r -> Response.text("a"));
        new Authenticate(auth, "/login").handle(request, r -> Response.text("a"));
        new Authenticate(auth, "/login", List.of("web")).handle(request, r -> Response.text("a"));
        new RedirectIfAuthenticated(auth).handle(request, r -> Response.text("b"));
        new RedirectIfAuthenticated(auth, "/").handle(request, r -> Response.text("b"));
        new RedirectIfAuthenticated(auth, "/", List.of("web")).handle(request, r -> Response.text("b"));
        new AuthenticateSession(auth).handle(request, r -> Response.text("c"));
        new AuthenticateSession(auth, "web").handle(request, r -> Response.text("c"));
        new AuthenticateSession(auth, "web", "/login").handle(request, r -> Response.text("c"));
        Gate gate = new Gate().define("x", (u, args) -> true);
        new Authorize(gate, "x").handle(request, r -> Response.text("d"));
        new Authorize(gate, "x", new Object[] {"m"}).handle(request, r -> Response.text("d"));
        Auth.clearManager();

        RateLimiter limiter = new RateLimiter();
        assertTrue(limiter.attempt("a", 5));
        assertTrue(limiter.hit("a") >= 1);
        RateLimit limit = new RateLimit(10);
        RateLimit limit2 = new RateLimit(10, 30);
        assertEquals(10, limit.getMaxAttempts());
        assertEquals(30, limit2.getDecaySeconds());
        assertEquals(1, RateLimit.perSecond(1).getDecaySeconds());
        assertEquals(60, RateLimit.perMinute(2).getDecaySeconds());
        assertEquals(3600, RateLimit.perHour(3).getDecaySeconds());
        assertEquals(86400, RateLimit.perDay(4).getDecaySeconds());

        SecurityHeadersConfig cfg = new SecurityHeadersConfig();
        new SecurityHeadersMiddleware(cfg).handle(Request.get("/"), r -> Response.text("ok"));
        new CorsMiddleware().handle(Request.get("/"), r -> Response.text("ok"));

        // adaptForeignSession missing put/forget
        assertNull(SessionAttributes.adaptForeignSession(new Object() {
            public Object get(String key) {
                return null;
            }
        }));
        assertNull(SessionAttributes.adaptForeignSession(new Object() {
            public Object get(String key) {
                return null;
            }

            public void put(String key, Object value) {
            }
        }));

        Object ok = new Object() {
            private final java.util.Map<String, Object> m = new java.util.HashMap<>();

            public Object get(String key) {
                return m.get(key);
            }

            public void put(String key, Object value) {
                m.put(key, value);
            }

            public void forget(String key) {
                m.remove(key);
            }
        };
        assertNotNull(SessionAttributes.adaptForeignSession(ok));

        Encrypter enc = Encrypter.fromBase64Key(Encrypter.generateKey());
        String payload = enc.encryptString("x");
        assertEquals("x", enc.decryptString(payload));

        // Hit @JvmStatic forwarders on the outer classes (Kotlin calls Companion directly).
        assertNull(TokenGuard.bearerToken(Request.get("/")));
        assertNotNull(PersonalAccessTokenManager.hashToken("id|secret"));
        Config config = new Config();
        config.set("app.key", Encrypter.generateKey());
        assertEquals("y", Encrypter.fromConfig(config).decryptString(Encrypter.fromConfig(config).encryptString("y")));
    }
}
