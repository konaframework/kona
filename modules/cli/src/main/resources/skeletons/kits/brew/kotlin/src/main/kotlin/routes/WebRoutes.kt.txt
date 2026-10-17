package routes

import app.http.controllers.WelcomeController
import org.konaframework.http.Router

class WebRoutes {
    fun register(router: Router) {
        val welcome = WelcomeController()
        router.get("/") { welcome.index(it) }.name("welcome")
    }
}
