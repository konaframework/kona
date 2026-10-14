package dev.kona.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Request;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JavaBrewApiTest {
    @AfterEach
    void resetBrew() {
        Brew.reset();
    }

    public static class Counter extends Component {
        public static final int IGNORED = 99;

        public int count = 0;
        public String label = "n";
        public boolean active = true;
        public byte b = 1;
        public short s = 2;
        public long l = 3L;
        public float f = 4f;
        public double d = 5d;
        public char ch = 'A';
        public String email = "";

        public void increment() {
            count++;
        }

        public void setLabel(Map<String, Object> params) {
            Object v = params.get("label");
            if (v != null) {
                label = v.toString();
            }
        }

        public void greet(String name) {
            label = "hi-" + name;
        }

        @Override
        public String render() {
            return "<p>" + label + "=" + count + "</p>";
        }
    }

    @Test
    void registerDispatchAndRenderFromJava() throws Exception {
        var base = Files.createTempDirectory("kona-brew-java");
        Application app = new Application(base);
        app.register(new HttpServiceProvider());
        app.register(new BrewServiceProvider());
        app.boot();

        BrewManager manager = app.resolve(BrewManager.class);
        manager.register("counter", Counter::new);

        String html = Brew.mount("counter");
        assertTrue(html.contains("data-brew-name=\"counter\""));
        assertTrue(html.contains("<p>n=0</p>"));

        Matcher m = Pattern.compile("data-brew=\"([^\"]+)\"").matcher(html);
        assertTrue(m.find());
        String snapshot = m.group(1);

        BrewManager.UpdateResult updated = manager.update("counter", snapshot, "increment");
        assertTrue(updated.getHtml().contains("<p>n=1</p>"));

        BrewManager.UpdateResult labeled =
                manager.update("counter", updated.getSnapshot(), "setLabel", Map.of("label", "x"));
        assertTrue(labeled.getHtml().contains("<p>x=1</p>"));

        BrewManager.UpdateResult greeted =
                manager.update("counter", labeled.getSnapshot(), "greet", Map.of("value", "Ada"));
        assertTrue(greeted.getHtml().contains("<p>hi-Ada=1</p>"));

        BrewManager.UpdateResult synced =
                manager.update(
                        "counter",
                        greeted.getSnapshot(),
                        "sync",
                        Map.of("email", "a@b.c", "count", 10, "active", false));
        assertTrue(synced.getHtml().contains("<p>hi-Ada=10</p>"));

        Counter restored = new Counter();
        BrewSnapshot.Payload payload = BrewSnapshot.decode(synced.getSnapshot());
        BrewState.write(restored, payload.getData());
        assertEquals("a@b.c", restored.email);
        assertEquals(10, restored.count);
        assertEquals(false, restored.active);

        HttpKernel kernel = app.resolve(HttpKernel.class);
        var script = kernel.handle(Request.get("/brew/brew.js"));
        assertEquals(200, script.getStatus());
        assertTrue(script.getBody().contains("fetch"));

        assertThrows(BrewException.class, () -> manager.getRegistry().resolve("nope"));
    }

    @Test
    void coercePrimitiveWrappersFromJsonNumbers() {
        Counter c = new Counter();
        BrewState.write(
                c,
                Map.of(
                        "b", 9,
                        "s", 8,
                        "l", 7,
                        "f", 6,
                        "d", 5.5,
                        "ch", "Z",
                        "active", true,
                        "count", 42));
        assertEquals(9, c.b);
        assertEquals(8, c.s);
        assertEquals(7L, c.l);
        assertEquals(6f, c.f);
        assertEquals(5.5d, c.d);
        assertEquals('Z', c.ch);
        assertTrue(c.active);
        assertEquals(42, c.count);

        HashMap<String, Object> nulls = new HashMap<>();
        nulls.put("b", null);
        nulls.put("email", null);
        BrewState.write(c, nulls);
        assertEquals(9, c.b);
        assertEquals(null, c.email);

        BrewState.write(c, Map.of("email", 123, "label", "same"));
        assertEquals("123", c.email);
        assertEquals("same", c.label);

        Map<String, Object> state = BrewState.read(c);
        assertTrue(state.containsKey("count"));
        assertTrue(state.containsKey("email"));
    }
}
