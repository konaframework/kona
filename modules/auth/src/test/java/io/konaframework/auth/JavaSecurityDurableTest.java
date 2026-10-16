package io.konaframework.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.konaframework.auth.hashing.Argon2Hasher;
import io.konaframework.auth.hashing.BcryptHasher;
import io.konaframework.auth.hashing.Hasher;
import io.konaframework.auth.passwords.DatabasePasswordResetTokenRepository;
import io.konaframework.auth.passwords.PasswordBroker;
import io.konaframework.auth.passwords.PasswordResetResult;
import io.konaframework.auth.tokens.DatabasePersonalAccessTokenRepository;
import io.konaframework.auth.tokens.PersonalAccessTokenManager;
import io.konaframework.cadence.Cadence;
import io.konaframework.cadence.CadenceCompanion;
import io.konaframework.cadence.ConnectionConfig;
import io.konaframework.cadence.Database;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JavaSecurityDurableTest {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private Database database;
    private Hasher hasher;

    public static class AuthUser extends Cadence<AuthUser> implements Authenticatable, HasCredentials, HasEmail {
        public static final CadenceCompanion<AuthUser> QUERY = Cadence.of(AuthUser.class);

        @Override
        public String getTable() {
            return "auth_users";
        }

        @Override
        public Set<String> getFillable() {
            return Set.of("email", "password", "remember_token", "name");
        }

        @Override
        public boolean getTimestamps() {
            return false;
        }

        @Override
        public Object getAuthIdentifier() {
            return getAttribute("id");
        }

        @Override
        public String getAuthPassword() {
            Object password = getAttribute("password");
            return password == null ? "" : password.toString();
        }

        @Override
        public String getRememberToken() {
            Object token = getAttribute("remember_token");
            return token == null ? null : token.toString();
        }

        @Override
        public void setRememberToken(String token) {
            setAttribute("remember_token", token);
        }

        @Override
        public Object getCredential(String key) {
            return getAttribute(key);
        }

        @Override
        public String getEmail() {
            Object email = getAttribute("email");
            return email == null ? null : email.toString();
        }
    }

    @BeforeEach
    void setUp() {
        hasher = new BcryptHasher(4);
        database = new Database()
                .addConnection(
                        "default",
                        new ConnectionConfig(
                                "jdbc:h2:mem:jauth"
                                        + COUNTER.incrementAndGet()
                                        + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                                "sa",
                                "",
                                null,
                                2))
                .setAsGlobal();
        database.connection()
                .statement(
                        """
                        CREATE TABLE auth_users (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          email VARCHAR(255),
                          password VARCHAR(255),
                          remember_token VARCHAR(255),
                          name VARCHAR(100)
                        )
                        """);
    }

    @AfterEach
    void tearDown() {
        Database.clearGlobal();
    }

    @Test
    void cadenceProviderAndDurableStoresFromJava() {
        AuthUser.QUERY.create(Map.of("email", "jv@kona.dev", "password", hasher.make("secret"), "name", "Java"));
        CadenceUserProvider<AuthUser> users = new CadenceUserProvider<>(AuthUser.class, hasher);
        Authenticatable found = users.retrieveByCredentials(Map.of("email", "jv@kona.dev"));
        assertNotNull(found);
        assertTrue(users.validateCredentials(found, Map.of("password", "secret")));

        PasswordBroker broker =
                new PasswordBroker(users, hasher, java.time.Duration.ofMinutes(30), java.time.Clock.systemUTC(),
                        new DatabasePasswordResetTokenRepository(database.connection()));
        String token = broker.createToken(Map.of("email", "jv@kona.dev"));
        assertNotNull(token);
        assertEquals(
                PasswordResetResult.RESET,
                broker.reset(Map.of("email", "jv@kona.dev", "token", token, "password", "next"), (user, password) -> {
                    AuthUser model = (AuthUser) user;
                    model.setAttribute("password", hasher.make(password));
                    model.save();
                }));

        PersonalAccessTokenManager tokens = new PersonalAccessTokenManager(
                new DatabasePersonalAccessTokenRepository(database.connection()), users);
        var created = tokens.create(found, "java", java.util.List.of("*"));
        assertTrue(created.getPlainTextToken().contains("|"));
        assertNotNull(tokens.findUser(created.getPlainTextToken()));

        Argon2Hasher argon = new Argon2Hasher(16, 1, 1, 16);
        String hash = argon.make("pw");
        assertTrue(argon.check("pw", hash));
        assertEquals(16, Argon2Hasher.parseParams(hash).getMemory());
        // Default-arg constructor bridge
        assertTrue(new Argon2Hasher().needsRehash("bogus"));
    }
}
