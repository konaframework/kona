package hello

import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.core.ServiceProvider
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.Middleware
import dev.kona.http.Request
import dev.kona.http.Response
import dev.kona.http.Router
import dev.kona.http.invoke

class Greeter(private val greeting: String) {
    fun greet(name: String): String = "$greeting, $name"
}

/** Adds a header to every response (global middleware). */
val PoweredBy = Middleware { request, next -> next(request).header("X-Powered-By", "Kona") }

/** Rejects requests without `X-Token: secret` (group middleware). */
val RequireToken = Middleware { request, next ->
    if (request.header("X-Token") == "secret") {
        next(request)
    } else {
        Response.json(mapOf("error" to "unauthorized"), 401)
    }
}

/** Binds services in `register`, defines routes in `boot` (after every provider registered). */
class HelloServiceProvider : ServiceProvider {
    var booted = false
        private set

    override fun register(app: Application) {
        app.container.singleton(Greeter::class.java) { Greeter("Hello") }
    }

    override fun boot(app: Application) {
        val greeter = app.resolve(Greeter::class.java)
        app.resolve(Router::class.java).routes {
            get("/") { Response.text("Hello from Kotlin Kona") }.name("home")
            group(prefix = "/api", name = "api.", middleware = listOf(RequireToken)) {
                get("/hello/{name}") { request ->
                    Response.json(mapOf("hello" to greeter.greet(request.attributes["name"].toString())))
                }.name("hello")
            }
        }
        app.resolve(HttpKernel::class.java).pushMiddleware(PoweredBy)
        booted = true
    }
}

object HelloKotlin {
    @JvmStatic
    fun boot(args: Array<String>): Application =
        Kona.create(args).providers(HttpServiceProvider(), HelloServiceProvider()).boot()

    /** Drives the in-process HTTP kernel through routes, groups, and middleware. */
    @JvmStatic
    fun demo(app: Application): List<String> {
        val kernel = app.resolve(HttpKernel::class.java)
        val router = app.resolve(Router::class.java)
        val home = kernel.handle(Request.get("/"))
        val denied = kernel.handle(Request.get("/api/hello/kona"))
        val allowed = kernel.handle(Request("GET", "/api/hello/kona", headers = mapOf("X-Token" to "secret")))
        val missing = kernel.handle(Request.get("/nope"))
        return listOf(
            "${home.status} ${home.body} [${home.headers["X-Powered-By"]}]",
            "${denied.status} ${denied.body}",
            "${allowed.status} ${allowed.body}",
            "${missing.status} [${missing.headers["X-Powered-By"]}]",
            "route(api.hello) = ${router.route("api.hello", mapOf("name" to "kona"))}",
        )
    }
}

fun main(args: Array<String>) {
    HelloKotlin.demo(HelloKotlin.boot(args)).forEach(::println)
    NotesDemo.run().forEach(::println)
    SecurityDemo.run(args).forEach(::println)
}
