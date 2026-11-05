package org.konaframework.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.auth.hashing.BcryptHasher;
import org.konaframework.auth.middleware.RequirePassword;
import org.konaframework.auth.session.MapSessionBag;
import org.konaframework.auth.twofactor.ConfirmTwoFactorAuthentication;
import org.konaframework.auth.twofactor.EnableTwoFactorAuthentication;
import org.konaframework.auth.twofactor.InMemoryUsedCodeStore;
import org.konaframework.auth.twofactor.RecoveryCode;
import org.konaframework.auth.twofactor.RedirectIfTwoFactorAuthenticatable;
import org.konaframework.auth.twofactor.Totp;
import org.konaframework.auth.twofactor.TwoFactor;
import org.konaframework.auth.twofactor.TwoFactorAuthenticatable;
import org.konaframework.auth.twofactor.TwoFactorAuthenticationProvider;
import org.konaframework.auth.twofactor.TwoFactorLogin;
import org.konaframework.http.Request;
import org.konaframework.security.Encrypter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java-callable default-parameter paths for two-factor APIs.
 */
class JavaTwoFactorTest {
    private final BcryptHasher hasher = new BcryptHasher(4);
    private final Encrypter encrypter = Encrypter.fromBase64Key(Encrypter.generateKey());

    @AfterEach
    void tearDown() {
        TwoFactor.setProvider(null);
        TwoFactor.setEncrypter(null);
        TwoFactor.setRequiresConfirmation(true);
        RecoveryCode.useGenerator(null);
        Auth.clearManager();
        Hash.clearManager();
    }

