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
}
