package dev.kona.grind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Java-callable grind API (Law 8). */
class GrindJavaTest {
    @TempDir
    Path temp;

    @Test
    void serveAndLocateProjectFromJava() throws Exception {
        Path app = temp.resolve("javaapp");
        Files.createDirectories(app);
        Files.writeString(app.resolve("settings.gradle.kts"), "rootProject.name = \"javaapp\"\n");
        Files.writeString(app.resolve("gradlew"), "#!/bin/sh\n");
        app.resolve("gradlew").toFile().setExecutable(true);

        Path nested = app.resolve("app/Http/Controllers");
        Files.createDirectories(nested);
        assertEquals(app, ServeCommand.findProjectRoot(nested));
        assertNull(ServeCommand.findProjectRoot(temp));

        ProcessRunner runner =
                (command, dir, env) -> command.equals(List.of(app.resolve("gradlew").toString(), "run")) ? 0 : 1;
        assertEquals(0, new ServeCommand(server -> kotlin.Unit.INSTANCE, runner, false).run(List.of("--base=" + app)));

        assertEquals(1, new GrindApplication().run(new String[] {"new", "x"}));
        assertTrue(new GrindApplication().run(new String[] {"help"}) == 0);
    }
}
