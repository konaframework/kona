package app.providers

import app.models.Project
import database.seeders.DatabaseSeeder
import org.konaframework.cadence.Database
import org.konaframework.cadence.migrations.MigrationRegistry
import org.konaframework.cadence.migrations.Migrator
import org.konaframework.cadence.seeding.Seeder
import org.konaframework.core.Application
import org.konaframework.core.Env
import org.konaframework.core.ServiceProvider

/**
 * Boots the Firefly desk sample database: run pending migrations, then seed demo
 * rows once when [Project] is empty (so Select.relationship has options to pick).
 *
 * Skip with `FIREFLY_SAMPLE_SEED=false`.
 */
class AppServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        // Bind application services into app.container here.
    }

    override fun boot(app: Application) {
        if (Env.getOrDefault("FIREFLY_SAMPLE_SEED", "true").equals("false", ignoreCase = true)) {
            return
        }
        val database = app.make(Database::class.java)
        database.setAsGlobal()
        val registry = app.make(MigrationRegistry::class.java)
        Migrator.using(database.connection(), registry) { migrator ->
            migrator.migrate()
            null
        }
        if (Project.query().count() == 0L) {
            Seeder.instantiate(DatabaseSeeder::class.java, app.container).run()
        }
    }
}
