package dev.kona.crema

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
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
    }

    companion object {
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
