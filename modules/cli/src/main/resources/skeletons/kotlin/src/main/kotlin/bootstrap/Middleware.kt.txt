package bootstrap

import dev.kona.core.Application
import dev.kona.http.HttpMiddleware
import dev.kona.session.SessionManager
import dev.kona.session.StartSession
import dev.kona.session.VerifyCsrfToken

/**
 * HTTP middleware registration (Almasix / Laravel bootstrap shape).
 *
 * Web stack: session + CSRF (API routes under /api are CSRF-excepted).
 */
object Middleware {
    @JvmStatic
    fun configure(app: Application) {
        val sessions = app.resolve(SessionManager::class.java)
        HttpMiddleware()
            .alias("start.session") { StartSession(sessions) }
            .alias("csrf") { VerifyCsrfToken(except = listOf("/api/*", "/brew/*")) }
            .web(listOf("start.session", "csrf"))
            // Behind a load balancer / ingress:
            // .trustProxies(at = listOf("*"))
            // .trustHosts(at = listOf("example.com", "*.example.com"))
            .apply(app)
    }
}
