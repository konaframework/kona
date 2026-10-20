package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.cadence.Database
import org.konaframework.cadence.migrations.MigrationDiscovery
import org.konaframework.cadence.migrations.MigrationRegistry
import org.konaframework.cadence.migrations.Migrator
import org.konaframework.support.console.ConsoleIO

/**
 * `grind migrate [--seed] [--class=…]`, `migrate:rollback [--step=N]`, `migrate:reset`,
 * `migrate:status`.
 *
 * Boots the application in-process (from `build/classes`, via `--bootstrap=bootstrap.App`), then
 * uses the [MigrationRegistry] bound in its container, or migrations discovered from
 * `META-INF/kona/migrations`, plus its [Database]. `--database=name` picks a named connection.
 *
 * With `--seed` / `--seeder` on [Action.MIGRATE] only, runs seeders in the same process before the
 * database is closed (so in-memory and file H2 both see migrated tables).
 */
class MigrateCommand(private val action: Action) {
    enum class Action { MIGRATE, ROLLBACK, RESET, STATUS }

    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        GrindLogging.hushConsole(io.verbosity == Verbosity.VERBOSE)
        withApplication(options) { app, loader ->
            val registry = resolveRegistry(app, loader)
            withDatabase(app) { database ->
                val name = options["database"]
                val connection = if (name == null) database.connection() else database.connection(name)
                Migrator.using(connection, registry) { migrator -> execute(migrator, options, io) }
                if (action == Action.MIGRATE && wantsSeed(options)) {
                    val seeder = options.getOrDefault("class", SeedCommand.DEFAULT_SEEDER)
                    SeedCommand.runSeeder(app, loader, seeder, io, database)
                }
            }
        }
        return 0
    }

    private fun execute(migrator: Migrator, options: Map<String, String>, io: GrindIO) {
        val events = GrindMigrationEvents(io)
        when (action) {
            Action.MIGRATE -> {
                if (migrator.pending().isEmpty()) {
                    io.info("Nothing to migrate.")
                } else {
                    io.info("Running migrations.")
                    migrator.migrate(events)
                }
            }
            Action.ROLLBACK -> {
                val steps = options["step"]?.let {
                    it.toIntOrNull()?.takeIf { n -> n > 0 } ?: throw GrindException("--step must be a positive integer")
                } ?: 1
                if (migrator.ran().isEmpty()) {
                    io.info("Nothing to rollback.")
                } else {
                    io.info("Rolling back migrations.")
                    migrator.rollback(steps, events)
                }
            }
            Action.RESET -> {
                if (migrator.ran().isEmpty()) {
                    io.info("Nothing to rollback.")
                } else {
                    io.info("Rolling back migrations.")
                    migrator.reset(events)
                }
            }
            Action.STATUS -> printStatus(migrator, io)
        }
    }

    private fun printStatus(migrator: Migrator, io: GrindIO) {
        val status = migrator.status()
        if (status.isEmpty()) {
            io.warn("No migrations registered.")
            return
        }
        io.title("Migration status")
        io.table(
            listOf("Status", "Batch", "Migration"),
            status.map { row ->
                val state = if (row.ran) io.paint(ConsoleIO.GREEN, "Ran") else io.paint(ConsoleIO.YELLOW, "Pending")
                val batch = row.batch
                val batchLabel = if (batch == null) "—" else batch.toString()
                listOf(state, batchLabel, row.name)
            },
        )
    }

    companion object {
        /** True when `--seed` / `--seeder` (or short `-s`) is present and truthy. */
        internal fun wantsSeed(options: Map<String, String>): Boolean {
            val value = options["seeder"] ?: options["seed"] ?: return false
            return value.isEmpty() ||
                value.equals("true", ignoreCase = true) ||
                value == "1" ||
                value.equals("yes", ignoreCase = true)
        }

        internal fun resolveRegistry(app: Application, loader: ClassLoader): MigrationRegistry {
            if (app.container.bound(MigrationRegistry::class.java)) {
                return app.resolve(MigrationRegistry::class.java)
            }
            val discovered = MigrationDiscovery.loadRegistry(loader)
            if (discovered.names.isEmpty()) {
                throw GrindException(
                    "No migrations registered. Run grind make:migration (auto-registers in " +
                        "${MigrationDiscovery.RESOURCE}), or bind a MigrationRegistry in a service provider.",
                )
            }
            return discovered
        }

        /** Boots the app found at or above `--base` in-process and hands it to [block]. */
        internal fun withApplication(options: Map<String, String>, block: (Application, ClassLoader) -> Unit) {
            val start = Names.base(options)
            val root = ServeCommand.findProjectRoot(start)
                ?: throw GrindException("No Kona application found at $start. Create one with: kona new <name>")
            Kona.reset()
            val verbose = GrindLogging.wantsVerbose(options)
            GrindLogging.hushConsole(verbose)
            val loader = AppLoader(root, options.getOrDefault("bootstrap", AppLoader.DEFAULT_BOOTSTRAP))
            val app = loader.create(arrayOf("--base=$root"))
            GrindLogging.hushConsole(verbose)
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
