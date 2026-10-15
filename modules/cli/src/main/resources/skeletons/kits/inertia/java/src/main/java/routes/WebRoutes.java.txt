package routes;

import app.http.controllers.WelcomeController;
import dev.kona.http.Router;

public class WebRoutes {
    public void register(Router router) {
        WelcomeController welcome = new WelcomeController();
        router.get("/", welcome::index).name("welcome");
    }
}
