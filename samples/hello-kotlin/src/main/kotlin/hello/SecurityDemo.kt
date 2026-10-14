package hello

import dev.kona.auth.AuthManager
import dev.kona.auth.AuthServiceProvider
import dev.kona.auth.Hash
import dev.kona.auth.InMemoryUserProvider
import dev.kona.auth.SimpleUser
import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.Request
import dev.kona.http.Response
import dev.kona.http.Router
import dev.kona.session.Session
import dev.kona.session.SessionManager
import dev.kona.session.SessionServiceProvider
import dev.kona.session.StartSession

/** Minimal session flash + auth attempt demo for M3. */
object SecurityDemo {
    @JvmStatic
    fun boot(args: Array<String>): Application =
        Kona.create(args)
            .providers(HttpServiceProvider(), SessionServiceProvider(), AuthServiceProvider())
            .configure { it.set("session.driver", "array") }
            .boot()

    @JvmStatic
    fun run(args: Array<String> = emptyArray()): List<String> {
        Kona.reset()
        val app = boot(args)
        val users = app.resolve(InMemoryUserProvider::class.java)
        users.add(SimpleUser(1, Hash.make("secret"), email = "dev@example.com"))

        val sessions = app.resolve(SessionManager::class.java)
        val kernel = app.resolve(HttpKernel::class.java)
        kernel.pushMiddleware(StartSession(sessions))

        val auth = app.resolve(AuthManager::class.java)
        app.resolve(Router::class.java).routes {
            get("/flash") { request ->
                val session = request.getAttribute(StartSession.ATTRIBUTE) as Session
                val prior = session.get("notice") as? String
                session.flash("notice", "saved")
                Response.json(mapOf("prior" to (prior ?: "")))
            }
            post("/login") { request ->
                auth.setRequest(request)
                val ok = auth.attempt(
                    mapOf(
                        "email" to request.input("email"),
                        "password" to request.input("password"),
                    ),
                )
                if (!ok) {
                    return@post Response.json(mapOf("ok" to false), 401)
                }
                Response.json(mapOf("ok" to true, "id" to auth.id()))
            }
        }

        val first = kernel.handle(Request.get("/flash"))
        val sid = first.cookies.first().value
        val cookieHeader = "${sessions.cookieName()}=$sid"
        val second = kernel.handle(
            Request(
                "GET",
                "/flash",
                headers = mapOf("Cookie" to cookieHeader),
            ),
        )
        val login = kernel.handle(
            Request(
                "POST",
                "/login",
                headers = mapOf(
                    "Cookie" to cookieHeader,
                    "Content-Type" to "application/x-www-form-urlencoded",
                ),
                body = "email=dev%40example.com&password=secret",
            ),
        )
        return listOf(
            "flash1=${first.status}:${first.body}",
            "flash2=${second.status}:${second.body}",
            "login=${login.status}:${login.body}",
        )
    }
}
