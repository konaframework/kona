package bootstrap

import org.konaframework.core.Application
import org.konaframework.filesystem.FilesystemManager
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpMiddleware
import org.konaframework.http.ServePublicStorage
import org.konaframework.session.SessionManager
import org.konaframework.session.StartSession
import org.konaframework.session.VerifyCsrfToken

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
            .alias("csrf") { VerifyCsrfToken(except = listOf("/api/*", "/wire/*")) }
            .web(listOf("start.session", "csrf"))
            // Behind a load balancer / ingress:
            // .trustProxies(at = listOf("*"))
            // .trustHosts(at = listOf("example.com", "*.example.com"))
            .apply(app)
    }
}
