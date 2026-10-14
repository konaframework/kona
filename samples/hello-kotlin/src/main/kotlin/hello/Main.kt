package hello

import dev.kona.core.Kona
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.Request
import dev.kona.http.Response
import dev.kona.http.Router

fun main(args: Array<String>) {
    val app = Kona.start(args)
    app.register(HttpServiceProvider())
    val router = app.resolve(Router::class.java)
    router.get("/") { Response.text("Hello from Kotlin Kona") }.name("home")
    router.get("/hello/{name}") { req: Request ->
        Response.json(mapOf("hello" to (req.attributes["name"] ?: "world")))
    }
    val kernel = app.resolve(HttpKernel::class.java)
    val response = kernel.handle(Request.get("/"))
    println(response.body)
}
