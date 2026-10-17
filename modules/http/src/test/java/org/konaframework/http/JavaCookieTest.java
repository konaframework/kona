package org.konaframework.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaCookieTest {
    @Test
    void cookieApiFromJava() {
        Map<String, String> parsed = Cookie.parseHeader("a=1; b=2");
        assertEquals("1", parsed.get("a"));

        // Exercise every @JvmOverloads Cookie constructor.
        assertTrue(new Cookie("n", "v").toSetCookieHeader().contains("n="));
        assertTrue(new Cookie("n", "v", Duration.ofSeconds(1)).toSetCookieHeader().contains("Max-Age"));
        assertTrue(new Cookie("n", "v", Duration.ofSeconds(1), "/app").toSetCookieHeader().contains("Path=/app"));
        assertTrue(new Cookie("n", "v", Duration.ofSeconds(1), "/app", "ex.test").toSetCookieHeader().contains("Domain"));
        assertTrue(new Cookie("n", "v", Duration.ofSeconds(1), "/app", "ex.test", true).toSetCookieHeader().contains("Secure"));
        assertTrue(new Cookie("n", "v", Duration.ofSeconds(1), "/app", "ex.test", true, false).toSetCookieHeader().contains("n="));
        Cookie cookie = new Cookie("n", "v", Duration.ofSeconds(1), "/", null, false, true, Cookie.SameSite.Lax);
        assertTrue(cookie.toSetCookieHeader().contains("n="));

        Response response = Response.text("ok")
            .cookie("a", "b")
            .cookie("c", "d", 5L)
            .cookie("e", "f", 5L, "/x")
            .cookie("g", "h", 5L, "/x", "ex.test")
            .cookie("i", "j", 5L, "/x", "ex.test", true)
            .cookie("k", "l", 5L, "/x", "ex.test", true, false)
            .cookie("m", "n", 5L, "/x", "ex.test", true, false, Cookie.SameSite.None);
        assertEquals(7, response.getCookies().size());

        Request request = new Request("GET", "/", Map.of("Cookie", "sid=abc"), Map.of(), "", new java.util.HashMap<>());
        assertEquals("abc", request.cookie("sid"));
        assertEquals("abc", request.cookie("sid", "x"));
    }
}
