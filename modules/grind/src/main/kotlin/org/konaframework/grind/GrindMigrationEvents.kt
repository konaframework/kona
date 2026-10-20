package org.konaframework.grind

import org.konaframework.cadence.migrations.MigrationEvents

/** Prints one dotted status row after each migration finishes. */
internal class GrindMigrationEvents(
    private val io: GrindIO,
    private val done: String = "DONE",
) : MigrationEvents {
    override fun finished(name: String, millis: Long) {
        io.taskLine(name, done, millis, ok = true)
    }
}
