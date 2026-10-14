package dev.kona.broadcasting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaBroadcastApiTest {
    static final class Ping implements ShouldBroadcast {
        @Override
        public List<Channel> broadcastOn() {
            return List.of(Channel.publicChannel("java"));
        }

        @Override
        public String broadcastAs() {
            return "ping";
        }

        @Override
        public Map<String, Object> broadcastWith() {
            return Map.of("ok", true);
        }
    }

    @Test
    void broadcastFromJava() throws Exception {
        var base = Files.createTempDirectory("kona-bcast");
        Application app = new Application(base);
        app.getConfig().set("broadcasting.default", "log");
        app.register(new BroadcastServiceProvider()).boot();
        Broadcast.event(new Ping());
        assertEquals(1, Broadcast.manager().log().messages().size());
        assertTrue(Channel.publicChannel("java").getName().equals("java"));

        app.getConfig().set("broadcasting.connections.websocket.driver", "websocket");
        WebsocketBroadcaster ws = Broadcast.manager().websocket();
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        ws.subscribe("java", (channels, event, payload) -> hits.incrementAndGet());
        Broadcast.manager().connection("websocket").broadcast(
                List.of(Channel.publicChannel("java")), "ping", Map.of("ok", true));
        assertEquals(1, hits.get());
        assertEquals(1, ws.messages().size());
    }
}
