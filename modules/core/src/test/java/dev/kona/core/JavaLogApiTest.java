package dev.kona.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JavaLogApiTest {
    @AfterEach
    void cleanup() {
        Log.unbind();
        Log.flushSharedContext();
        Log.setDefaultChannel("stack");
        FileLogging.resetForTests();
        Kona.reset();
    }

    @Test
    void channelsAndContextFromJava() {
        Log.setDefaultChannel("java-log");
        Log.shareContext(Map.of("tenant", "acme"));
        Log.channel("billing").info("invoice");
        Log.withContext(Map.of("actor", "java")).warn("notice");
        Log.stack("a", "b").debug("stack");
        Log.info("top");
        Log.warning("warn");
        Log.notice("notice");
        Log.emergency("emergency");
        Log.alert("alert");
        Log.critical("critical");
        Log.log("debug", "level-string");
        Log.log(LogLevel.INFO, "level-enum");
        assertTrue(Log.sharedContext().containsKey("tenant"));
        assertTrue(Log.sharedContext().containsKey("actor"));
        Log.flushSharedContext();
        assertTrue(Log.sharedContext().isEmpty());
    }

    @Test
    void managerDriversFromJava() throws Exception {
        Path base = Files.createTempDirectory("kona-java-log");
        try {
            Application app = Kona.create(base).boot();
            LogManager manager = app.resolve(LogManager.class);
            assertEquals("stack", manager.defaultChannel());
            Log.channel("single").info("java-single");
            Log.channel("daily").info("java-daily");
            Log.channel("null").info("java-null");
            Log.stack("single", "null").info("java-stack");
            String text = Files.readString(app.storagePath("logs", "kona.log"));
            assertTrue(text.contains("java-single"));
            assertTrue(text.contains("java-daily"));
            assertTrue(text.contains("java-stack"));
        } finally {
            deleteRecursively(base);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
