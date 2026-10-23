package org.konaframework.wire

import org.konaframework.wire.view.InjectAssets
import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import org.konaframework.crema.CremaEngine
import org.konaframework.http.HttpKernel
import org.konaframework.http.Response
import org.konaframework.http.Router

/**
 * Binds [WireManager], installs the Crema surface, and registers
 * `POST {brew.path}/update`, `GET {brew.path}/brew.js|brew.min.js|brew.min.js.map`, plus asset injection.
 */
open class WireServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(WireManager::class.java) {
            val views = if (app.container.bound(CremaEngine::class.java)) app.resolve(CremaEngine::class.java) else null
            WireManager(WireConfig.from(app.config), views, app.container)
        }
    }

    override fun boot(app: Application) {
        val brew = app.resolve(WireManager::class.java)
        Wire.setManager(brew)
        // Always attach the app Crema engine so `<wire:…>` / `@brew` work even if WireManager was
        // constructed before CremaEngine was first resolved (or constructed with views=null).
        if (app.container.bound(CremaEngine::class.java)) {
            brew.setViews(app.resolve(CremaEngine::class.java))
        }
        // Capture the app classloader (set by AppLoader / the creating thread) before request VTs spawn.
        Thread.currentThread().contextClassLoader?.let { brew.registry.classLoader = it }
        brew.config.classNamespaces.forEach { brew.registry.addLocation(it) }
        if (app.container.bound(Router::class.java)) routes(brew, app.resolve(Router::class.java))
        if (app.container.bound(HttpKernel::class.java)) app.resolve(HttpKernel::class.java).pushMiddleware(InjectAssets(brew))
    }

    companion object {
        @JvmStatic
        fun routes(brew: WireManager, router: Router) {
            val names = listOf(
                "wire.js", "wire.min.js", "wire.min.js.map", "wire.csp.min.js", "wire.esm.js",
                "brew.js", "brew.min.js", "brew.min.js.map", "brew.csp.min.js", "brew.esm.js",
            )
            val paths = linkedSetOf(brew.config.path, "/brew", "/wire")
            for (path in paths) {
                if (path == brew.config.path) {
                    router.post(brew.config.updateUri) {
                        if (brew.updateUri != brew.config.updateUri) Response.text("Not Found", 404) else brew.handleUpdate(it)
                    }
                } else {
                    router.post("$path/update") { brew.handleUpdate(it) }
                }
                for (name in names) {
                    router.get("$path/$name") {
                        if (brew.scriptUri != null) Response.text("Not Found", 404) else brew.assets.response(name)
                    }
                }
                router.post("$path/upload") { brew.handleUpload(it) }
                router.get("$path/preview/{path}") { brew.handlePreview(it) }
            }
        }
    }
}

