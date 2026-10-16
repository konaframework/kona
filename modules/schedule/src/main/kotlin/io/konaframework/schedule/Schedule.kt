package io.konaframework.schedule

import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

fun interface ScheduledTask {
    fun run()
}

/** A registered schedule entry with fluent modifiers. */
class ScheduledEvent(
    val name: String,
    val expression: CronExpression,
    val task: ScheduledTask,
) {
    @Volatile
    var withoutOverlapping: Boolean = false
        private set

    @Volatile
    var overlapExpiresMinutes: Long = 1440
        private set

    @Volatile
    var onOneServer: Boolean = false
        private set

    @Volatile
    var runInBackground: Boolean = false
        private set

    fun withoutOverlapping(expiresMinutes: Long = 1440): ScheduledEvent {
        withoutOverlapping = true
        overlapExpiresMinutes = expiresMinutes
        return this
    }

    fun onOneServer(): ScheduledEvent {
        onOneServer = true
        withoutOverlapping = true
        return this
    }

    fun runInBackground(): ScheduledEvent {
        runInBackground = true
        return this
    }
}

/** Registers cron-style tasks. */
class Schedule(
    private val mutex: ScheduleMutex = ScheduleMutex(),
) {
    constructor(basePath: Path) : this(ScheduleMutex(basePath))

    private val events = CopyOnWriteArrayList<ScheduledEvent>()

    fun command(name: String, expression: String, task: ScheduledTask): ScheduledEvent {
        val event = ScheduledEvent(name, CronExpression(expression), task)
        events += event
        return event
    }

    fun everyMinute(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "* * * * *", task)

    fun everyFiveMinutes(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "*/5 * * * *", task)

    fun everyTenMinutes(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "*/10 * * * *", task)

    fun everyFifteenMinutes(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "*/15 * * * *", task)

    fun everyThirtyMinutes(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "*/30 * * * *", task)

    fun hourly(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "0 * * * *", task)

    fun everyTwoHours(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "0 */2 * * *", task)

    fun everyThreeHours(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "0 */3 * * *", task)

    fun everyFourHours(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "0 */4 * * *", task)

    fun everySixHours(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "0 */6 * * *", task)

    fun daily(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "0 0 * * *", task)

    fun dailyAt(name: String, hour: Int, task: ScheduledTask): ScheduledEvent =
        dailyAt(name, hour, 0, task)

    fun dailyAt(name: String, hour: Int, minute: Int, task: ScheduledTask): ScheduledEvent =
        command(name, "$minute $hour * * *", task)

    fun weekly(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "0 0 * * 0", task)

    fun monthly(name: String, task: ScheduledTask): ScheduledEvent =
        command(name, "0 0 1 * *", task)

    fun events(): List<ScheduledEvent> = events.toList()

    fun due(): List<ScheduledEvent> = due(LocalDateTime.now())

    fun due(now: LocalDateTime): List<ScheduledEvent> =
        events.filter { it.expression.matches(now) }

    fun runDue(): List<String> = runDue(LocalDateTime.now())

    fun runDue(now: LocalDateTime): List<String> {
        val ran = mutableListOf<String>()
        due(now).forEach { event ->
            if (runEvent(event)) {
                ran += event.name
            }
        }
        return ran
    }

    private fun runEvent(event: ScheduledEvent): Boolean {
        val needsMutex = event.withoutOverlapping || event.onOneServer
        val key = "schedule:${event.name}"
        if (needsMutex) {
            val expires = event.overlapExpiresMinutes.coerceAtLeast(1) * 60
            if (!mutex.acquire(key, expires, event.onOneServer)) {
                return false
            }
        }
        val run = {
            try {
                event.task.run()
            } finally {
                releaseMutex(needsMutex, key, event.onOneServer)
            }
        }
        if (event.runInBackground) {
            Thread.ofVirtual().name("schedule-${event.name}").start(run)
        } else {
            run()
        }
        return true
    }

    private fun releaseMutex(needsMutex: Boolean, key: String, onOneServer: Boolean) {
        if (!needsMutex) {
            return
        }
        mutex.release(key, onOneServer)
    }
}
