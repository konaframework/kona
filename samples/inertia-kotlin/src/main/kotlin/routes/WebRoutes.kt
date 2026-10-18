package routes

import org.konaframework.http.Router
import org.konaframework.inertia.Inertia

object WebRoutes {
    fun register(router: Router) {
        router.get("/") {
            Inertia.render(
                "Pages/Welcome",
                mapOf("message" to "Welcome to Kona Inertia"),
            )
        }
        router.get("/about") {
            Inertia.render("Pages/About", mapOf("title" to "About"))
        }
        router.get("/leave") {
            Inertia.location("https://example.com")
        }
    }
}
