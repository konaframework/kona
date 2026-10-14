package dev.kona.grind

import dev.kona.schedule.Schedule
import java.time.LocalDateTime

/**
 * `grind schedule:run` / `schedule:list`
 */
class ScheduleRunCommand(private val action: Action) {
    enum class Action { RUN, LIST }

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
                        io.writeln("No scheduled events.")
                    } else {
                        events.forEach { io.writeln(it.name) }
                    }
                }
                Action.RUN -> {
                    val ran = schedule.runDue(LocalDateTime.now())
                    if (ran.isEmpty()) {
                        io.writeln("No scheduled tasks were ready.")
                    } else {
                        ran.forEach { io.writeln("Running: $it") }
                    }
                    io.verbose("schedule:run finished with ${ran.size} task(s)")
                }
            }
        }
        return 0
    }
}
