package io.konaframework.grind

import io.konaframework.queue.QueueManager
import io.konaframework.queue.Worker
import io.konaframework.queue.WorkerOptions

/**
 * `grind queue:work` — process queued jobs.
 *
 * Flags: `--connection` `--queue` `--max` `--once` `--stop-when-empty`
 * `--timeout` `--memory` `--tries` `--backoff` `--sleep` `--daemon` `--bootstrap`
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
            val workerOptions = WorkerOptions(
                maxJobs = parsePositive(options["max"], Int.MAX_VALUE),
                once = flag(options, "once"),
                stopWhenEmpty = !flag(options, "daemon") &&
                    (flag(options, "stop-when-empty") || !options.containsKey("sleep")),
                timeoutSeconds = parseLong(options["timeout"], 60),
                memoryMb = parsePositive(options["memory"], 0),
                maxTries = options["tries"]?.toIntOrNull(),
                backoffSeconds = options["backoff"]?.toLongOrNull(),
                sleepSeconds = parseLong(options["sleep"], 3),
            )
            val processed = Worker(manager, connection, queue, workerOptions).run()
            io.success("Processed $processed job(s).")
        }
        return 0
    }

    private fun flag(options: Map<String, String>, name: String): Boolean =
        options.containsKey(name) && options[name] != "false"

    private fun parsePositive(raw: String?, default: Int): Int {
        if (raw == null) return default
        val parsed = raw.toIntOrNull()
        return if (parsed != null && parsed > 0) parsed else default
    }

    private fun parseLong(raw: String?, default: Long): Long {
        if (raw == null) return default
        val parsed = raw.toLongOrNull()
        return if (parsed != null && parsed >= 0) parsed else default
    }
}
