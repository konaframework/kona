import dev.kona.core.Kona
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.KonaHttpServer
import dev.kona.http.Response
import dev.kona.http.Router

/**
 * Minimal pure-HTTP bench app — no session, CSRF, auth, or ORM.
 * Endpoints match Spring Boot / FastAPI counterparts.
 */
fun main(args: Array<String>) {
    val app = Kona.create(args)
        .providers(HttpServiceProvider())
        .boot()
    val router = app.resolve(Router::class.java)
    router.get("/ping") { Response.text("pong") }
    router.get("/json") {
        Response.json(
            mapOf(
                "message" to "hello",
                "ok" to true,
                "n" to 42,
            ),
        )
    }
    val host = System.getenv("HOST") ?: "127.0.0.1"
    val port = (System.getenv("PORT") ?: "4000").toInt()
    KonaHttpServer(app.resolve(HttpKernel::class.java), host, port).start()
    Thread.currentThread().join()
}
