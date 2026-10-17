package app.http.controllers

import org.konaframework.crema.view
import org.konaframework.http.Request
import org.konaframework.http.Response

class WelcomeController {
    fun index(request: Request): Response =
        view("welcome", mapOf("name" to "Kona")).toResponse()
}
