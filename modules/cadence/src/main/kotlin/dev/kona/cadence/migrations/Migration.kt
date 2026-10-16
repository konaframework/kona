package dev.kona.cadence.migrations

import dev.kona.cadence.CadenceException
import dev.kona.cadence.schema.Schema

/** One reversible schema change. [up] applies it; [down] undoes exactly what [up] did. */
interface Migration {
    fun up(schema: Schema)

    fun down(schema: Schema)
}

/**
 * The ordered list of an application's migrations. Order of registration is the order they run;
 * each name (the class simple name unless given) is what [Migrator] records once it has run.
 */
class MigrationRegistry() {
    private val entries = linkedMapOf<String, Migration>()

    constructor(vararg migrations: Migration) : this() {
        migrations.forEach { add(it) }
    }

    fun add(migration: Migration): MigrationRegistry = add(migration.javaClass.simpleName, migration)

    fun add(name: String, migration: Migration): MigrationRegistry = apply {
        require(name.isNotBlank()) { "Migration name must not be blank" }
        require(name !in entries) { "Migration [$name] is already registered" }
        entries[name] = migration
    }

    val names: List<String> get() = entries.keys.toList()

    operator fun get(name: String): Migration? = entries[name]
}

/** A registered migration and, when it has run, the batch it ran in. */
data class MigrationStatus(val name: String, val batch: Int?) {
    val ran: Boolean get() = batch != null
}

class MigrationException(message: String, cause: Throwable? = null) : CadenceException(message, cause)
