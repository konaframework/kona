package org.konaframework.cadence.migrations

import org.konaframework.cadence.CadenceException
import org.konaframework.cadence.schema.Schema

/** One reversible schema change. [up] applies it; [down] undoes exactly what [up] did. */
interface Migration {
    fun up(schema: Schema)

    fun down(schema: Schema)
}

/** Start/finish of each migration [Migrator] applies or reverts. Used by grind for console timing. */
interface MigrationEvents {
    fun starting(name: String) {}

    fun finished(name: String, millis: Long) {}
}

/**
 * The ordered list of an application's migrations. Timestamped names
 * (`yyyy_MM_dd_HHmmss_snake_name` file stems, or legacy `M{stamp}_Class` names)
 * run in timestamp order; names without a stamp keep registration order and run
 * first. Each name is what [Migrator] records once it has run.
 */
class MigrationRegistry() {
    private val entries = linkedMapOf<String, Migration>()

    constructor(vararg migrations: Migration) : this() {
        migrations.forEach { add(it) }
    }

    fun add(migration: Migration): MigrationRegistry = add(MigrationOrder.recordedName(migration), migration)

    fun add(name: String, migration: Migration): MigrationRegistry = apply {
        require(name.isNotBlank()) { "Migration name must not be blank" }
        require(name !in entries) { "Migration [$name] is already registered" }
        entries[name] = migration
    }

    val names: List<String> get() = MigrationOrder.sort(entries.keys.toList())

    operator fun get(name: String): Migration? = entries[name]
}

/** A registered migration and, when it has run, the batch it ran in. */
data class MigrationStatus(val name: String, val batch: Int?) {
    val ran: Boolean get() = batch != null
}

class MigrationException(message: String, cause: Throwable? = null) : CadenceException(message, cause)
