package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.support.date.KonaDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class JsonJavaTimeTest {
    @Test
    fun getApiUsersSerializesInstantCreatedAt() {
        val created = Instant.parse("2024-06-15T12:00:00Z")
        val router = Router()
        router.get("/api/users") {
            Response.json(
                listOf(
                    linkedMapOf<String, Any?>(
                        "id" to 1,
                        "name" to "Ada",
                        "created_at" to created,
                    ),
                ),
            )
        }
        val response = HttpKernel(router).handle(Request.get("/api/users"))
        assertEquals(200, response.status)
        assertEquals(
            """[{"id":1,"name":"Ada","created_at":"2024-06-15T12:00:00Z"}]""",
            response.body,
        )
    }

    @Test
    fun jsonPayloadAcceptsJavaTimeAndKonaDate() {
        val payload = linkedMapOf<String, Any?>(
            "instant" to Instant.parse("2024-01-02T03:04:05Z"),
            "localDate" to LocalDate.of(2024, 1, 2),
            "localDateTime" to LocalDateTime.of(2024, 1, 2, 3, 4, 5),
            "offset" to OffsetDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
            "zoned" to ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
            "kona" to KonaDate.parse("2024-01-02T03:04:05Z"),
        )
        val body = Response.json(payload).body
        assertTrue(body.contains("\"instant\":\"2024-01-02T03:04:05Z\""))
        assertTrue(body.contains("\"localDate\":\"2024-01-02\""))
        assertTrue(body.contains("\"localDateTime\":\"2024-01-02T03:04:05\""))
        assertTrue(body.contains("\"offset\":\"2024-01-02T03:04:05Z\""))
        assertTrue(body.contains("\"zoned\":\"2024-01-02T03:04:05Z\""))
        assertTrue(body.contains("\"kona\":\"2024-01-02T03:04:05Z\""))
    }

    @Test
    fun jsonResourceSerializesInstantCreatedAt() {
        JsonResource.flushState()
        class UserResource : JsonResource(null) {
            override fun toArray(request: Request): Map<String, Any?> =
                mapOf(
                    "id" to 1,
                    "created_at" to Instant.parse("2024-06-15T12:00:00Z"),
                )
        }
        val response = UserResource().toResponse(Request.get("/api/users"))
        assertTrue(response.body.contains("\"created_at\":\"2024-06-15T12:00:00Z\""))
        JsonResource.flushState()
    }
}
