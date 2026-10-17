package org.konaframework.filesystem

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

class FilesystemServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(FilesystemManager::class.java) {
            FilesystemManager(app.config, app.basePath)
        }
        app.container.singleton(FilesystemAdapter::class.java) {
            app.resolve(FilesystemManager::class.java).disk()
        }
    }
}
