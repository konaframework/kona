package org.konaframework.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.konaframework.support.date.KonaDate;
import org.konaframework.support.lang.Translator;
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
        assertNotNull(KonaDate.parse("2024-06-01T00:00:00+02:00"));
        assertNotNull(KonaDate.parse("2024-06-01"));
        assertEquals("2024-06-01T00:00:00Z", KonaDate.parse("2024-06-01T00:00:00Z").toIso());
        assertEquals(ZoneOffset.UTC, KonaDate.parse("2024-06-01T00:00:00Z").timezone());
        assertNotNull(KonaDate.ofLocalDateTime(java.time.LocalDateTime.of(2024, 6, 1, 0, 0)));
        assertNotNull(KonaDate.ofLocalDateTime(java.time.LocalDateTime.of(2024, 6, 1, 0, 0), ZoneOffset.UTC));
        assertNotNull(KonaDate.ofLocalDate(java.time.LocalDate.of(2024, 6, 1)));
        assertNotNull(KonaDate.ofLocalDate(java.time.LocalDate.of(2024, 6, 1), ZoneOffset.UTC));
        assertNotNull(KonaDate.ofZonedDateTime(java.time.ZonedDateTime.parse("2024-06-01T00:00:00Z")));
        assertNotNull(KonaDate.ofOffsetDateTime(java.time.OffsetDateTime.parse("2024-06-01T00:00:00Z")));

        Translator t = new Translator("en");
        t.addLines("en", Map.of("solo", "only"));
        assertEquals("only", t.trans("solo"));
        assertEquals("only", t.get("solo"));
    }
}
