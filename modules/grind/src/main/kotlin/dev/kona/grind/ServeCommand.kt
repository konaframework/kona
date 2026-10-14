package dev.kona.grind

import dev.kona.core.Application
import dev.kona.core.Env
import dev.kona.core.Kona
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.KonaHttpServer
import dev.kona.http.Response
import dev.kona.http.Router
import java.nio.file.Path

class ServeCommand(
    private val waitForever: () -> Unit = { Thread.currentThread().join() },
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val base = Path.of(options.getOrDefault("base", ".")).toAbsolutePath().normalize()

        Kona.reset()
        val app = Kona.start(arrayOf("--base=$base"))
        val host = options.getOrDefault("host", Env.getOrDefault("HOST", "127.0.0.1"))
        val port = options.getOrDefault("port", Env.getOrDefault("PORT", "8080")).toInt()
        app.register(HttpServiceProvider())
        configureDefaultRoutes(app)

        val kernel = app.resolve(HttpKernel::class.java)
        val server = KonaHttpServer(kernel, host, port)
        server.start()

        if (options.getOrDefault("once", "false") == "true") {
            server.stop()
            return 0
        }
        waitForever()
        return 0
    }

    internal fun configureDefaultRoutes(app: Application) {
        val router = app.resolve(Router::class.java)
        registerExternalRoutes(router)
        if (router.all().isEmpty()) {
            addDefaultRoutes(router)
        }
    }

    private fun registerExternalRoutes(router: Router) {
        val routesClass = System.getProperty("kona.routes")
        if (routesClass == null) {
            return
        }
        if (routesClass.isBlank()) {
            return
        }
        val clazz = Class.forName(routesClass)
        val registrar = clazz.getDeclaredConstructor().newInstance()
        val method = clazz.getMethod("register", Router::class.java)
        method.invoke(registrar, router)
    }

    private fun addDefaultRoutes(router: Router) {
        router.get("/") { Response.text("Kona is brewing.") }.name("welcome")
        router.get("/health") { Response.json(mapOf("status" to "ok")) }
    }
}
