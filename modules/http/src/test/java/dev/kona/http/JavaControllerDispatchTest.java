package dev.kona.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaControllerDispatchTest {
    public static final class NotesController {
        public Response index(Request request) {
            return Response.text("notes");
        }

        public Response store(Request request) {
            return Response.text("stored");
        }
    }

    public static final class NoteResource extends JsonResource {
        public NoteResource(Object resource) {
            super(resource);
        }

        @Override
        public Map<String, Object> toArray(Request request) {
            // Hit @JvmOverloads whenLoaded(String, Object) from Java
            Object comments = whenLoaded("comments", List.of("a"));
            return Map.of(
                "value", String.valueOf(getResource()),
                "comments", comments == MissingValue.INSTANCE ? "missing" : comments
            );
        }
    }

    public static final class NotesApiController extends BaseResourceController {
        @Override
        public Response index(Request request) {
            return new NoteResource("n1").toResponse(request);
        }

        @Override
        public Response show(Request request) {
            return new NoteResource(request.getAttribute("id")).toResponse(request);
        }
    }

    @TempDir
    Path temp;

    @Test
    void classAndStringActionsPlusApiResource() {
        Application app = new Application(temp);
        app.register(new HttpServiceProvider());
        app.boot();
        Router router = app.resolve(Router.class);

        // IDE-friendly: instantiate + method reference (recommended)
        NotesController notes = new NotesController();
        router.get("/typed", notes::index);

        // Concise: Class + method / Controller@method
        router.get("/class", NotesController.class, "index");
        router.post("/class", NotesController.class.getName() + "@store");

        router.apiResource("notes", NotesApiController.class);

        HttpKernel kernel = app.resolve(HttpKernel.class);
        assertEquals("notes", kernel.handle(Request.get("/typed")).getBody());
        assertEquals("notes", kernel.handle(Request.get("/class")).getBody());
        assertEquals("stored", kernel.handle(Request.post("/class")).getBody());

        Response list = kernel.handle(Request.get("/notes"));
        assertTrue(list.getBody().contains("n1"));
        assertTrue(list.getBody().contains("data"));

        Response show = kernel.handle(Request.get("/notes/42"));
        assertTrue(show.getBody().contains("42"));

        // Static forwarders on JsonResource (not only Companion)
        JsonResource.wrap("payload");
        assertEquals("payload", JsonResource.wrapping());
        JsonResource.withoutWrapping();
        assertEquals(null, JsonResource.wrapping());
        Map<String, Object> bare = new NoteResource("x").resolve(Request.get("/"));
        assertEquals("x", bare.get("value"));
        JsonResource.flushState();
        assertEquals("data", JsonResource.wrapping());

        ResourceCollection collection = JsonResource.collection(List.of(new NoteResource("a")));
        assertTrue(collection.toResponse(Request.get("/")).getBody().contains("data"));
    }
}
