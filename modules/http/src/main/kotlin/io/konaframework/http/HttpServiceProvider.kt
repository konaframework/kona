package io.konaframework.http

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider

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
}
