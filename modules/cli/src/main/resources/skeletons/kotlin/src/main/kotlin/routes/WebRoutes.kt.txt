package routes

import app.http.controllers.WelcomeController
import io.konaframework.http.Router

class WebRoutes {
    fun register(router: Router) {
        val welcome = WelcomeController()
        router.get("/") { welcome.index(it) }.name("welcome")
    }
}
