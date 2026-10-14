package hello;

import dev.kona.core.Application;
import dev.kona.core.Kona;
import dev.kona.core.ServiceProvider;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Middleware;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.http.Router;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Main {
    /** Service bound by {@link HelloServiceProvider#register}. */
    public static final class Greeter {
        private final String greeting;

        public Greeter(String greeting) {
            this.greeting = greeting;
        }

        public String greet(String name) {
            return greeting + ", " + name;
        }
    }

    /** Global middleware: tags every response. */
    public static final Middleware POWERED_BY = (request, next) ->
        next.handle(request).header("X-Powered-By", "Kona");

    /** Group middleware: requires {@code X-Token: secret}. */
    public static final Middleware REQUIRE_TOKEN = (request, next) ->
        "secret".equals(request.header("X-Token"))
            ? next.handle(request)
            : Response.json(Map.of("error", "unauthorized"), 401);

    /** Route middleware: marks admin responses. */
    public static final Middleware ADMIN_ONLY = (request, next) ->
        next.handle(request).header("X-Admin", "true");

    /** Binds services in register(), defines routes in boot() once every provider has registered. */
    public static final class HelloServiceProvider implements ServiceProvider {
        private boolean booted;

        @Override
        public void register(Application app) {
            app.getContainer().singleton(Greeter.class, container -> new Greeter("Hello"));
        }

        @Override
        public void boot(Application app) {
            Greeter greeter = app.resolve(Greeter.class);
            Router router = app.resolve(Router.class);
            router.get("/", request -> Response.text("Hello from Java Kona")).name("home");
            router.prefix("/api").name("api.").middleware(REQUIRE_TOKEN).group(api -> {
                api.get("/hello/{name}", request ->
                    Response.json(Map.of("hello", greeter.greet(String.valueOf(request.getAttribute("name")))))
                ).name("hello");
                api.group("/admin", List.of(ADMIN_ONLY), admin -> {
                    admin.get("/stats", request -> Response.json(Map.of("routes", router.all().size()))).name("admin.stats");
                });
            });
            app.resolve(HttpKernel.class).pushMiddleware(POWERED_BY);
            booted = true;
        }

        public boolean isBooted() {
            return booted;
        }
    }

    public static Application boot(String[] args) {
        return Kona.create(args)
            .providers(new HttpServiceProvider(), new HelloServiceProvider())
            .boot();
    }

    /** Drives the in-process HTTP kernel through routes, nested groups, and middleware. */
    public static List<String> demo(Application app) {
        HttpKernel kernel = app.resolve(HttpKernel.class);
        Router router = app.resolve(Router.class);
        Map<String, String> token = Map.of("X-Token", "secret");
        Response home = kernel.handle(Request.get("/"));
        Response denied = kernel.handle(Request.get("/api/hello/kona"));
        Response allowed = kernel.handle(new Request("GET", "/api/hello/kona", token, Map.of(), "", new HashMap<>()));
        Response stats = kernel.handle(new Request("GET", "/api/admin/stats", token, Map.of(), "", new HashMap<>()));
        Response missing = kernel.handle(Request.get("/nope"));
        return List.of(
            home.getStatus() + " " + home.getBody() + " [" + home.getHeaders().get("X-Powered-By") + "]",
            denied.getStatus() + " " + denied.getBody(),
            allowed.getStatus() + " " + allowed.getBody(),
            stats.getStatus() + " " + stats.getBody() + " [admin=" + stats.getHeaders().get("X-Admin") + "]",
            missing.getStatus() + " [" + missing.getHeaders().get("X-Powered-By") + "]",
            "route(api.hello) = " + router.route("api.hello", Map.of("name", "kona"))
        );
    }

    public static void main(String[] args) {
        demo(boot(args)).forEach(System.out::println);
        SecurityDemo.run(args).forEach(System.out::println);
        M9Demo.run().forEach(System.out::println);
    }

    private Main() {
    }
}
