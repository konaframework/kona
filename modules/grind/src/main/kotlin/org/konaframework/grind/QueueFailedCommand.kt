package org.konaframework.grind

import org.konaframework.queue.FailedJobProvider
import org.konaframework.queue.QueueManager

/**
 * `grind queue:failed` — list failed jobs.
 * `grind queue:retry [id|--all]` — re-queue failed payloads (attempts reset) then forget.
 * `grind queue:flush` — clear failed jobs.
 */
class QueueFailedCommand(private val action: Action) {
    enum class Action { LIST, RETRY, FLUSH }

    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        MigrateCommand.withApplication(options) { app, _ ->
            when (action) {
                Action.LIST -> {
                    val failed = failedProvider(app)
                    val jobs = failed.all()
                    if (jobs.isEmpty()) {
                        io.info("No failed jobs.")
                    } else {
                        jobs.forEach {
                            io.twoColumn(it.id, "${it.queue}  ${it.payload.displayName}  ${it.exception}", width = 12)
                        }
                    }
                }
                Action.FLUSH -> {
                    failedProvider(app).flush()
                    io.success("Failed jobs cleared.")
                }
                Action.RETRY -> {
                    val manager = queueManager(app)
                    val failed = manager.failed
                    val targets = if (options.containsKey("all")) {
                        failed.all()
                    } else {
                        val id = positional.firstOrNull()
                            ?: throw GrindException("Usage: grind queue:retry <id>|--all")
                        val job = failed.find(id)
                            ?: throw GrindException("Failed job [$id] not found.")
                        listOf(job)
                    }
                    targets.forEach { job ->
                        val reset = job.payload.copy(attempts = 0, availableAt = 0)
                        manager.connection(job.connection).release(reset, job.queue, 0)
                        failed.forget(job.id)
                    }
                    io.success("Retried ${targets.size} failed job(s).")
                }
            }
        }
        return 0
    }

    private fun queueManager(app: org.konaframework.core.Application): QueueManager {
        if (app.container.bound(QueueManager::class.java)) {
            return app.resolve(QueueManager::class.java)
        }
        throw GrindException(
            "No QueueManager bound: register QueueServiceProvider (app.register(QueueServiceProvider()))",
        )
    }

    private fun failedProvider(app: org.konaframework.core.Application): FailedJobProvider {
        if (app.container.bound(QueueManager::class.java)) {
            return app.resolve(QueueManager::class.java).failed
        }
        if (app.container.bound(FailedJobProvider::class.java)) {
            return app.resolve(FailedJobProvider::class.java)
        }
        throw GrindException(
            "No FailedJobProvider bound: register QueueServiceProvider",
        )
    }
}
