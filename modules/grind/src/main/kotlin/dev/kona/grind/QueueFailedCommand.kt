package dev.kona.grind

import dev.kona.queue.FailedJobProvider
import dev.kona.queue.QueueManager

/**
 * `grind queue:failed` — list failed jobs.
 * `grind queue:retry [id|--all]` — remove from failed list (re-dispatch is app-specific).
 * `grind queue:flush` — clear failed jobs.
 */
class QueueFailedCommand(private val action: Action) {
    enum class Action { LIST, RETRY, FLUSH }

    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        MigrateCommand.withApplication(options) { app, _ ->
            val failed = failedProvider(app)
            when (action) {
                Action.LIST -> {
                    val jobs = failed.all()
                    if (jobs.isEmpty()) {
                        println("No failed jobs.")
                    } else {
                        jobs.forEach {
                            println("${it.id}  ${it.queue}  ${it.payload.displayName}  ${it.exception}")
                        }
                    }
                }
                Action.FLUSH -> {
                    failed.flush()
                    println("Failed jobs cleared.")
                }
                Action.RETRY -> {
                    if (options.containsKey("all")) {
                        val ids = failed.all().map { it.id }
                        ids.forEach { failed.forget(it) }
                        println("Forgot ${ids.size} failed job(s).")
                    } else {
                        val id = positional.firstOrNull()
                            ?: throw GrindException("Usage: grind queue:retry <id>|--all")
                        if (!failed.forget(id)) {
                            throw GrindException("Failed job [$id] not found.")
                        }
                        println("Forgot failed job $id.")
                    }
                }
            }
        }
        return 0
    }

    private fun failedProvider(app: dev.kona.core.Application): FailedJobProvider {
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
