package routes

import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.inertia.Inertia

object WebRoutes {
    fun register(router: Router) {
        router.get("/") {
            Inertia.render(
                "Pages/Welcome",
                mapOf(
                    "message" to "Welcome to Kona Inertia",
                    "stats" to Inertia.optional { mapOf("visits" to 1) },
                    "sidebar" to Inertia.defer({ listOf("Getting started", "Protocol") }, "aside"),
                    "events" to Inertia.merge(listOf(mapOf("id" to 1, "title" to "Launch"))),
                ),
            )
        }
        router.get("/about") {
            Inertia.render("Pages/About", mapOf("title" to "About"))
        }
        router.put("/profile") { request: Request ->
            Response.redirect("/about").with("success", "Profile updated")
        }
        router.get("/leave") {
            Inertia.location("https://example.com")
        }
    }
}
