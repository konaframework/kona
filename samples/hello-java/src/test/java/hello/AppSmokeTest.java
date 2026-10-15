package hello;

import bootstrap.App;
import dev.kona.core.Application;
import dev.kona.core.Kona;
import dev.kona.http.HttpKernel;
import dev.kona.http.Request;
import dev.kona.http.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSmokeTest {
    @TempDir
    Path temp;

    @AfterEach
    void reset() {
        Kona.reset();
    }

    @Test
    void welcomeAndHealth() {
        Application app = App.create(new String[]{"--base=" + temp});
        HttpKernel kernel = app.resolve(HttpKernel.class);
        Response home = kernel.handle(Request.get("/"));
        Response health = kernel.handle(Request.get("/api/health"));
        assertEquals(200, home.getStatus());
        assertEquals("Welcome to Kona", home.getBody());
        assertEquals(200, health.getStatus());
        assertEquals("{\"status\":\"ok\"}", health.getBody());
    }

    @Test
    void sampleTreeUsesSrcMainLayout() {
        Path base = Files.isDirectory(Path.of("src/main/java/bootstrap"))
            ? Path.of("").toAbsolutePath().normalize()
            : Path.of("samples/hello-java").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/bootstrap/Middleware.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/routes/ApiRoutes.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/resources/config/paths.json")));
        assertTrue(Files.isDirectory(base.resolve("storage")));
    }
}
