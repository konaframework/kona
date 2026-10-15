package dev.kona.grind

import dev.kona.queue.QueueManager
import dev.kona.queue.Worker

/**
 * `grind queue:work [--connection=name] [--queue=name] [--max=N] [--bootstrap=…]`
 */
class QueueWorkCommand {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        MigrateCommand.withApplication(options) { app, _ ->
            if (!app.container.bound(QueueManager::class.java)) {
                throw GrindException(
                    "No QueueManager bound: register QueueServiceProvider " +
                        "(app.register(QueueServiceProvider()))",
                )
            }
            val manager = app.resolve(QueueManager::class.java)
            val connection = options["connection"] ?: manager.defaultConnection()
            val queue = options["queue"] ?: manager.defaultQueue(connection)
            val max = parseMax(options["max"])
            val processed = Worker(manager, connection, queue).run(max)
            io.success("Processed $processed job(s).")
        }
        return 0
    }

    private fun parseMax(raw: String?): Int {
        if (raw == null) return Int.MAX_VALUE
        val parsed = raw.toIntOrNull()
        return if (parsed != null && parsed > 0) parsed else Int.MAX_VALUE
    }
}
