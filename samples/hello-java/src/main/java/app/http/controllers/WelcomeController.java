package app.http.controllers;

import org.konaframework.crema.Views;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import java.util.Map;

public class WelcomeController {
    public Response index(Request request) {
        return Views.make("welcome", Map.of("name", "Kona")).toResponse();
    }
}
