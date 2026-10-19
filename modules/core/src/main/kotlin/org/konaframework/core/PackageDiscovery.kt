package org.konaframework.core

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Discovers reusable packages via `META-INF/kona/providers` (one FQCN per line).
 *
 * Written by `./grind install` / [org.konaframework.support.packages.PackageInstaller].
 * [Application.boot] registers these automatically so forked `bootstrap.App` processes
 * (default `grind serve`) load Brew/Inertia/etc. without listing every provider in App.kt.
 */
object PackageDiscovery {
    const val RESOURCE = "META-INF/kona/providers"

    @JvmStatic
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

    @JvmStatic
    fun loadProviders(loader: ClassLoader): List<ServiceProvider> =
        providerNames(loader).map { name ->
            val type = Class.forName(name, true, loader)
            require(ServiceProvider::class.java.isAssignableFrom(type)) {
                "Package provider [$name] must implement ServiceProvider"
            }
            @Suppress("UNCHECKED_CAST")
            (type as Class<out ServiceProvider>).getDeclaredConstructor().newInstance()
        }

    /**
     * Registers each discovered provider on [app]. Idempotent when the same class
     * is already registered (e.g. listed explicitly in `bootstrap.App`).
     */
    @JvmStatic
    fun registerAll(app: Application): List<String> =
        registerAll(app, Thread.currentThread().contextClassLoader ?: app.javaClass.classLoader)

    /** Registers providers discovered on [loader]. */
    @JvmStatic
    fun registerAll(app: Application, loader: ClassLoader): List<String> {
        val names = providerNames(loader)
        if (names.isEmpty()) {
            return emptyList()
        }
        loadProviders(loader).forEach { app.register(it) }
        return names
    }
}
