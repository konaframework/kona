package sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bootstrap.App;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.konaframework.core.Kona;
import org.konaframework.http.HttpKernel;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.inertia.Inertia;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class InertiaSampleTest {
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void reset() {
        Inertia.reset();
        Kona.reset();
    }

    private HttpKernel kernel() {
        Path local = Path.of("src/main/java/bootstrap");
        Path base = Files.isDirectory(local) ? Path.of("") : Path.of("samples/inertia-java");
        return App.create(new String[] {"--base=" + base.toAbsolutePath().normalize()})
            .resolve(HttpKernel.class);
    }

    @Test
    void firstVisitReturnsHtmlShell() {
        Response response = kernel().handle(Request.get("/"));
        assertEquals(200, response.getStatus());
        assertTrue(response.getBody().contains("data-page="));
        assertTrue(response.getBody().contains("Pages/Welcome"));
    }

    @Test
    void inertiaVisitReturnsJsonWithSharedProps() throws Exception {
        Response response = kernel().handle(
            new Request(
                "GET",
                "/",
                Map.of("X-Inertia", "true", "X-Inertia-Version", "1"),
                Map.of(),
                ""
            )
        );
        assertEquals(200, response.getStatus());
        assertEquals("true", response.getHeaders().get("X-Inertia"));
        JsonNode page = json.readTree(response.getBody());
        assertEquals("Pages/Welcome", page.get("component").asText());
        assertEquals("Welcome to Kona Inertia", page.get("props").get("message").asText());
        assertEquals("Inertia sample (Java)", page.get("props").get("appName").asText());
    }
}
