package app.http.controllers

import dev.kona.core.Kona
import dev.kona.crema.CremaEngine
import dev.kona.http.Request
import dev.kona.http.Response

class WelcomeController {
    fun index(request: Request): Response {
        val crema = Kona.app().resolve(CremaEngine::class.java)
        return Response.html(crema.render("welcome", mapOf("name" to "Kona")))
    }
}
