package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Discovers reusable packages via `META-INF/kona/providers` (one FQCN per line).
 */
object PackageDiscovery {
    const val RESOURCE = "META-INF/kona/providers"

    fun providerNames(loader: ClassLoader): List<String> {
        val names = linkedSetOf<String>()
        val resources = loader.getResources(RESOURCE)
        while (resources.hasMoreElements()) {
            resources.nextElement().openStream().use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { names += it }
                }
            }
        }
        return names.toList()
    }

    fun loadProviders(loader: ClassLoader): List<ServiceProvider> =
        providerNames(loader).map { name ->
            val type = Class.forName(name, true, loader)
            require(ServiceProvider::class.java.isAssignableFrom(type)) {
                "Package provider [$name] must implement ServiceProvider"
            }
            @Suppress("UNCHECKED_CAST")
            (type as Class<out ServiceProvider>).getDeclaredConstructor().newInstance()
        }

    fun registerAll(app: Application, loader: ClassLoader = app.javaClass.classLoader): List<String> {
        val names = providerNames(loader)
        loadProviders(loader).forEach { app.register(it) }
        return names
    }
}
