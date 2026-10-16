package app.http.controllers

import dev.kona.crema.view
import dev.kona.http.Request
import dev.kona.http.Response

class WelcomeController {
    fun index(request: Request): Response =
        view("welcome", mapOf("name" to "Kona")).toResponse()
}
