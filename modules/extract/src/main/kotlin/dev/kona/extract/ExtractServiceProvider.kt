package dev.kona.extract

import dev.kona.core.Application
import dev.kona.core.ServiceProvider

/**
 * Registers the [Database] manager (from `database.default` / `database.connections.*`,
 * falling back to `DB_*` env) as a container singleton aliased `db`, and makes it global on boot
 * so [DB] and [Extract] models can reach it.
 */
class ExtractServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Database::class.java) { Database.fromConfig(app.config) }
        app.container.alias("db", Database::class.java.name)
    }

    override fun boot(app: Application) {
        app.make(Database::class.java).setAsGlobal()
    }
}
