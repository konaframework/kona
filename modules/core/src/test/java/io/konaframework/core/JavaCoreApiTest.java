package io.konaframework.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JavaCoreApiTest {
    public static final class Clock {
    }

    public static final class Scheduler {
        private final Clock clock;

        @Inject
        public Scheduler(Clock clock) {
            this.clock = clock;
        }

        public Clock clock() {
            return clock;
        }
    }

    static final class RecordingProvider implements ServiceProvider {
        private final List<String> events;

        RecordingProvider(List<String> events) {
            this.events = events;
        }

        @Override
        public void register(Application app) {
            events.add("register");
            app.getContainer().singleton(Clock.class, c -> new Clock());
        }

        @Override
        public void boot(Application app) {
            events.add("boot");
        }
    }

    @AfterEach
    void cleanup() {
        Kona.reset();
    }

    @Test
    void containerFromJava() {
        Container container = new Container();
        container.bind(Clock.class, c -> new Clock());
        container.bind(Clock.class, c -> new Clock(), true);
        assertSame(container.make(Clock.class), container.make(Clock.class));
        assertTrue(container.make(Scheduler.class).clock() != null);
        container.bind("greeting", c -> "hi");
        assertEquals("hi", container.<String>resolve("greeting"));
        assertThrows(BindingException.class, () -> container.resolve("missing"));
        container.bind(Object.class, Clock.class);
        assertTrue(container.make(Object.class) instanceof Clock);
    }

    @Test
    void configFromJava() {
        Config config = new Config();
        config.set("app.debug", true);
        config.set("app.port", "8080");
        assertTrue(config.getBoolean("app.debug", false));
        assertEquals(8080, config.getInt("app.port", 0));
        assertEquals("fallback", config.getString("app.missing", "fallback"));
        assertEquals("fallback", config.get("app.missing", "fallback"));
        assertEquals(Boolean.TRUE, config.get("app.debug"));
        assertEquals(Boolean.TRUE, Config.toBoolean("yes"));
    }

    @Test
    void configFileFromJava() {
        ConfigFile mail = ConfigFile.of("mail", () -> Map.of("driver", "smtp"));
        Config config = new Config();
        config.load(mail);
        assertEquals("smtp", config.getString("mail.driver"));
        assertEquals("mail", mail.key());
    }

    @Test
    void builderAndKonaFromJava() throws Exception {
        Path base = Files.createTempDirectory("kona-java");
        List<String> events = new ArrayList<>();
        Application app = Application.builder(base)
            .withProvider(new RecordingProvider(events))
            .configure(config -> config.set("app.name", "JavaApp"))
            .build();
        assertEquals(List.of("register", "boot"), events);
        assertEquals("JavaApp", app.getConfig().getString("app.name"));
        assertTrue(app.make(Scheduler.class).clock() == app.make(Clock.class));

        List<String> konaEvents = new ArrayList<>();
        Application kona = Kona.create(base)
            .providers(new RecordingProvider(konaEvents))
            .configure(config -> config.set("app.env", "java"))
            .boot();
        assertSame(kona, Kona.app());
        assertEquals("java", kona.getConfig().getString("app.env"));
        assertEquals(List.of("register", "boot"), konaEvents);

        assertTrue(Application.builder().build().isBooted());
        assertTrue(new Application.Builder().build().getBasePath().isAbsolute());

        PathLayout layout = PathLayout.detect(base);
        assertEquals(base.resolve("src/main/kotlin"), layout.getLangRoot());
        assertEquals(PathLayout.KOTLIN_ROOT, PathLayout.detectLangRoot(base));
        Application.builder(base)
            .afterBoot(booted -> assertEquals(booted.appPath(), Paths.app()))
            .boot();
    }
}
