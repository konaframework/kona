package dev.kona.drip;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

public class JavaDripApiTest {
    @Test
    void getFromJava() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        try {
            Drip drip = new Drip().baseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            assertTrue(drip.get("/").successful());
            assertTrue(drip.post("/", Map.of("a", 1)).successful());
            assertTrue(drip.patch("/", Map.of("p", 1)).successful());
            assertTrue(drip.patch("/").successful());
        } finally {
            server.stop(0);
        }
    }
}
