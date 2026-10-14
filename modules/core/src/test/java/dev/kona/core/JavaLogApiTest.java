package dev.kona.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JavaLogApiTest {
    @AfterEach
    void cleanup() {
        Log.flushSharedContext();
        Log.setDefaultChannel("kona");
    }

    @Test
    void channelsAndContextFromJava() {
        Log.setDefaultChannel("java-log");
        Log.shareContext(Map.of("tenant", "acme"));
        Log.channel("billing").info("invoice");
        Log.withContext(Map.of("actor", "java")).warn("notice");
        Log.stack("a", "b").debug("stack");
        Log.info("top");
        assertTrue(Log.sharedContext().containsKey("tenant"));
        assertTrue(Log.sharedContext().containsKey("actor"));
        Log.flushSharedContext();
        assertTrue(Log.sharedContext().isEmpty());
    }
}
