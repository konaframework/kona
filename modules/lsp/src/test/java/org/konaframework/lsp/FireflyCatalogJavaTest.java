package org.konaframework.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FireflyCatalogJavaTest {
    @Test
    void javaCallsTheCatalog() {
        assertFalse(FireflyCatalog.types().isEmpty());
        assertFalse(FireflyCatalog.completions("Resource", "java", 0, 8).isEmpty());
        assertEquals(
            "org.konaframework.firefly.widgets.Page",
            FireflyCatalog.hover("Page", "java", 0, 0)
        );
        assertNull(FireflyCatalog.read(null).stream().findFirst().orElse(null));
    }
}
