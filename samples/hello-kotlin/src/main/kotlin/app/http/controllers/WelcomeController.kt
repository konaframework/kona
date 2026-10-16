package app.http.controllers

import io.konaframework.crema.view
import io.konaframework.http.Request
import io.konaframework.http.Response

class WelcomeController {
    fun index(request: Request): Response =
        view("welcome", mapOf("name" to "Kona")).toResponse()
}
