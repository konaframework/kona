package org.konaframework.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.konaframework.core.Application;
import org.konaframework.support.console.ConsoleIO;

public class JavaLogRequestsTest {
    @Test
    void javaCallSites(@TempDir Path base) throws Exception {
        Path root = Files.createDirectories(base.resolve("app"));
        Application app = new Application(root);
        app.getConfig().set("app.debug", true);
        app.getConfig().set("logging.to_file", false);
        app.register(new HttpServiceProvider()).boot();
        assertTrue(RequestLogging.enabled(app));
        assertTrue(
            app.resolve(HttpKernel.class).getGlobalMiddleware().stream()
                .anyMatch(mw -> mw instanceof LogRequests)
        );

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ConsoleIO console = new ConsoleIO(new PrintStream(buf), new PrintStream(buf), false);
        LogRequests logger = new LogRequests(console, false);
        Response response = logger.handle(Request.get("/java"), next -> Response.text("ok", 202));
        assertEquals(202, response.getStatus());
        assertTrue(buf.toString().contains("GET"));
        assertTrue(buf.toString().contains("/java"));
        assertTrue(buf.toString().contains("202"));
        assertEquals(
            "/x?a=1",
            LogRequests.formatTarget(new Request("GET", "/x", Map.of(), Map.of("a", "1")))
        );
        assertEquals(ConsoleIO.YELLOW, LogRequests.statusColor(422));
    }
}
