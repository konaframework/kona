package io.konaframework.http;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.konaframework.core.Application;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Touch @JvmOverloads constructors for KonaHttpServer from Java. */
public class JavaKonaHttpServerTest {
    @Test
    void constructorsFromJava(@TempDir java.nio.file.Path base) throws Exception {
        Files.createDirectories(base);
        Application app = new Application(base);
        app.register(new HttpServiceProvider()).boot();
        HttpKernel kernel = app.resolve(HttpKernel.class);
        assertNotNull(new KonaHttpServer(kernel));
        assertNotNull(new KonaHttpServer(kernel, "127.0.0.1"));
        assertNotNull(new KonaHttpServer(kernel, "127.0.0.1", 0));
        assertNotNull(new KonaHttpServer(kernel, "127.0.0.1", 0, 1024));
        assertNotNull(new KonaHttpServer(kernel, "127.0.0.1", 0, 1024, "nio"));
    }
}
