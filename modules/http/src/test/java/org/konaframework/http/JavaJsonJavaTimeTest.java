package org.konaframework.http;

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
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaJsonJavaTimeTest {
    @Test
    void getApiUsersSerializesInstantCreatedAt() {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("created_at", Instant.parse("2024-06-15T12:00:00Z"));
        Router router = new Router();
        router.get("/api/users", request -> Response.json(List.of(row)));
        Response response = new HttpKernel(router).handle(Request.get("/api/users"));
        assertEquals(200, response.getStatus());
        assertEquals("[{\"id\":1,\"created_at\":\"2024-06-15T12:00:00Z\"}]", response.getBody());
    }

    @Test
    void jsonAcceptsJavaTimeAndKonaDate() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instant", Instant.parse("2024-01-02T03:04:05Z"));
        payload.put("localDate", LocalDate.of(2024, 1, 2));
        payload.put("localDateTime", LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        payload.put("offset", OffsetDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC));
        payload.put("zoned", ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC));
        payload.put("kona", KonaDate.parse("2024-01-02T03:04:05Z"));
        String body = Response.json(payload).getBody();
        assertTrue(body.contains("\"instant\":\"2024-01-02T03:04:05Z\""));
        assertTrue(body.contains("\"localDate\":\"2024-01-02\""));
        assertTrue(body.contains("\"localDateTime\":\"2024-01-02T03:04:05\""));
        assertTrue(body.contains("\"kona\":\"2024-01-02T03:04:05Z\""));
    }
}
