package org.konaframework.process;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class JavaProcessApiTest {
    @Test
    void runFromJava() {
        assertTrue(Process.run("sh", "-c", "exit 0").successful());
    }
}
