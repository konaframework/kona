package dev.kona.grind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void newAndLocateProjectFromJava() throws Exception {
        GrindVersions versions = GrindVersions.load();
        assertEquals(versions, GrindVersions.load("dev/kona/grind/grind.properties"));
        assertThrows(GrindException.class, () -> GrindVersions.load("dev/kona/grind/absent.properties"));

        Path app = temp.resolve("javaapp");
        assertEquals(0, new GrindApplication().run(new String[] {"new", app.toString(), "--lang=java"}));
        assertTrue(Files.readString(app.resolve("build.gradle.kts")).contains("dev.kona:kona-http:" + versions.getKona()));

        Path nested = app.resolve("app/Http/Controllers");
        assertEquals(app, ServeCommand.findProjectRoot(nested));
        assertNull(ServeCommand.findProjectRoot(temp));

        ProcessRunner runner = (command, dir, env) -> command.equals(List.of(app.resolve("gradlew").toString(), "run")) ? 0 : 1;
        assertEquals(0, new ServeCommand(server -> kotlin.Unit.INSTANCE, runner, false).run(List.of("--base=" + app)));
    }
}
