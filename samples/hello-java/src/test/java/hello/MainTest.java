package hello;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import dev.kona.core.Kona;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    @TempDir
    Path temp;

    @AfterEach
    void reset() {
        Kona.reset();
    }

    @Test
    void providerGroupsAndMiddlewareFromJava() {
        Application app = Main.boot(new String[] {"--base=" + temp});
        Main.HelloServiceProvider provider = app.getProvider(Main.HelloServiceProvider.class);
        assertTrue(provider.isBooted());
        assertEquals("Hello, kona", app.resolve(Main.Greeter.class).greet("kona"));

        List<String> lines = Main.demo(app);
        assertEquals("200 Hello from Java Kona [Kona]", lines.get(0));
        assertEquals("401 {\"error\":\"unauthorized\"}", lines.get(1));
        assertEquals("200 {\"hello\":\"Hello, kona\"}", lines.get(2));
        assertEquals("200 {\"routes\":3} [admin=true]", lines.get(3));
        assertEquals("404 [Kona]", lines.get(4));
        assertEquals("route(api.hello) = /api/hello/kona", lines.get(5));
    }

    @Test
    void securitySessionAndAuthDemo() {
        List<String> lines = SecurityDemo.run(new String[] {"--base=" + temp});
        assertEquals("flash1=200:{\"prior\":\"\"}", lines.get(0));
        assertEquals("flash2=200:{\"prior\":\"saved\"}", lines.get(1));
        assertTrue(lines.get(2).startsWith("login=200:"));
        assertTrue(lines.get(2).contains("\"ok\":true"));
        assertTrue(lines.get(2).contains("\"id\":1"));
    }

    @Test
    void supportCacheAndRedisDemo() {
        var result = SupportCacheDemo.run(temp);
        assertEquals("hello-kona", result.get("slug"));
        assertEquals(List.of("Ada", "Grace"), result.get("names"));
        assertEquals("PONG", result.get("redis"));
    }

    @Test
    void httpPolishDemo() {
        var result = HttpPolishDemo.run(new String[] {"--base=" + temp});
        assertTrue(result.get("index").startsWith("200:"));
        assertTrue(result.get("index").contains("Ada"));
        assertTrue(result.get("created").startsWith("201:"));
        assertEquals("https://demo.test/notes", result.get("route"));
        assertEquals("https://demo.test/notes", result.get("signed"));
        assertEquals("pong", result.get("ping"));
    }
}
