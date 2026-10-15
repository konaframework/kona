package dev.kona.grind

import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.extract.Database
import dev.kona.extract.migrations.MigrationRegistry
import dev.kona.extract.migrations.Migrator

/**
 * `grind migrate`, `migrate:rollback [--step=N]`, `migrate:reset`, `migrate:status`.
 *
 * Boots the application in-process (from `build/classes`, via `--bootstrap=bootstrap.App`), then
 * uses the [MigrationRegistry] bound in its container and its [Database] — the bound instance, or
 * one built from the `database` config section. `--database=name` picks a named connection.
 */
class MigrateCommand(private val action: Action) {
    enum class Action { MIGRATE, ROLLBACK, RESET, STATUS }

    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        withApplication(options) { app, _ ->
            val registry = if (app.container.bound(MigrationRegistry::class.java)) {
                app.resolve(MigrationRegistry::class.java)
            } else {
                throw GrindException(
                    "No migrations registered: bind a MigrationRegistry in a service provider " +
                        "(app.container.instance(MigrationRegistry::class.java, MigrationRegistry(...)))",
                )
            }
            withDatabase(app) { database ->
                val name = options["database"]
                val connection = if (name == null) database.connection() else database.connection(name)
                Migrator.using(connection, registry) { migrator -> execute(migrator, options, io) }
            }
        }
        return 0
    }

    private fun execute(migrator: Migrator, options: Map<String, String>, io: GrindIO) {
        when (action) {
            Action.MIGRATE -> report(io, migrator.migrate(), "Migrated", "Nothing to migrate.")
            Action.ROLLBACK -> {
                val steps = options["step"]?.let {
                    it.toIntOrNull()?.takeIf { n -> n > 0 } ?: throw GrindException("--step must be a positive integer")
                } ?: 1
                report(io, migrator.rollback(steps), "Rolled back", "Nothing to rollback.")
            }
            Action.RESET -> report(io, migrator.reset(), "Rolled back", "Nothing to rollback.")
            Action.STATUS -> {
                val status = migrator.status()
                if (status.isEmpty()) {
                    io.warn("No migrations registered.")
                }
                status.forEach {
                    val state = if (it.ran) "Ran" else "Pending"
                    io.twoColumn(
                        "%-8s %-6s".format(state, it.batch ?: "").trimEnd(),
                        it.name,
                        width = 16,
                    )
                }
            }
        }
    }

    private fun report(io: GrindIO, names: List<String>, verb: String, empty: String) {
        if (names.isEmpty()) {
            io.info(empty)
            return
        }
        names.forEach { io.success("$verb: $it") }
    }

    companion object {
        /** Boots the app found at or above `--base` in-process and hands it to [block]. */
        internal fun withApplication(options: Map<String, String>, block: (Application, ClassLoader) -> Unit) {
            val start = Names.base(options)
            val root = ServeCommand.findProjectRoot(start)
                ?: throw GrindException("No Kona application found at $start. Create one with: kona new <name>")
            Kona.reset()
            val loader = AppLoader(root, options.getOrDefault("bootstrap", AppLoader.DEFAULT_BOOTSTRAP))
            val app = loader.create(arrayOf("--base=$root"))
            block(app, loader.classLoader!!)
        }

        /** The container's [Database] when bound, otherwise one built from config; closed when [block] returns. */
        internal fun withDatabase(app: Application, block: (Database) -> Unit) {
            val database = if (app.container.bound(Database::class.java)) {
                app.resolve(Database::class.java)
            } else {
                Database.fromConfig(app.config)
            }
            database.use(block)
        }
    }
}
