package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.cadence.Database
import org.konaframework.cadence.seeding.Seeder

/**
 * `grind db:seed [--class=database.seeders.DatabaseSeeder]` — boots the app in-process, makes its
 * database global for models, then builds the seeder through the app container and runs it.
 */
class SeedCommand {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        val name = options.getOrDefault("class", DEFAULT_SEEDER)
        MigrateCommand.withApplication(options) { app, loader ->
            MigrateCommand.withDatabase(app) { database ->
                runSeeder(app, loader, name, io, database)
            }
        }
        return 0
    }

    companion object {
        const val DEFAULT_SEEDER = "database.seeders.DatabaseSeeder"

        /**
         * Resolve [name] on [loader], bind [database] as global, run the seeder through the app
         * container, and print progress. Shared by `db:seed` and `migrate --seed`.
         */
        internal fun runSeeder(
            app: Application,
            loader: ClassLoader,
            name: String,
            io: GrindIO,
            database: Database,
        ) {
            val type = try {
                Class.forName(name, true, loader)
            } catch (ex: ClassNotFoundException) {
                throw GrindException("Seeder class [$name] not found — compile the app first (./gradlew classes)")
            }
            if (!Seeder::class.java.isAssignableFrom(type)) {
                throw GrindException("[$name] does not extend org.konaframework.cadence.seeding.Seeder")
            }
            database.setAsGlobal()
            val seeder = Seeder.instantiate(type.asSubclass(Seeder::class.java), app.container)
            io.info("Seeding: ${type.simpleName}")
            seeder.run()
            seeder.called.forEach { io.info("Seeding: $it") }
            io.success("Database seeding completed successfully.")
        }
    }
}
