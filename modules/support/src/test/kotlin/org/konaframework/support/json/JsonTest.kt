package org.konaframework.support.json

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.support.date.KonaDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class JsonTest {
    @Test
    fun javaTimeAndKonaDateAreIso8601Strings() {
        assertSame(Json.mapper, Json.mapper)
        assertEquals("\"2024-06-15T12:00:00Z\"", Json.stringify(Instant.parse("2024-06-15T12:00:00Z")))
        assertEquals("\"2024-06-15\"", Json.stringify(LocalDate.of(2024, 6, 15)))
        assertEquals("\"2024-06-15T12:00:00\"", Json.stringify(LocalDateTime.of(2024, 6, 15, 12, 0, 0)))
        assertEquals(
            "\"2024-06-15T12:00:00Z\"",
            Json.stringify(OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)),
        )
        val zoned = Json.stringify(ZonedDateTime.of(2024, 6, 15, 15, 0, 0, 0, ZoneOffset.ofHours(3)))
        assertTrue(zoned.contains("2024-06-15T15:00:00+03:00"))

        val date = KonaDate.ofInstant(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC)
        assertEquals("\"2024-06-15T12:00:00Z\"", Json.stringify(date))
        assertEquals(
            date,
            Json.mapper.readValue("\"2024-06-15T12:00:00Z\"", KonaDate::class.java),
        )

        val users = listOf(
            linkedMapOf<String, Any?>(
                "id" to 1,
                "created_at" to Instant.parse("2024-06-15T12:00:00Z"),
            ),
        )
        assertEquals("""[{"id":1,"created_at":"2024-06-15T12:00:00Z"}]""", Json.stringify(users))
    }
}
