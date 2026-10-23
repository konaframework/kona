package routes

import org.konaframework.core.Kona
import org.konaframework.crema.CremaEngine
import org.konaframework.http.Response
import org.konaframework.http.Router

object WebRoutes {
    fun register(router: Router) {
        router.get("/") {
            val crema = Kona.app().resolve(CremaEngine::class.java)
            Response.html(crema.render("welcome", mapOf("title" to "Wire sample")))
        }.name("welcome")
    }
}
