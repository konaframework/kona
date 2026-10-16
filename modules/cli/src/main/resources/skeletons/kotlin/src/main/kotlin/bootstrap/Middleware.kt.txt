package bootstrap

import io.konaframework.core.Application
import io.konaframework.filesystem.FilesystemManager
import io.konaframework.http.HttpKernel
import io.konaframework.http.HttpMiddleware
import io.konaframework.http.ServePublicStorage
import io.konaframework.session.SessionManager
import io.konaframework.session.StartSession
import io.konaframework.session.VerifyCsrfToken

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
