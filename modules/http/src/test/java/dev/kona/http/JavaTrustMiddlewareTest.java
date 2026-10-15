package dev.kona.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JavaTrustMiddlewareTest {
    @Test
    void defaultTrustProxiesAndHttpMiddlewareApiDefaults() {
        TrustProxies mw = new TrustProxies();
        Request request = new Request("GET", "/", Map.of("X-Forwarded-For", "203.0.113.50"));
        mw.handle(request, req -> Response.text("ok"));
        assertEquals("203.0.113.50", request.getAttribute(TrustProxies.CLIENT_IP));

        TrustProxies limited = new TrustProxies(List.of("*"), Set.of(TrustProxies.HEADER_FORWARDED_FOR));
        Request bare = new Request("GET", "/");
        limited.handle(bare, req -> Response.text("ok"));
        assertNull(bare.getAttribute(TrustProxies.CLIENT_IP));

        HttpMiddleware http = new HttpMiddleware().api().web().trustProxies();
        assertEquals(0, http.apiStack().size());
        assertEquals(0, http.webStack().size());
    }
}
