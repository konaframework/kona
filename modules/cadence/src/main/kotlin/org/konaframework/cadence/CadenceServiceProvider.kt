package org.konaframework.cadence

import org.konaframework.cadence.migrations.MigrationDiscovery
import org.konaframework.cadence.migrations.MigrationRegistry
import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

/**
 * Registers the [Database] manager (from `database.default` / `database.connections.*`,
 * falling back to `DB_*` env) as a container singleton aliased `db`, and makes it global on boot
 * so [DB] and [Cadence] models can reach it.
 *
 * When the app has not bound a [MigrationRegistry], loads migrations from
 * `META-INF/kona/migrations` (written by `grind make:migration`).
 */
class CadenceServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Database::class.java) { Database.fromConfig(app.config) }
        app.container.alias("db", Database::class.java.name)
        if (!app.container.bound(MigrationRegistry::class.java)) {
            app.container.singleton(MigrationRegistry::class.java) {
                MigrationDiscovery.loadRegistry(migrationClassLoader())
            }
        }
    }

    override fun boot(app: Application) {
        app.make(Database::class.java).setAsGlobal()
    }

    companion object {
        /** Prefer the thread context loader (app classes); fall back to the provider's loader. */
        internal fun migrationClassLoader(): ClassLoader =
            Thread.currentThread().contextClassLoader
                ?: CadenceServiceProvider::class.java.classLoader
    }
}
