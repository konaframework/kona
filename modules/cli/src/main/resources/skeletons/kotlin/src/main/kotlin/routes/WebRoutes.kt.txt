package routes

import app.http.controllers.WelcomeController
import dev.kona.http.Router

class WebRoutes {
    fun register(router: Router) {
        val welcome = WelcomeController()
        router.get("/") { welcome.index(it) }.name("welcome")
    }
}
