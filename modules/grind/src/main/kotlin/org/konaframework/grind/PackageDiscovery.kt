package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import org.konaframework.core.PackageDiscovery as CorePackageDiscovery

/**
 * Discovers reusable packages via `META-INF/kona/providers` (one FQCN per line).
 *
 * Delegates to [org.konaframework.core.PackageDiscovery] — [Application.boot] also
 * registers these automatically for forked app processes.
 */
object PackageDiscovery {
    const val RESOURCE = CorePackageDiscovery.RESOURCE

    fun providerNames(loader: ClassLoader): List<String> = CorePackageDiscovery.providerNames(loader)

    fun loadProviders(loader: ClassLoader): List<ServiceProvider> = CorePackageDiscovery.loadProviders(loader)

    fun registerAll(app: Application, loader: ClassLoader = app.javaClass.classLoader): List<String> =
        CorePackageDiscovery.registerAll(app, loader)
}
