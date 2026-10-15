package app.http.controllers

import dev.kona.http.Request
import dev.kona.http.Response

class WelcomeController {
    fun index(request: Request): Response = Response.text("Welcome to Kona")
}
