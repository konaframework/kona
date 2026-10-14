package hello;

import dev.kona.core.Application;
import dev.kona.core.Kona;
import dev.kona.http.BaseResourceController;
import dev.kona.http.DefaultExceptionHandler;
import dev.kona.http.FormRequest;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Controllers;
import dev.kona.http.InvokableController;
import dev.kona.http.JsonResource;
import dev.kona.http.Request;
import dev.kona.http.Response;
import dev.kona.http.Router;
import dev.kona.http.UrlGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HttpPolishDemo {
    private HttpPolishDemo() {}

    static final class UserResource extends JsonResource {
        private final int id;
        private final String name;

        UserResource(int id, String name) {
            super(name);
            this.id = id;
            this.name = name;
        }

        @Override
        public Map<String, Object> toArray(Request request) {
            return Map.of("id", id, "name", name);
        }
    }

    static final class StoreNoteRequest extends FormRequest {
        @Override
        public Map<String, List<String>> rules() {
            return Map.of("title", List.of("required", "min:3"));
        }
    }

    static final class NotesController extends BaseResourceController {
        @Override
        public Response index(Request request) {
            return JsonResource.collection(List.of(new UserResource(1, "Ada"))).toResponse(request);
        }

        @Override
        public Response store(Request request) {
            Map<String, Object> data = validate(request, new StoreNoteRequest());
            return Response.json(data, 201);
        }
    }

    public static Map<String, String> run(String[] args) {
        Application app = Kona.create(args).providers(new HttpServiceProvider()).boot();
        return run(app);
    }

    public static Map<String, String> run(Application app) {
        Router router = app.resolve(Router.class);
        UrlGenerator urls = app.resolve(UrlGenerator.class).setRoot("https://demo.test").setKey("demo");
        NotesController notes = new NotesController();
        router.get("/notes", notes::index).name("notes.index");
        router.post("/notes", notes::store);
        InvokableController ping = request -> Response.text("pong");
        router.get("/ping", Controllers.asHandler(ping)).name("ping");

        HttpKernel kernel = new HttpKernel(router, List.of(), new DefaultExceptionHandler());
        Response index = kernel.handle(Request.get("/notes"));
        Response created = kernel.handle(Request.post("/notes", "title=Hello"));
        String signed = urls.signedRoute("notes.index", Map.of(), 60L);

        Map<String, String> out = new LinkedHashMap<>();
        out.put("index", index.getStatus() + ":" + index.getBody());
        out.put("created", created.getStatus() + ":" + created.getBody());
        out.put("route", urls.route("notes.index"));
        out.put("signed", signed.substring(0, signed.indexOf('?')));
        out.put("ping", kernel.handle(Request.get("/ping")).getBody());
        return out;
    }
}