    @Test
    void defaultConstructorsAndFactories() throws Exception {
        InMemoryUsedCodeStore store = new InMemoryUsedCodeStore();
        TwoFactorAuthenticationProvider provider = new TwoFactorAuthenticationProvider();
        String secret = provider.generateSecretKey();
        assertNotNull(secret);
        assertTrue(provider.qrCodeUrl("Kona", "a@b.c", secret).startsWith("otpauth://"));
        long now = System.currentTimeMillis() / 1000L;
        assertNotNull(Totp.verify(secret, Totp.code(secret)));
        assertNotNull(Totp.verify(secret, Totp.code(secret, now), now));
        assertNotNull(Totp.verify(secret, Totp.code(secret, now), now, 1));
        assertNotNull(Totp.verify(secret, Totp.code(secret, now), now, 1, 30L));
        assertNotNull(Totp.verify(secret, Totp.code(secret, now), now, 1, 30L, 6));
        assertTrue(Totp.otpAuthUrl("Kona", "a@b.c", secret).startsWith("otpauth://"));
        assertTrue(Totp.otpAuthUrl("Kona", "a@b.c", secret, 6).startsWith("otpauth://"));
        assertTrue(Totp.otpAuthUrl("Kona", "a@b.c", secret, 6, 30L).startsWith("otpauth://"));
        assertEquals(6, Totp.code(secret).length());
        assertEquals(6, Totp.code(secret, now).length());
        assertEquals(6, Totp.code(secret, now, 30L).length());
        assertEquals(6, Totp.code(secret, now, 30L, 6).length());

        ConfirmTwoFactorAuthentication confirm = new ConfirmTwoFactorAuthentication(provider, encrypter);
        RequirePassword requirePassword = new RequirePassword(new AuthManager());
        assertNotNull(requirePassword);

        List<String> codes = RecoveryCode.generateMany();
        assertEquals(8, codes.size());

        JavaUser user = new JavaUser(1L, hasher.make("password"), "ada@example.com");
        new EnableTwoFactorAuthentication(provider, encrypter).enable(user);
        String plain = encrypter.decryptString(user.getTwoFactorSecret());
        assertTrue(confirm.confirm(user, Totp.code(plain)));

        InMemoryUserProvider users = new InMemoryUserProvider(hasher);
        users.add(user);
        users.add(new SimpleUser(2L, hasher.make("password"), "bob@example.com"));
        AuthManager auth = new AuthManager();
        auth.provider("users", users);
        auth.extend("web", new SessionGuard("web", users));
        Auth.setManager(auth);

        Request login = new Request("POST", "/login");
        login.setAttribute("session", new HashMap<String, Object>());
        RedirectIfTwoFactorAuthenticatable.Result challenged =
                new RedirectIfTwoFactorAuthenticatable(auth, users)
                        .handle(login, Map.of("email", "ada@example.com", "password", "password"));
        assertTrue(challenged.challenged());
        assertFalse(challenged.passed());
        assertFalse(challenged.failed());

        Request plainLogin = new Request("POST", "/login");
        plainLogin.setAttribute("session", new HashMap<String, Object>());
        RedirectIfTwoFactorAuthenticatable.Result ok =
                new RedirectIfTwoFactorAuthenticatable(auth, users)
                        .handle(plainLogin, Map.of("email", "bob@example.com", "password", "password"));
        assertTrue(ok.passed());
        assertFalse(ok.challenged());
        assertFalse(ok.failed());

        RedirectIfTwoFactorAuthenticatable.Result invalid =
                new RedirectIfTwoFactorAuthenticatable(auth, users)
                        .handle(plainLogin, Map.of("email", "missing@example.com", "password", "x"));
        assertTrue(invalid.failed());
        assertFalse(invalid.challenged());
        assertFalse(invalid.passed());

        Request challenge = new Request("POST", "/two-factor-challenge");
        MapSessionBag session = new MapSessionBag(new HashMap<>());
        challenge.setAttribute("session", session);
        TwoFactor.rememberLogin(challenge, 1L);
        session.forget(TwoFactor.LOGIN_REMEMBER);
        assertFalse(TwoFactor.pendingRemember(challenge));

        // Fresh provider so the confirm OTP is not treated as a replay.
        TwoFactorAuthenticationProvider loginProvider = new TwoFactorAuthenticationProvider();
        long later = (System.currentTimeMillis() / 1000L) + 30L;
        TwoFactorLogin loginHelper = new TwoFactorLogin(auth, users, loginProvider, encrypter, null, 5, 60);
        assertNull(loginHelper.attempt(challenge));
        assertNull(loginHelper.attempt(challenge, "000000"));
        TwoFactor.rememberLogin(challenge, 1L);
        assertNotNull(loginHelper.attempt(challenge, Totp.code(plain, later), null));

        // Non-TwoFactorAuthenticatable pending id
        Request orphan = new Request("POST", "/c");
        orphan.setAttribute("session", new HashMap<String, Object>());
        TwoFactor.rememberLogin(orphan, 2L);
        assertNull(new TwoFactorLogin(auth, users, provider, encrypter).attempt(orphan, "123456", null));

        // Numeric string password confirmation timestamp
        Request secure = new Request("GET", "/secure");
        MapSessionBag secureSession = new MapSessionBag(new HashMap<>());
        secure.setAttribute("session", secureSession);
        auth.setRequest(secure);
        auth.login(user);
        RequirePassword.confirm(secure);
        org.konaframework.http.Response confirmed =
                new RequirePassword(auth).handle(secure, req -> org.konaframework.http.Response.text("ok"));
        assertEquals("ok", confirmed.getBody());
        secureSession.put(RequirePassword.CONFIRMED_AT_KEY, String.valueOf(Instant.now().getEpochSecond()));
        assertEquals("ok", new RequirePassword(auth).handle(secure, req -> org.konaframework.http.Response.text("ok")).getBody());

        // newerThan set but matched > newerThan → still accepted
        InMemoryUsedCodeStore replay = new InMemoryUsedCodeStore();
        java.time.Clock fixed = java.time.Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), java.time.ZoneOffset.UTC);
        TwoFactorAuthenticationProvider windowed = new TwoFactorAuthenticationProvider(1, 30L, replay, fixed);
        String s2 = Totp.generateSecret();
        String code = Totp.code(s2, Instant.now(fixed).getEpochSecond());
        String cacheKey = "kona.2fa_codes." + md5(code);
        replay.put(cacheKey, 0L, 60L);
        assertTrue(windowed.verify(s2, code));
    }

    private static String md5(String value) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("MD5")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static final class JavaUser implements Authenticatable, HasCredentials, HasEmail, TwoFactorAuthenticatable {
        private final Object id;
        private final String password;
        private final String email;
        private String secret;
        private String recovery;
        private Instant confirmedAt;

        JavaUser(Object id, String password, String email) {
            this.id = id;
            this.password = password;
            this.email = email;
        }

        @Override
        public Object getAuthIdentifier() {
            return id;
        }

        @Override
        public String getAuthPassword() {
            return password;
        }

        @Override
        public Object getCredential(String key) {
            return "email".equals(key) ? email : null;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public String getTwoFactorSecret() {
            return secret;
        }

        @Override
        public void setTwoFactorSecret(String secret) {
            this.secret = secret;
        }

        @Override
        public String getTwoFactorRecoveryCodes() {
            return recovery;
        }

        @Override
        public void setTwoFactorRecoveryCodes(String codes) {
            this.recovery = codes;
        }

        @Override
        public Instant getTwoFactorConfirmedAt() {
            return confirmedAt;
        }

        @Override
        public void setTwoFactorConfirmedAt(Instant confirmedAt) {
            this.confirmedAt = confirmedAt;
        }

        @Override
        public void saveTwoFactorState() {
        }
    }
}
