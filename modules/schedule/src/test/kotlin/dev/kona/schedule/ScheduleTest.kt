package dev.kona.schedule

import dev.kona.core.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ScheduleTest {
    @Test
    fun cronMatchesListsRangesAndHelpers(@TempDir base: Path) {
        val cron = CronExpression("*/5 13 1 6 1")
        assertTrue(cron.matches(LocalDateTime.of(2026, 6, 1, 13, 0)))
        assertTrue(cron.matches(LocalDateTime.of(2026, 6, 1, 13, 5)))
        assertFalse(cron.matches(LocalDateTime.of(2026, 6, 1, 13, 6)))
        assertTrue(CronExpression("* * * * *").matches(LocalDateTime.now()))
        assertTrue(CronExpression("0 0 * * 0").matches(LocalDateTime.of(2026, 1, 4, 0, 0)))
        assertTrue(CronExpression("0,30 * * * *").matches(LocalDateTime.of(2026, 1, 1, 10, 0)))
        assertTrue(CronExpression("0,30 * * * *").matches(LocalDateTime.of(2026, 1, 1, 10, 30)))
        assertFalse(CronExpression("0,30 * * * *").matches(LocalDateTime.of(2026, 1, 1, 10, 15)))
        assertTrue(CronExpression("10-12 * * * *").matches(LocalDateTime.of(2026, 1, 1, 0, 11)))
        assertFalse(CronExpression("10-12 * * * *").matches(LocalDateTime.of(2026, 1, 1, 0, 13)))
        assertThrows(IllegalArgumentException::class.java) { CronExpression("* *") }
        assertThrows(IllegalArgumentException::class.java) { CronExpression("*/0 * * * *") }
        assertThrows(IllegalArgumentException::class.java) { CronExpression("99 * * * *") }
        assertThrows(IllegalArgumentException::class.java) { CronExpression("12-10 * * * *") }

        val n = AtomicInteger()
        val schedule = Schedule()
        schedule.everyMinute("tick") { n.incrementAndGet() }
        schedule.hourly("hour") { n.incrementAndGet() }
        schedule.dailyAt("noon", 12, 0) { n.incrementAndGet() }
        schedule.dailyAt("midnight", 0) { n.incrementAndGet() }
        schedule.command("custom", "30 8 * * *") { n.incrementAndGet() }
        schedule.everyFiveMinutes("five") { }
        schedule.everyTenMinutes("ten") { }
        schedule.everyFifteenMinutes("fifteen") { }
        schedule.everyThirtyMinutes("thirty") { }
        schedule.everyTwoHours("two") { }
        schedule.everyThreeHours("three") { }
        schedule.everyFourHours("four") { }
        schedule.everySixHours("six") { }
        schedule.daily("daily") { }
        schedule.weekly("weekly") { }
        schedule.monthly("monthly") { }
        assertEquals(16, schedule.events().size)

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
        val only = Schedule()
        only.dailyAt("only-hour", 15) { }
        only.runDue(LocalDateTime.of(2026, 1, 1, 15, 0))

        val app = Application(base)
        app.register(ScheduleServiceProvider()).boot()
        assertTrue(app.container.bound(Schedule::class.java))
        assertNotNull(app.resolve(Schedule::class.java))
    }

    @Test
    fun withoutOverlappingOnOneServerAndBackground(@TempDir base: Path) {
        val latch = CountDownLatch(1)
        val schedule = Schedule(base)
        schedule.everyMinute("locked") { Thread.sleep(50) }.withoutOverlapping(1)
        schedule.everyMinute("server") { }.onOneServer()
        schedule.everyMinute("bg") { latch.countDown() }.runInBackground()
        val bgLock = CountDownLatch(1)
        schedule.everyMinute("bg-lock") {
            bgLock.countDown()
        }.withoutOverlapping().runInBackground()

        val now = LocalDateTime.of(2026, 1, 1, 0, 0)
        val first = schedule.runDue(now)
        assertTrue(first.contains("locked"))
        assertTrue(first.contains("server"))
        assertTrue(first.contains("bg"))
        assertTrue(first.contains("bg-lock"))
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(bgLock.await(2, TimeUnit.SECONDS))

        val mutex = ScheduleMutex(base)
        assertTrue(mutex.acquire("schedule:manual", 60, false))
        assertFalse(mutex.acquire("schedule:manual", 60, false))
        mutex.release("schedule:manual", false)
        // Expired local lock can be re-acquired after expireLocal sweeps
        assertTrue(mutex.acquire("schedule:expire", 1, false))
        Thread.sleep(1100)
        assertTrue(mutex.acquire("schedule:expire", 60, false))
        mutex.release("schedule:expire", false)
        // coerceAtLeast(1) on zero expiry
        assertTrue(mutex.acquire("schedule:zero", 0, false))
        mutex.release("schedule:zero", false)
        assertTrue(mutex.acquire("schedule:file", 60, true))
        assertFalse(mutex.acquire("schedule:file", 60, true))
        mutex.release("schedule:file", true)
        // Expired / corrupt file lock
        val stale = base.resolve("storage").resolve("framework").resolve("schedule-schedule_stale.lock")
        java.nio.file.Files.createDirectories(stale.parent)
        java.nio.file.Files.writeString(stale, "1")
        assertTrue(mutex.acquire("schedule:stale", 60, true))
        mutex.release("schedule:stale", true)
        java.nio.file.Files.writeString(stale, "not-a-number")
        assertTrue(mutex.acquire("schedule:stale", 60, true))
        mutex.release("schedule:stale", true)
        assertTrue(mutex.acquire("schedule:file", 60, true))
        mutex.release("schedule:file", true)

        // release best-effort when lock path is a non-empty directory
        val badLock = base.resolve("storage").resolve("framework").resolve("schedule-schedule_bad.lock")
        java.nio.file.Files.createDirectories(badLock)
        java.nio.file.Files.writeString(badLock.resolve("child"), "x")
        mutex.release("schedule:bad", true)

        // Skip when mutex held
        assertTrue(mutex.acquire("schedule:held", 60, false))
        val skip = Schedule(mutex)
        skip.everyMinute("held") { }.withoutOverlapping()
        assertFalse(skip.runDue(now).contains("held"))
        mutex.release("schedule:held", false)
    }
}

