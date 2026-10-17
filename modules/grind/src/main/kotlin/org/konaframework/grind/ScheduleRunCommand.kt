package org.konaframework.grind

import org.konaframework.schedule.Schedule
import java.time.LocalDateTime

/** `grind schedule:run` / `schedule:list` / `schedule:work` */
class ScheduleRunCommand(private val action: Action) {
    enum class Action { RUN, LIST, WORK }

    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        MigrateCommand.withApplication(options) { app, _ ->
            if (!app.container.bound(Schedule::class.java)) {
                throw GrindException(
                    "No Schedule bound: register ScheduleServiceProvider " +
                        "(app.register(ScheduleServiceProvider()))",
                )
            }
            val schedule = app.resolve(Schedule::class.java)
            when (action) {
                Action.LIST -> {
                    val events = schedule.events()
                    if (events.isEmpty()) {
                        io.info("No scheduled events.")
                    } else {
                        io.title("Scheduled events")
                        events.forEach { event ->
                            val flags = buildList {
                                if (event.withoutOverlapping) add("no-overlap")
                                if (event.onOneServer) add("one-server")
                                if (event.runInBackground) add("background")
                            }.joinToString(", ")
                            io.twoColumn(event.name, flags.ifEmpty { "" })
                        }
                    }
                }
                Action.RUN -> {
                    val ran = schedule.runDue(LocalDateTime.now())
                    if (ran.isEmpty()) io.info("No scheduled tasks were ready.")
                    else ran.forEach { io.success("Running: $it") }
                    io.verbose("schedule:run finished with ${ran.size} task(s)")
                }
                Action.WORK -> {
                    val maxTicks = workMaxTicks(options)
                    val sleepSeconds = workSleepSeconds(options)
                    var ticks = 0
                    io.info("schedule:work started (sleep=${sleepSeconds}s)")
                    while (ticks < maxTicks) {
                        val ran = schedule.runDue(LocalDateTime.now())
                        ran.forEach { io.success("Running: $it") }
                        ticks += 1
                        if (ticks < maxTicks) {
                            try {
                                Thread.sleep(sleepSeconds * 1000)
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            }
                        }
                    }
                    io.verbose("schedule:work finished after $ticks tick(s)")
                }
            }
        }
        return 0
    }

    internal companion object {
        fun workMaxTicks(options: Map<String, String>): Int =
            options["max"]?.toIntOrNull()?.coerceAtLeast(1) ?: Int.MAX_VALUE

        fun workSleepSeconds(options: Map<String, String>): Long =
            options["sleep"]?.toLongOrNull()?.coerceAtLeast(1) ?: 60L
    }
}
