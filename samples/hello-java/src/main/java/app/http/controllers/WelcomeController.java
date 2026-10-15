package app.http.controllers;

import dev.kona.http.Request;
import dev.kona.http.Response;

public class WelcomeController {
    public Response index(Request request) {
        return Response.text("Welcome to Kona");
    }
}
