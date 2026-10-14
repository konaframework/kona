package hello;

import dev.kona.core.Application;
import dev.kona.core.Kona;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.http.Router;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        Application app = Kona.start(args);
        app.register(new HttpServiceProvider());
        Router router = app.resolve(Router.class);
        router.get("/", request -> Response.text("Hello from Java Kona")).name("home");
        router.get("/hello/{name}", (Request request) ->
            Response.json(Map.of("hello", String.valueOf(request.getAttribute("name"))))
        );
        HttpKernel kernel = app.resolve(HttpKernel.class);
        System.out.println(kernel.handle(Request.get("/")).getBody());
    }

    private Main() {
    }
}
