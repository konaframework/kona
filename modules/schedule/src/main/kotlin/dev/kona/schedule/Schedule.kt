package dev.kona.schedule

import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

fun interface ScheduledTask {
    fun run()
}

data class ScheduledEvent(
    val name: String,
    val expression: CronExpression,
    val task: ScheduledTask,
)

/**
 * Registers cron-style tasks (Laravel Schedule subset).
 */
class Schedule {
    private val events = CopyOnWriteArrayList<ScheduledEvent>()

    fun command(name: String, expression: String, task: ScheduledTask): Schedule {
        events += ScheduledEvent(name, CronExpression(expression), task)
        return this
    }

    fun everyMinute(name: String, task: ScheduledTask): Schedule = command(name, "* * * * *", task)

    fun hourly(name: String, task: ScheduledTask): Schedule = command(name, "0 * * * *", task)

    fun dailyAt(name: String, hour: Int, task: ScheduledTask): Schedule =
        dailyAt(name, hour, 0, task)

    fun dailyAt(name: String, hour: Int, minute: Int, task: ScheduledTask): Schedule =
        command(name, "$minute $hour * * *", task)

    fun events(): List<ScheduledEvent> = events.toList()

    fun due(): List<ScheduledEvent> = due(LocalDateTime.now())

    fun due(now: LocalDateTime): List<ScheduledEvent> =
        events.filter { it.expression.matches(now) }

    fun runDue(): List<String> = runDue(LocalDateTime.now())

    fun runDue(now: LocalDateTime): List<String> {
        val ran = mutableListOf<String>()
        due(now).forEach {
            it.task.run()
            ran += it.name
        }
        return ran
    }
}
