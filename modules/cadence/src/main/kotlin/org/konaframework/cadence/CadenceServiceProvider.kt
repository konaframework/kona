package org.konaframework.cadence

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

/**
 * Registers the [Database] manager (from `database.default` / `database.connections.*`,
 * falling back to `DB_*` env) as a container singleton aliased `db`, and makes it global on boot
 * so [DB] and [Cadence] models can reach it.
 */
class CadenceServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Database::class.java) { Database.fromConfig(app.config) }
        app.container.alias("db", Database::class.java.name)
    }

    override fun boot(app: Application) {
        app.make(Database::class.java).setAsGlobal()
    }
}
