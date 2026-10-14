package dev.kona.inertia

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.crema.CremaEngine
import dev.kona.inertia.assets.AssetManifest
import dev.kona.inertia.assets.Vite
import java.nio.file.Path

/**
 * Binds [InertiaManager] and [AssetManifest] / [Vite].
 *
 * Config keys (optional):
 * - `inertia.root_view` — Crema view name for the HTML shell
 * - `inertia.version` — asset version string (`X-Inertia-Version`)
 * - `inertia.public_path` — directory containing `build/manifest.json` and optional `hot`
 */
class InertiaServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(InertiaManager::class.java) {
            InertiaManager().also { configure(app, it) }
        }
        app.container.singleton(AssetManifest::class.java) {
            val configured = app.config.getString("inertia.public_path")
            val publicPath = if (configured != null) {
                Path.of(configured)
            } else {
                app.basePath.resolve("public")
            }
            AssetManifest(publicPath)
        }
    }

    override fun boot(app: Application) {
        val inertia = app.resolve(InertiaManager::class.java)
        configure(app, inertia)
        Inertia.setManager(inertia)
        if (app.container.bound(CremaEngine::class.java)) {
            inertia.cremaEngine = app.resolve(CremaEngine::class.java)
        }
        val assets = app.resolve(AssetManifest::class.java)
        Vite.setManifest(assets)
    }

    private fun configure(app: Application, inertia: InertiaManager) {
        app.config.getString("inertia.root_view")?.let { inertia.rootView = it }
        app.config.getString("inertia.version")?.let { inertia.version = it }
        app.config.getString("inertia.root_html")?.let { inertia.rootHtml = it }
    }
}
