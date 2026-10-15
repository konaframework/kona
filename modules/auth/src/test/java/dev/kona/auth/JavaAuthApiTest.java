package dev.kona.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.auth.authorization.AuthorizationException;
import dev.kona.auth.authorization.Gate;
import dev.kona.auth.authorization.Policy;
import dev.kona.auth.hashing.BcryptHasher;
import dev.kona.auth.hashing.HashManager;
import dev.kona.auth.middleware.Authenticate;
import dev.kona.auth.middleware.Authorize;
import dev.kona.auth.passwords.PasswordBroker;
import dev.kona.auth.passwords.PasswordResetResult;
import dev.kona.auth.passwords.PasswordRules;
import dev.kona.auth.session.SessionAttributes;
import dev.kona.auth.tokens.AuthenticateWithToken;
import dev.kona.auth.tokens.NewAccessToken;
import dev.kona.auth.tokens.PersonalAccessTokenManager;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.security.CorsConfig;
import dev.kona.security.CorsMiddleware;
import dev.kona.security.Encrypter;
import dev.kona.security.RateLimit;
import dev.kona.security.RateLimiter;
import dev.kona.security.SecurityHeadersMiddleware;
import dev.kona.security.ThrottleRequests;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaAuthApiTest {
    @Test
    void hashingAuthGateTokensAndSecurityFromJava() {
        HashManager hashes = new HashManager("bcrypt", 4);
        Hash.setManager(hashes);
        String hashed = Hash.make("secret");
        assertTrue(Hash.check("secret", hashed));
        assertFalse(Hash.needsRehash(hashed));

        InMemoryUserProvider users = new InMemoryUserProvider(hashes.driver());
        SimpleUser user = new SimpleUser(1, hashes.make("secret"), "java@kona.dev");
        users.add(user);

        AuthManager auth = new AuthManager();
        auth.provider("users", users);
        auth.extend("web", new SessionGuard("web", users));
        Auth.setManager(auth);
        Request request = Request.get("/login");
        SessionAttributes.ensure(request);
        auth.setRequest(request);
        assertTrue(auth.validate(Map.of("email", "java@kona.dev", "password", "secret")));
        assertTrue(auth.attempt(Map.of("email", "java@kona.dev", "password", "secret"), false));
        assertEquals(1, auth.id());
        assertTrue(Auth.check());
        auth.logout();
        assertTrue(auth.guest());
        assertTrue(auth.onceUsingId(1));
        auth.logout();
        Response unauth = new Authenticate(auth, "/login").handle(Request.get("/dash"), r -> Response.text("no"));
        assertEquals(302, unauth.getStatus());

        PasswordBroker broker = new PasswordBroker(users, hashes.driver());
        String token = broker.createToken(Map.of("email", "java@kona.dev"));
        PasswordResetResult reset = broker.reset(
                Map.of("email", "java@kona.dev", "token", token, "password", "next"),
                (u, password) -> users.clear().add(new SimpleUser(u.getAuthIdentifier(), hashes.make(password), "java@kona.dev")));
        assertEquals(PasswordResetResult.RESET, reset);

        Gate gate = new Gate();
        gate.define("hello", (u, args) -> u != null);
        gate.setUserResolver(auth::user);
        auth.login(users.retrieveById(1), false);
        assertTrue(gate.allows("hello"));
        gate.policy(String.class, new Policy() {
            @Override
            public Boolean before(Authenticatable u, String ability) {
                return null;
            }

            @Override
            public boolean authorize(Authenticatable u, String ability, Object model) {
                return "read".equals(ability);
            }
        });
        assertTrue(gate.allows("read", "doc"));
        assertTrue(gate.any(List.of("hello", "missing")));
        assertThrows(AuthorizationException.class, () -> gate.authorize("write", "doc"));
        assertThrows(AuthorizationException.class, () -> new Authorize(gate, "write").handle(request, r -> Response.text("x")));
        assertTrue(PasswordRules.min(8).contains("min:8"));
        assertEquals(60, RateLimit.perMinute(5).getDecaySeconds());

        PersonalAccessTokenManager tokens = new PersonalAccessTokenManager(
                new dev.kona.auth.tokens.InMemoryPersonalAccessTokenRepository(),
                users);
        NewAccessToken created = tokens.create(user, "java", List.of("*"));
        TokenGuard api = new TokenGuard("api", tokens, users);
        Request bearer = new Request(
                "GET",
                "/api",
                Map.of("Authorization", "Bearer " + created.getPlainTextToken()),
                Map.of(),
                "",
                new java.util.LinkedHashMap<>());
        api.setRequest(bearer);
        assertTrue(api.check());
        Response ok = new AuthenticateWithToken(tokens, users).handle(bearer, r -> Response.text("ok"));
        assertEquals("ok", ok.getBody());

        String key = Encrypter.generateKey();
        Encrypter encrypter = Encrypter.fromBase64Key(key);
        assertEquals("payload", encrypter.decryptString(encrypter.encryptString("payload")));

        Response headers = new SecurityHeadersMiddleware().handle(Request.get("/"), r -> Response.text("h"));
        assertEquals("nosniff", headers.getHeaders().get("X-Content-Type-Options"));

        CorsMiddleware cors = new CorsMiddleware(new CorsConfig());
        Response preflight = cors.handle(
                new Request(
                        "OPTIONS",
                        "/",
                        Map.of(
                                "Origin", "https://example.com",
                                "Access-Control-Request-Method", "POST"),
                        Map.of(),
                        "",
                        new java.util.LinkedHashMap<>()),
                r -> Response.text("nope"));
        assertEquals(204, preflight.getStatus());

        RateLimiter limiter = new RateLimiter();
        limiter.forName("api", req -> new RateLimit(5, 60));
        ThrottleRequests throttle = new ThrottleRequests(limiter, "api");
        Response limited = throttle.handle(Request.get("/"), r -> Response.text("ok"));
        assertEquals("5", limited.getHeaders().get("X-RateLimit-Limit"));

        assertEquals(4, new BcryptHasher(4).make("x").split("\\$")[2].length() > 0 ? 4 : 0);
        Hash.clearManager();
        Auth.clearManager();
    }
}
