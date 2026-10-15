package hello;

import bootstrap.App;
import dev.kona.core.Application;
import dev.kona.core.Kona;
import dev.kona.http.HttpKernel;
import dev.kona.http.Request;
import dev.kona.http.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSmokeTest {
    @AfterEach
    void reset() {
        Kona.reset();
    }

    private Path sampleBase() {
        Path sample = Path.of("samples/hello-java").toAbsolutePath().normalize();
        return Files.isDirectory(Path.of("src/main/java/bootstrap"))
            ? Path.of("").toAbsolutePath().normalize()
            : sample;
    }

    @Test
    void welcomeAndHealth() {
        Path base = sampleBase();
        Application app = App.create(new String[]{"--base=" + base});
        HttpKernel kernel = app.resolve(HttpKernel.class);
        Response home = kernel.handle(Request.get("/"));
        Response health = kernel.handle(Request.get("/api/health"));
        assertEquals(200, home.getStatus());
        assertTrue(home.getBody().toLowerCase().contains("<!doctype html>"), home.getBody());
        assertTrue(home.getBody().toLowerCase().contains("welcome"), home.getBody());
        assertEquals(200, health.getStatus());
        assertEquals("{\"status\":\"ok\"}", health.getBody());
    }

    @Test
    void sampleTreeUsesSrcMainLayout() {
        Path base = sampleBase();
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/bootstrap/Middleware.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/routes/ApiRoutes.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/config/Paths.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/java/config/Session.java")));
        assertTrue(Files.isRegularFile(base.resolve("src/main/resources/views/welcome.crema.html")));
        assertTrue(Files.isDirectory(base.resolve("storage")));
    }
}
