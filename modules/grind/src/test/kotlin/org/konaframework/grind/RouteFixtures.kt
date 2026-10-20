package org.konaframework.grind

import app.http.NamedShowHandler
import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.http.Handler
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router

object EmptyRouterApp {
    @JvmStatic
    fun create(args: Array<String>): Application =
        Kona.create(args).providers(HttpServiceProvider()).boot()
}

object RouteListApp {
    @JvmStatic
    fun create(args: Array<String>): Application {
        val app = Kona.create(args).providers(HttpServiceProvider()).boot()
        val router = app.resolve(Router::class.java)
        router.get("/home") { Response.text("home") }.name("home")
        router.post("/users") { Response.text("store") }.name("users.store")
        router.get("/users/{id}", NamedShowHandler()).name("users.show")
        router.match(listOf("PUT", "PATCH"), "/users/{id}") { Response.text("update") }.name("users.update")
        router.delete("/users/{id}") { Response.text("destroy") }.name("users.destroy")
        router.get("/admin") { Response.text("admin") }
            .middleware(NamedGate(), Middleware { request, nxt -> nxt.handle(request) })
            .name("admin")
        router.get("/anon") { Response.text("anon") }
        router.any("/any") { Response.text("any") }.name("any")
        router.options("/ping") { Response.text("pong") }.name("ping")
        return app
    }
}

class NamedGate : Middleware {
    override fun handle(request: Request, next: Handler): Response = next.handle(request)
}
