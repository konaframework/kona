import io.konaframework.core.Kona
import io.konaframework.http.HttpKernel
import io.konaframework.http.HttpServiceProvider
import io.konaframework.http.KonaHttpServer
import io.konaframework.http.Response
import io.konaframework.http.Router

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
