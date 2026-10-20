package org.konaframework.support.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.support.date.KonaDate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JavaJsonTest {
    @Test
    void stringifyJavaTimeAndKonaDate() throws Exception {
        assertEquals("\"2024-06-15T12:00:00Z\"", Json.stringify(Instant.parse("2024-06-15T12:00:00Z")));
        assertEquals("\"2024-06-15\"", Json.stringify(LocalDate.of(2024, 6, 15)));
        assertEquals("\"2024-06-15T08:30:00\"", Json.stringify(LocalDateTime.of(2024, 6, 15, 8, 30, 0)));
        assertEquals(
            "\"2024-06-15T12:00:00+02:00\"",
            Json.stringify(OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.ofHours(2)))
        );
        String zoned = Json.stringify(ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC));
        assertTrue(zoned.contains("2024-06-15T12:00:00"));

        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("created_at", Instant.parse("2024-06-15T12:00:00Z"));
        assertEquals("[{\"created_at\":\"2024-06-15T12:00:00Z\"}]", Json.stringify(List.of(row)));

        KonaDate date = KonaDate.parse("2024-06-15T12:00:00Z");
        assertEquals("\"2024-06-15T12:00:00Z\"", Json.stringify(date));
        assertEquals(date, Json.mapper.readValue("\"2024-06-15T12:00:00Z\"", KonaDate.class));
        assertTrue(Json.mapper.writeValueAsString(date).contains("2024-06-15T12:00:00Z"));
    }
}
