package org.konaframework.http

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

class HttpServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Router::class.java) { Router() }
        app.container.singleton(UrlGenerator::class.java) { c ->
            val router = c.resolve(Router::class.java)
            val root = app.config.getString("app.url") ?: ""
            val key = app.config.getString("app.key") ?: "kona-app-key"
            UrlGenerator(router).setRoot(root).setKey(key).also { Url.setGenerator(it) }
        }
        app.container.singleton(HttpKernel::class.java) { c ->
            val router = c.resolve(Router::class.java)
            val debug = app.config.get<Boolean>("app.debug") ?: false
            HttpKernel(router, exceptionHandler = DefaultExceptionHandler(debug))
        }
    }

    override fun boot(app: Application) {
        val kernel = app.resolve(HttpKernel::class.java)
        if (kernel.globalMiddleware.none { it is PreventRequestsDuringMaintenance }) {
            kernel.prependMiddleware(PreventRequestsDuringMaintenance(app))
        }
        if (!RequestLogging.enabled(app)) {
            return
        }
        if (kernel.globalMiddleware.any { it is LogRequests }) {
            return
        }
        // Outermost so the access line sees final status (including 404/500 from the kernel).
        kernel.prependMiddleware(LogRequests())
    }
}
