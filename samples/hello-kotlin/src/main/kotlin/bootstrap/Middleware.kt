package bootstrap

import dev.kona.core.Application
import dev.kona.filesystem.FilesystemManager
import dev.kona.http.HttpKernel
import dev.kona.http.HttpMiddleware
import dev.kona.http.ServePublicStorage
import dev.kona.session.SessionManager
import dev.kona.session.StartSession
import dev.kona.session.VerifyCsrfToken

/**
 * HTTP middleware registration (Almasix / Laravel bootstrap shape).
 *
 * Web stack: session + CSRF (API routes under /api are CSRF-excepted).
 * Public disk files are served under the /storage URL prefix before the web stack.
 */
object Middleware {
    @JvmStatic
    fun configure(app: Application) {
        val sessions = app.resolve(SessionManager::class.java)
        val files = app.resolve(FilesystemManager::class.java)
        app.resolve(HttpKernel::class.java)
            .prependMiddleware(ServePublicStorage(files.disk("public")))
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
