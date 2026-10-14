package dev.kona.http

import dev.kona.core.Application
import dev.kona.core.ServiceProvider

class HttpServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Router::class.java) { Router() }
        app.container.singleton(HttpKernel::class.java) { c ->
            val router = c.resolve(Router::class.java)
            val debug = app.config.get<Boolean>("app.debug") ?: false
            HttpKernel(router, exceptionHandler = DefaultExceptionHandler(debug))
        }
    }
}
