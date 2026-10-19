package org.konaframework.cadence.migrations

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Discovers migrations via `META-INF/kona/migrations` (one FQCN per line, registration order).
 *
 * `grind make:migration` / `make:model -m` append each new class here so
 * `./grind migrate` runs them without a manual [MigrationRegistry] bind.
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
        return names.toList()
    }

    fun loadMigrations(loader: ClassLoader): List<Migration> =
        classNames(loader).map { name ->
            val type = Class.forName(name, true, loader)
            require(Migration::class.java.isAssignableFrom(type)) {
                "Migration [$name] must implement Migration"
            }
            @Suppress("UNCHECKED_CAST")
            (type as Class<out Migration>).getDeclaredConstructor().newInstance()
        }

    fun loadRegistry(loader: ClassLoader): MigrationRegistry {
        val registry = MigrationRegistry()
        loadMigrations(loader).forEach { registry.add(it) }
        return registry
    }
}
