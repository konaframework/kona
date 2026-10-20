package org.konaframework.crema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.http.Url;

/** Java-callable Crema echo helpers (Law 8). */
class JavaCremaHelpersTest {
    @AfterEach
    void reset() {
        Url.clearGenerator();
    }

    @Test
    void urlRouteAndCatalogFromJava() {
        assertEquals("/branding/kona-logo.svg", CremaHelpers.url("/branding/kona-logo.svg"));
        assertEquals("/login", CremaHelpers.route("login"));
        CremaContext context = new CremaContext();
        context.set("_token", "tok");
        assertEquals("tok", CremaHelpers.csrfToken(context));
        assertEquals(
                "/logo.svg",
                CremaHelpers.call("url", List.of("logo.svg"), new CremaContext()));
        assertTrue(CremaHelpers.isCallable("url"));
        assertTrue(CremaHelpers.completions("ur").contains("url"));
        assertEquals("url", CremaHelpers.parseCall("url('/x')").getFirst());
    }
}
