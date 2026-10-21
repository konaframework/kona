package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.cadence.Database
import org.konaframework.cadence.migrations.MigrationDiscovery
import org.konaframework.cadence.migrations.MigrationRegistry
import org.konaframework.cadence.migrations.Migrator
import org.konaframework.support.console.ConsoleIO

/**
 * `grind migrate [--seed] [--class=…] [--seeder=…]`, `migrate:rollback [--step=N]`,
 * `migrate:reset`, `migrate:refresh [--step=N]`, `migrate:fresh [--drop-views] [--drop-types]`,
 * `migrate:status`, `migrate:install`, `db:wipe`.
 *
 * Boots the application in-process (from `build/classes`, via `--bootstrap=bootstrap.App`), then
 * uses the [MigrationRegistry] bound in its container, or migrations discovered from
 * `META-INF/kona/migrations`, plus its [Database]. `--database=name` picks a named connection.
 *
 * `--force` is required when `app.env` is `production`. `--seed` / `--seeder` / `--seeder=Class`
 * on [Action.MIGRATE], [Action.FRESH], and [Action.REFRESH] run seeders in the same process
 * before the database is closed (so in-memory and file H2 both see migrated tables).
 */
class MigrateCommand(private val action: Action) {
    enum class Action { MIGRATE, ROLLBACK, RESET, REFRESH, FRESH, STATUS, INSTALL, WIPE }

    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        GrindLogging.hushConsole(io.verbosity == Verbosity.VERBOSE)
        withApplication(options) { app, loader ->
            if (action.destructive) {
                confirmProduction(app, options)
            }
            val registry = if (action.needsMigrations) {
                resolveRegistry(app, loader)
            } else {
                resolveRegistryOrEmpty(app, loader)
            }
            withDatabase(app) { database ->
                val name = options["database"]
                val connection = if (name == null) database.connection() else database.connection(name)
                Migrator.using(connection, registry) { migrator -> execute(migrator, options, io) }
                val seeder = seederClass(options)
                if (action.seeds && seeder != null) {
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
                val steps = rollbackSteps(options)
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
            Action.REFRESH -> {
                val steps = if (options.containsKey("step")) rollbackSteps(options) else 0
                io.info("Refreshing the database.")
                migrator.refresh(steps, events)
            }
            Action.FRESH -> {
                io.info("Dropping all tables.")
                migrator.wipe(dropViews = wantsFlag(options, "drop-views"), dropTypes = wantsFlag(options, "drop-types"))
                io.info("Dropped all tables successfully.")
                if (migrator.pending().isEmpty()) {
                    io.info("Nothing to migrate.")
                } else {
                    io.info("Running migrations.")
                    migrator.migrate(events)
                }
            }
            Action.STATUS -> printStatus(migrator, io)
            Action.INSTALL -> {
                migrator.install()
                io.info("Migration table created successfully.")
            }
            Action.WIPE -> {
                io.info("Dropping all tables.")
                migrator.wipe(dropViews = wantsFlag(options, "drop-views"), dropTypes = wantsFlag(options, "drop-types"))
                io.info("Dropped all tables successfully.")
            }
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
        /** True when `--seed` / `--seeder` (or short `-s`) is present and truthy, including `--seeder=Class`. */
        internal fun wantsSeed(options: Map<String, String>): Boolean = seederClass(options) != null

        internal fun seederClass(options: Map<String, String>): String? {
            val raw = options["seeder"] ?: options["seed"] ?: return null
            if (!flagEnabled(raw)) {
                return null
            }
            return if (isBooleanToken(raw)) {
                options.getOrDefault("class", SeedCommand.DEFAULT_SEEDER)
            } else {
                raw
            }
        }

        internal fun wantsFlag(options: Map<String, String>, name: String): Boolean =
            flagEnabled(options[name])

        internal fun flagEnabled(value: String?): Boolean {
            if (value == null) {
                return false
            }
            return !isFalseToken(value)
        }

        private fun isBooleanToken(value: String): Boolean =
            value.isEmpty() ||
                value.equals("true", ignoreCase = true) ||
                value == "1" ||
                value.equals("yes", ignoreCase = true)

        private fun isFalseToken(value: String): Boolean =
            value.equals("false", ignoreCase = true) ||
                value == "0" ||
                value.equals("no", ignoreCase = true)

        internal fun rollbackSteps(options: Map<String, String>): Int {
            val raw = options["step"] ?: return 1
            return raw.toIntOrNull()?.takeIf { n -> n > 0 }
                ?: throw GrindException("--step must be a positive integer")
        }

        internal fun confirmProduction(app: Application, options: Map<String, String>) {
            if (wantsFlag(options, "force")) {
                return
            }
            val env = app.config.getString("app.env", "local")
            if (env.equals("production", ignoreCase = true)) {
                throw GrindException("Command cancelled; pass --force to run in production.")
            }
        }

        internal fun resolveRegistry(app: Application, loader: ClassLoader): MigrationRegistry {
            val registry = resolveRegistryOrEmpty(app, loader)
            if (registry.names.isEmpty()) {
                throw GrindException(
                    "No migrations registered. Run grind make:migration (auto-registers in " +
                        "${MigrationDiscovery.RESOURCE}), or bind a MigrationRegistry in a service provider.",
                )
            }
            return registry
        }

        internal fun resolveRegistryOrEmpty(app: Application, loader: ClassLoader): MigrationRegistry {
            if (app.container.bound(MigrationRegistry::class.java)) {
                return app.resolve(MigrationRegistry::class.java)
            }
            return MigrationDiscovery.loadRegistry(loader)
        }

        /** Boots the app found at or above `--base` in-process and hands it to [block]. */
        internal fun withApplication(options: Map<String, String>, block: (Application, ClassLoader) -> Unit) {
            val start = Names.base(options)
            val root = ServeCommand.findProjectRoot(start)
                ?: throw GrindException("No Kona application found at $start. Create one with: kona new")
            JdbcDriverInstaller.ensure(root, options, GrindIO.fromOptions(options))
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

private val MigrateCommand.Action.needsMigrations: Boolean
    get() = this == MigrateCommand.Action.MIGRATE ||
        this == MigrateCommand.Action.ROLLBACK ||
        this == MigrateCommand.Action.RESET ||
        this == MigrateCommand.Action.REFRESH

private val MigrateCommand.Action.seeds: Boolean
    get() = this == MigrateCommand.Action.MIGRATE ||
        this == MigrateCommand.Action.FRESH ||
        this == MigrateCommand.Action.REFRESH

private val MigrateCommand.Action.destructive: Boolean
    get() = this != MigrateCommand.Action.STATUS
