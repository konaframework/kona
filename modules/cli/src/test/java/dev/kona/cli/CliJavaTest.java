package dev.kona.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Java-callable kona CLI API (Law 8). */
class CliJavaTest {
    @TempDir
    Path temp;

    @Test
    void newFromJava() throws Exception {
        KonaVersions versions = KonaVersions.load();
        assertEquals(versions, KonaVersions.load("dev/kona/cli/kona.properties"));
        assertThrows(KonaCliException.class, () -> KonaVersions.load("dev/kona/cli/absent.properties"));

        Path app = temp.resolve("javaapp");
        assertEquals(0, new KonaApplication().run(new String[] {"new", app.toString(), "--lang=java"}));
        assertTrue(Files.readString(app.resolve("build.gradle.kts")).contains("dev.kona:kona-http:" + versions.getKona()));
        assertTrue(Files.isRegularFile(app.resolve("grind")));
    }

    @Test
    void grindProxyFromJava() throws Exception {
        Path app = temp.resolve("proxyapp");
        Files.createDirectories(app);
        Files.writeString(app.resolve("settings.gradle.kts"), "rootProject.name = \"proxyapp\"\n");
        Files.writeString(app.resolve("grind"), "#!/bin/sh\n");
        app.resolve("grind").toFile().setExecutable(true);

        int[] captured = {-1};
        GrindCommand grind =
                new GrindCommand(
                        (command, workingDir) -> {
                            assertEquals(app.resolve("grind").toString(), command.get(0));
                            assertEquals("serve", command.get(1));
                            assertEquals(app, workingDir);
                            captured[0] = 0;
                            return 0;
                        },
                        false,
                        app,
                        new dev.kona.support.console.ConsoleIO());
        assertEquals(0, grind.run(java.util.List.of("serve")));
        assertEquals(0, captured[0]);
        assertEquals(app, GrindCommand.findProjectRoot(app.resolve("nested")));
        // 3-arg @JvmOverloads ctor (defaults ConsoleIO) — must be invoked from Java for kover
        assertEquals(0, new GrindCommand(GrindProcessRunner.SYSTEM, false, app).run(java.util.List.of("--help")));
    }
}
