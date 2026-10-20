package org.konaframework.cli;

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
        assertEquals(versions, KonaVersions.load("org/konaframework/cli/kona.properties"));
        assertThrows(KonaCliException.class, () -> KonaVersions.load("org/konaframework/cli/absent.properties"));

        Path app = temp.resolve("javaapp");
        assertEquals(
            0,
            new KonaApplication()
                .run(new String[] {"new", app.toString(), "--lang=java", "--no-interaction"}));
        String build = Files.readString(app.resolve("build.gradle.kts"));
        assertTrue(build.contains("org.konaframework:kona-http:" + versions.getKona()));
        assertTrue(build.contains("org.xerial:sqlite-jdbc"));
        assertTrue(Files.isRegularFile(app.resolve("grind")));

        Path inertia = temp.resolve("javainertia");
        assertEquals(
            0,
            new KonaApplication()
                .run(
                    new String[] {
                        "new",
                        inertia.toString(),
                        "--lang=java",
                        "--kit=inertia",
                        "--frontend=react",
                        "--css=custom",
                        "--no-interaction"
                    }));
        assertTrue(Files.isRegularFile(inertia.resolve("src/main/resources/js/app.jsx")));
        assertTrue(Files.readString(inertia.resolve("src/main/java/bootstrap/App.java")).contains("app.jsx"));
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
                        new org.konaframework.support.console.ConsoleIO());
        assertEquals(0, grind.run(java.util.List.of("serve")));
        assertEquals(0, captured[0]);
        assertEquals(app, GrindCommand.findProjectRoot(app.resolve("nested")));
        // 3-arg @JvmOverloads ctor (defaults ConsoleIO) — must be invoked from Java for kover
        assertEquals(0, new GrindCommand(GrindProcessRunner.SYSTEM, false, app).run(java.util.List.of("--help")));
    }

    @Test
    void installerPromptOverloadsFromJava() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream stream = new java.io.PrintStream(out);
        InstallerPrompts prompts =
                new InstallerPrompts(
                        new java.io.ByteArrayInputStream("\n\n\n\n\n\n".getBytes()), stream, false);
        prompts.ask("Q");
        prompts.ask("Q", "d");
        prompts.confirm("Q");
        prompts.confirm("Q", true);
        prompts.choice("Q", java.util.List.of("A", "B"));
        prompts.choice("Q", java.util.List.of("A", "B"), 1);
    }

    @Test
    void newCommandOverloadsFromJava() {
        KonaVersions versions = new KonaVersions("0.0.0-test", "2.0.0");
        new KonaVersions("0.0.0-test", "2.0.0", "abc123");
        org.konaframework.support.console.ConsoleIO io =
                new org.konaframework.support.console.ConsoleIO();
        InstallerPrompts prompts = new InstallerPrompts();
        GrindProcessRunner runner = GrindProcessRunner.SYSTEM;
        new NewCommand();
        new NewCommand(versions);
        new NewCommand(versions, io);
        new NewCommand(versions, io, prompts);
        new NewCommand(versions, io, prompts, runner);
        new NewCommand(versions, io, prompts, runner, false);
        new KonaApplication();
        new KonaApplication(io);
        new KonaApplication(io, new NewCommand(versions, io, prompts, runner, false));
        assertTrue(versions.display().startsWith("kona "));
    }

    @Test
    void positionalDirectoryStillPromptsFromJava() {
        Path dest = temp.resolve("app-seven");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream stream = new java.io.PrintStream(out);
        String answers = "\n".repeat(16);
        InstallerPrompts prompts =
                new InstallerPrompts(new java.io.ByteArrayInputStream(answers.getBytes()), stream, false);
        org.konaframework.support.console.ConsoleIO io =
                new org.konaframework.support.console.ConsoleIO(stream, stream, false);
        NewCommand cmd =
                new NewCommand(
                        new KonaVersions("0.0.0-test", "2.0.0"),
                        io,
                        prompts,
                        (command, dir) -> 0,
                        false);
        assertEquals(0, new KonaApplication(io, cmd).run(new String[] {"new", dest.toString()}));
        String text = out.toString();
        assertTrue(text.contains("Language"));
        assertTrue(text.contains("What are we brewing?"));
        assertTrue(text.contains("CSS"));
        assertTrue(text.contains("Database"));
        assertTrue(text.contains("Install dependencies"));
        assertTrue(text.contains("Run initial migrations"));
        assertTrue(text.contains("Run database seeders"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("Where should we create the app?"));
        assertTrue(Files.isRegularFile(dest.resolve("build.gradle.kts")));
    }

    @Test
    void unreadableStdinFromJava() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream stream = new java.io.PrintStream(out);
        InstallerPrompts prompts =
                new InstallerPrompts(new java.io.ByteArrayInputStream(new byte[0]), stream, false);
        org.konaframework.support.console.ConsoleIO io =
                new org.konaframework.support.console.ConsoleIO(stream, stream, false);
        NewCommand cmd = new NewCommand(new KonaVersions("0.0.0-test", "2.0.0"), io, prompts);
        KonaCliException ex =
                assertThrows(
                        KonaCliException.class,
                        () -> cmd.run(java.util.List.of(temp.resolve("eof-java").toString())));
        assertTrue(ex.getMessage().contains("Cannot read stdin"));
        assertTrue(ex.getMessage().contains("--no-interaction"));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(temp.resolve("eof-java")));
    }
}
