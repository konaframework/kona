package routes

import app.http.controllers.PingApiController
import org.konaframework.http.Response
import org.konaframework.http.Router

class ApiRoutes {
    fun register(router: Router) {
        router.group(prefix = "/api", name = "api.") {
            get("/health") { Response.json(mapOf("status" to "ok")) }.name("health")

            apiResource("pings", PingApiController())
        }
    }
}
