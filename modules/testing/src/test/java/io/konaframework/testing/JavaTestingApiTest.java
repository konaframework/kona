package io.konaframework.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.konaframework.http.HttpServiceProvider;
import io.konaframework.http.Response;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

public class JavaTestingApiTest {
    @Test
    void harnessFromJava() throws Exception {
        var base = Files.createTempDirectory("kona-testing-java");
        var app = TestApplication.create(base, new HttpServiceProvider());
        app.resolve(io.konaframework.http.Router.class).get("/j", request -> Response.text("ok"));
        new HttpTestClient(app).get("/j").assertOk();
        assertEquals(0, new ConsoleTester().run("list").getExitCode());
        FakeMail mail = new FakeMail();
        assertTrue(mail.messages().isEmpty());
        FakeQueue queue = new FakeQueue();
        assertTrue(queue.size() >= 0);
        FakeStorage storage = new FakeStorage();
        storage.put("f.txt", "x");
        assertTrue(storage.exists("f.txt"));
        FakeEvent events = new FakeEvent();
        events.dispatch("ping");
        assertEquals(1, events.events().size());
        FakeCache cache = new FakeCache();
        cache.put("k", "v");
        assertEquals("v", cache.get("k"));
        FakeBroadcast broadcast = new FakeBroadcast();
        assertTrue(broadcast.messages().isEmpty());
        app.resolve(io.konaframework.http.Router.class)
                .put("/u", request -> Response.json(java.util.Map.of("ok", true)));
        new HttpTestClient(app).putJson("/u", java.util.Map.of("a", 1)).assertOk().assertJson("ok", true);
    }
}
