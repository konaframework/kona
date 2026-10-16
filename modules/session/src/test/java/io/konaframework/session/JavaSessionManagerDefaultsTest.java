package io.konaframework.session;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.konaframework.core.Config;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaSessionManagerDefaultsTest {
    @Test
    void twoArgConstructor(@TempDir Path base) {
        Config config = new Config();
        config.set("session.driver", "array");
        SessionManager manager = new SessionManager(config, base);
        assertTrue(manager.driver() instanceof ArraySessionStore);
    }
}
