package routes

import app.http.controllers.PingApiController
import dev.kona.http.Response
import dev.kona.http.Router

class ApiRoutes {
    fun register(router: Router) {
        router.group(prefix = "/api", name = "api.") {
            get("/health") { Response.json(mapOf("status" to "ok")) }.name("health")

            // Recommended: instantiate + method reference style also works with apiResource(instance)
            apiResource("pings", PingApiController())

            // Concise alternative: Class resolved from the application container
            // apiResource("pings", PingApiController::class.java)
        }
    }
}
