package bootstrap

import dev.kona.core.Application
import dev.kona.http.HttpMiddleware

/**
 * HTTP middleware registration (Almasix / Laravel bootstrap shape).
 */
object Middleware {
    @JvmStatic
    fun configure(app: Application) {
        HttpMiddleware()
            // Behind a load balancer / ingress:
            // .trustProxies(at = listOf("*"))
            // .trustHosts(at = listOf("example.com", "*.example.com"))
            .apply(app)
    }
}
