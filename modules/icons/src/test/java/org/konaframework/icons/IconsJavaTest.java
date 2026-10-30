package org.konaframework.icons;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IconsJavaTest {
    @AfterEach
    void reset() {
        Icons.reset();
    }

    @Test
    void javaResolvesHeroiconsAndCustomAttributes() {
        Icons.ensureDefaults();
        Svg svg = Icons.svg("heroicon-o-home");
        assertTrue(svg.toHtml().contains("svg"));
        assertTrue(Icons.svg("heroicon-o-bell", "w-4").toHtml().contains("w-4"));
        assertTrue(Icons.svg("heroicon-s-check", "icon", Map.of("data-t", "1")).toHtml().contains("data-t=\"1\""));
        assertEquals("heroicons", Icons.defaultSet());
        assertTrue(Icons.has("heroicon-m-user"));
        Icons.setDefault("heroicons");
        Icons.setFallback(null);
        assertTrue(Icons.all().containsKey("heroicons"));
        Icons.add("custom", new IconSetOptions("custom", null, java.util.List.of(), "icons/custom"));
        assertTrue(Icons.svg("custom-star").toHtml().contains("svg"));
        Icons.add(
            "custom2",
            new IconSetOptions(
                "custom2",
                null,
                java.util.List.of(),
                "icons/custom",
                java.util.List.of(),
                "cls",
                Map.of("data-a", "1"),
                "star"
            )
        );
        assertTrue(Icons.svg("custom2-missing").toHtml().contains("svg"));
    }
}
