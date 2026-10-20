package org.konaframework.cadence.migrations

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Discovers migrations via `META-INF/kona/migrations` (one FQCN per line).
 *
 * `grind make:migration` / `make:model -m` append each new class here so
 * `./grind migrate` runs them without a manual [MigrationRegistry] bind.
 * [MigrationRegistry] sorts by [MigrationOrder] using each class's
 * [MigrationName] (file stem) or legacy timestamped class name, so run order
 * does not depend on how lines happen to be listed here.
 */
object MigrationDiscovery {
    const val RESOURCE: String = "META-INF/kona/migrations"

    fun classNames(loader: ClassLoader): List<String> {
        val names = linkedSetOf<String>()
        val resources = loader.getResources(RESOURCE)
        while (resources.hasMoreElements()) {
            resources.nextElement().openStream().use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
                        .forEach { names += it }
                }
            }
        }
        return MigrationOrder.sort(names.toList())
    }

    fun loadMigrations(loader: ClassLoader): List<Migration> {
        val loaded = classNames(loader).map { name -> instantiate(name, loader) }
        val byName = loaded.associateBy { MigrationOrder.recordedName(it) }
        return MigrationOrder.sort(byName.keys.toList()).map { byName.getValue(it) }
    }

    internal fun instantiate(name: String, loader: ClassLoader): Migration {
        val type = Class.forName(name, true, loader)
        require(Migration::class.java.isAssignableFrom(type)) {
            "Migration [$name] must implement Migration"
        }
        val constructor = type.getDeclaredConstructor()
        constructor.trySetAccessible()
        @Suppress("UNCHECKED_CAST")
        return (constructor.newInstance() as Migration)
    }

    fun loadRegistry(loader: ClassLoader): MigrationRegistry {
        val registry = MigrationRegistry()
        loadMigrations(loader).forEach { registry.add(it) }
        return registry
    }
}
