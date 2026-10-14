package dev.kona.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Response;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

public class JavaTestingApiTest {
    @Test
    void harnessFromJava() throws Exception {
        var base = Files.createTempDirectory("kona-testing-java");
        var app = TestApplication.create(base, new HttpServiceProvider());
        app.resolve(dev.kona.http.Router.class).get("/j", request -> Response.text("ok"));
        new HttpTestClient(app).get("/j").assertOk();
        assertEquals(0, new ConsoleTester().run("list").getExitCode());
        FakeMail mail = new FakeMail();
        assertTrue(mail.messages().isEmpty());
        FakeQueue queue = new FakeQueue();
        assertTrue(queue.size() >= 0);
        FakeStorage storage = new FakeStorage();
        storage.put("f.txt", "x");
        assertTrue(storage.exists("f.txt"));
    }
}
