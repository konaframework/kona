package org.konaframework.crema

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import org.konaframework.crema.assets.AssetManifest
import org.konaframework.crema.assets.Vite
import org.konaframework.http.HttpKernel
import org.konaframework.http.ServePublicDirectory
import java.nio.file.Files
import java.nio.file.Path

class CremaServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(CremaEngine::class.java) {
            val configured = app.config.getString("view.path")
            val root = if (configured != null) {
                Path.of(configured)
            } else {
                resolveDefaultViews(app)
            }
            CremaEngine(FileViewFinder(root))
        }
        app.container.singleton(AssetManifest::class.java) {
            AssetManifest(resolveVitePublic(app))
        }
    }

    override fun boot(app: Application) {
        Views.setEngine(app.resolve(CremaEngine::class.java))
        Vite.setManifest(app.resolve(AssetManifest::class.java))
        ensureViteAssetsServed(app)
    }

    companion object {
        /**
         * Directory that contains `hot` and `build/` (default `<base>/public`).
         * `vite.public_path` wins over `inertia.public_path`. Relative paths resolve from the app base.
         */
        @JvmStatic
        fun resolveVitePublic(app: Application): Path {
            val configured = app.config.getString("vite.public_path")
                ?: app.config.getString("inertia.public_path")
            if (configured == null) {
                return app.basePath.resolve("public")
            }
            val path = Path.of(configured)
            return if (path.isAbsolute) path else app.basePath.resolve(path)
        }

        /**
         * Serves the project `public/` directory (logos, `robots.txt`, and Vite `public/build`).
         * That directory is also `paths.public` by default. Apps that override `paths.public`
         * still get hashed `/build/assets/…` from this root.
         */
        @JvmStatic
        fun ensureViteAssetsServed(app: Application) {
            if (!app.container.bound(HttpKernel::class.java)) {
                return
            }
            val directory = resolveVitePublic(app)
            val kernel = app.resolve(HttpKernel::class.java)
            val already = kernel.globalMiddleware.any { middleware ->
                middleware is ServePublicDirectory && middleware.serves(directory)
            }
            if (already) {
                return
            }
            kernel.prependMiddleware(ServePublicDirectory(directory))
        }

        fun resolveDefaultViews(app: Application): Path {
            val candidates = listOf(
                app.viewPath(),
                app.basePath.resolve("src/main/resources/views"),
                app.basePath.resolve("build/resources/main/views"),
                app.basePath.resolve("resources/views"),
            )
            return candidates.firstOrNull { Files.isDirectory(it) } ?: candidates.first()
        }

        /** @deprecated Prefer [resolveDefaultViews] with an [Application]. */
        fun resolveDefaultViews(basePath: Path): Path {
            val candidates = listOf(
                basePath.resolve("src/main/resources/views"),
                basePath.resolve("build/resources/main/views"),
                basePath.resolve("resources/views"),
            )
            return candidates.firstOrNull { Files.isDirectory(it) } ?: candidates.first()
        }
    }
}
