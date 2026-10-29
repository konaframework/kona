package app.providers;

import app.models.Project;
import database.seeders.DatabaseSeeder;
import org.konaframework.cadence.Database;
import org.konaframework.cadence.migrations.MigrationRegistry;
import org.konaframework.cadence.migrations.Migrator;
import org.konaframework.cadence.seeding.Seeder;
import org.konaframework.core.Application;
import org.konaframework.core.Env;
import org.konaframework.core.ServiceProvider;

/**
 * Boots the Firefly desk sample database: pending migrations, then seed once when
 * projects are empty. Skip with {@code FIREFLY_SAMPLE_SEED=false}.
 */
public class AppServiceProvider implements ServiceProvider {
    @Override
    public void register(Application app) {
        // Bind application services into app.getContainer() here.
    }

    @Override
    public void boot(Application app) {
        if ("false".equalsIgnoreCase(Env.getOrDefault("FIREFLY_SAMPLE_SEED", "true"))) {
            return;
        }
        Database database = app.make(Database.class);
        database.setAsGlobal();
        MigrationRegistry registry = app.make(MigrationRegistry.class);
        Migrator.using(database.connection(), registry, Migrator.DEFAULT_TABLE, migrator -> {
            migrator.migrate();
            return null;
        });
        if (Project.QUERY.query().count() == 0L) {
            Seeder.instantiate(DatabaseSeeder.class, app.getContainer()).run();
        }
    }
}
