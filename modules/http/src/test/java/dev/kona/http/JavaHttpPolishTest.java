package dev.kona.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaHttpPolishTest {
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

    static final class CreateUserRequest extends FormRequest {
        @Override
        public Map<String, List<String>> rules() {
            return Map.of("name", List.of("required", "min:2"));
        }
    }

    static final class ShowController extends BaseResourceController {
        @Override
        public Response show(Request request) {
            return Response.json(Map.of("id", request.getAttribute("id")));
        }
    }

    @Test
    void urlResourcesControllersAndHtmlErrors() {
        Router router = new Router();
        router.get("/users/{id}", request -> Response.text("u")).name("users.show");
        UrlGenerator urls = new UrlGenerator(router).setRoot("https://api.test").setKey("java-key");
        assertEquals("https://api.test/users/3", urls.route("users.show", Map.of("id", "3")));
        assertEquals("https://api.test/users/3", urls.route("users.show", Map.of("id", "3")));
        String signed = urls.signed("/invite", 120L);
        assertTrue(signed.contains("signature="));
        assertTrue(urls.signed("/invite").contains("signature="));
        assertTrue(urls.signedRoute("users.show").contains("/users/"));
        assertTrue(urls.signedRoute("users.show", Map.of("id", "1")).contains("/users/1"));
        assertTrue(urls.signedRoute("users.show", Map.of("id", "1"), null).contains("/users/1"));

        UserResource resource = new UserResource(1, "Ada");
        Response json = resource.toResponse(Request.get("/"));
        assertTrue(json.getBody().contains("Ada"));
        ResourceCollection collection = JsonResource.collection(List.of(resource));
        assertTrue(collection.toResponse(Request.get("/")).getBody().contains("data"));

        CreateUserRequest form = new CreateUserRequest();
        Map<String, Object> data = form.validate(Request.post("/users", "name=Ada"));
        assertEquals("Ada", data.get("name"));
        assertThrows(ValidationException.class, () -> form.validate(Request.post("/users", "name=A")));

        InvokableController invokable = request -> Response.text("go");
        assertEquals("go", Controllers.asHandler(invokable).handle(Request.get("/")).getBody());

        ShowController controller = new ShowController();
        assertEquals(404, assertThrows(NotFoundException.class, () -> controller.index(Request.get("/"))).getStatus());
        assertTrue(controller.show(Request.get("/").setAttribute("id", "9")).getBody().contains("9"));

        Request html = new Request("GET", "/", Map.of("Accept", "text/html"), Map.of(), "");
        Response page = new DefaultExceptionHandler().handle(html, new NotFoundException("gone"));
        assertTrue(page.getHeaders().get("Content-Type").contains("text/html"));
        assertTrue(page.getBody().contains("gone"));

        DefaultExceptionHandler custom = new DefaultExceptionHandler()
            .report(t -> {})
            .render((request, throwable) -> Response.text("handled", 499));
        assertEquals(499, custom.handle(Request.get("/"), new RuntimeException("x")).getStatus());
        assertFalse(urls.hasValidSignature(Request.get("/invite")));
    }
}
