package bootstrap

import org.konaframework.wire.WireServiceProvider
import org.konaframework.core.Application
import org.konaframework.core.Env
import org.konaframework.core.Kona
import org.konaframework.crema.CremaServiceProvider
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpMiddleware
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.KonaHttpServer
import org.konaframework.http.Router
import org.konaframework.session.SessionManager
import org.konaframework.session.SessionServiceProvider
import org.konaframework.session.StartSession
import org.konaframework.session.VerifyCsrfToken
import routes.WebRoutes

object App {
    @JvmStatic
    fun create(args: Array<String>): Application {
        val app = Kona.create(args)
            .configure {
                it.set("app.key", Env.getOrDefault("APP_KEY", "wire-sample-kotlin-key"))
                it.set("app.debug", true)
                it.set("session.driver", "cookie")
            }
            .providers(HttpServiceProvider(), SessionServiceProvider(), CremaServiceProvider(), WireServiceProvider())
            .afterBoot(::middleware)
            .boot()
        WebRoutes.register(app.resolve(Router::class.java))
        return app
    }

    private fun middleware(app: Application) {
        val sessions = app.resolve(SessionManager::class.java)
        HttpMiddleware()
            .alias("start.session") { StartSession(sessions) }
            .alias("csrf") { VerifyCsrfToken() }
            .web(listOf("start.session", "csrf"))
            .apply(app)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val app = create(args)
        val host = Env.getOrDefault("HOST", "127.0.0.1")
        val port = Env.getOrDefault("PORT", "4000").toInt()
        KonaHttpServer(app.resolve(HttpKernel::class.java), host, port).start()
    }
}
