package dev.kona.crema

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import java.nio.file.Path

class CremaServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(CremaEngine::class.java) {
            val configured = app.config.getString("view.path")
            val root = if (configured != null) {
                Path.of(configured)
            } else {
                app.basePath.resolve("resources/views")
            }
            CremaEngine(FileViewFinder(root))
        }
    }
}
