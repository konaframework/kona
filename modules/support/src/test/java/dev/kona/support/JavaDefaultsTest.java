package dev.kona.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.kona.support.date.KonaDate;
import dev.kona.support.lang.Translator;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaDefaultsTest {
    @Test
    void konaDateDefaultOverloads() {
        assertNotNull(KonaDate.now());
        assertNotNull(KonaDate.now(ZoneOffset.UTC));
        assertNotNull(KonaDate.now(ZoneOffset.UTC, Clock.systemUTC()));
        assertNotNull(KonaDate.today());
        assertNotNull(KonaDate.today(ZoneOffset.UTC));
        assertNotNull(KonaDate.ofInstant(java.time.Instant.EPOCH));
        assertNotNull(KonaDate.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC));
        assertNotNull(KonaDate.parse("2024-06-01T00:00:00Z"));
        assertNotNull(KonaDate.parse("2024-06-01T00:00:00Z", ZoneOffset.UTC));
        assertNotNull(KonaDate.parse("2024-06-01 00:00:00"));
        assertNotNull(KonaDate.parse("2024-06-01 00:00:00", ZoneOffset.ofHours(1)));

        Translator t = new Translator("en");
        t.addLines("en", Map.of("solo", "only"));
        assertEquals("only", t.trans("solo"));
        assertEquals("only", t.get("solo"));
    }
}
