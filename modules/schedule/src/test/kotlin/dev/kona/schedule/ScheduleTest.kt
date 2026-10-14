package dev.kona.schedule

import dev.kona.core.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

class ScheduleTest {
    @Test
    fun cronMatchesAndHelpers(@TempDir base: Path) {
        val cron = CronExpression("*/5 13 1 6 1")
        assertTrue(cron.matches(LocalDateTime.of(2026, 6, 1, 13, 0)))
        assertTrue(cron.matches(LocalDateTime.of(2026, 6, 1, 13, 5)))
        assertFalse(cron.matches(LocalDateTime.of(2026, 6, 1, 13, 6)))
        assertFalse(cron.matches(LocalDateTime.of(2026, 6, 1, 14, 0)))
        assertFalse(cron.matches(LocalDateTime.of(2026, 6, 2, 13, 0)))
        assertFalse(cron.matches(LocalDateTime.of(2026, 7, 1, 13, 0)))
        assertTrue(CronExpression("* * * * *").matches(LocalDateTime.now()))
        assertTrue(CronExpression("0 0 * * 0").matches(LocalDateTime.of(2026, 1, 4, 0, 0)))
        assertThrows(IllegalArgumentException::class.java) { CronExpression("* *") }
        assertThrows(IllegalArgumentException::class.java) { CronExpression("*/0 * * * *") }
        assertThrows(IllegalArgumentException::class.java) { CronExpression("99 * * * *") }

        val n = AtomicInteger()
        val schedule = Schedule()
            .everyMinute("tick") { n.incrementAndGet() }
            .hourly("hour") { n.incrementAndGet() }
            .dailyAt("noon", 12, 0) { n.incrementAndGet() }
            .dailyAt("midnight", 0) { n.incrementAndGet() }
            .command("custom", "30 8 * * *") { n.incrementAndGet() }
        assertEquals(5, schedule.events().size)

        val noon = LocalDateTime.of(2026, 1, 1, 12, 0)
        val due = schedule.due(noon)
        assertTrue(due.any { it.name == "tick" })
        assertTrue(due.any { it.name == "noon" })
        assertTrue(due.any { it.name == "hour" })
        assertFalse(due.any { it.name == "custom" })

        val ran = schedule.runDue(noon)
        assertTrue(ran.contains("tick"))
        assertTrue(ran.contains("noon"))
        assertFalse(ran.contains("custom"))
        assertTrue(schedule.runDue(LocalDateTime.of(2026, 1, 1, 8, 30)).contains("custom"))
        assertTrue(schedule.runDue(LocalDateTime.of(2026, 1, 1, 0, 0)).contains("midnight"))

        schedule.due()
        schedule.runDue()
        Schedule().dailyAt("only-hour", 15) { }.runDue(LocalDateTime.of(2026, 1, 1, 15, 0))

        val app = Application(base)
        app.register(ScheduleServiceProvider()).boot()
        assertTrue(app.container.bound(Schedule::class.java))
    }
}
