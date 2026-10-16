package app.http.controllers;

import dev.kona.crema.Views;
import dev.kona.http.Request;
import dev.kona.http.Response;
import java.util.Map;

public class WelcomeController {
    public Response index(Request request) {
        return Views.make("welcome", Map.of("name", "Kona")).toResponse();
    }
}
