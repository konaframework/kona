package org.konaframework.grind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

        Path nested = app.resolve("src/main/java/app/http/controllers");
        Files.createDirectories(nested);
        assertEquals(app, ServeCommand.findProjectRoot(nested));
        assertNull(ServeCommand.findProjectRoot(temp));

        ProcessRunner runner =
                (command, dir, env) -> command.equals(
                                List.of(
                                        app.resolve("gradlew").toString(),
                                        "--quiet",
                                        "--console=plain",
                                        "-Dorg.gradle.console=plain",
                                        "run"))
                        ? 0
                        : 1;
        assertEquals(0, new ServeCommand(server -> kotlin.Unit.INSTANCE, runner, false).run(List.of("--base=" + app)));

        assertEquals(1, new GrindApplication().run(new String[] {"new", "x"}));
        assertTrue(new GrindApplication().run(new String[] {"help"}) == 0);

        Path linkBase = temp.resolve("linkapp");
        Files.createDirectories(linkBase.resolve("src/main/resources/public"));
        assertEquals(0, new StorageLinkCommand().run(List.of("--base=" + linkBase)));
        assertTrue(Files.isSymbolicLink(linkBase.resolve("src/main/resources/public/storage")));
    }

    @Test
    void extractGeneratorsFromJava() throws Exception {
        Path app = temp.resolve("javamodels");
        Files.createDirectories(app.resolve("src/main/java/app"));
        Files.writeString(app.resolve("src/main/java/app/Existing.java"), "class Existing {}");
        assertEquals(0, new MakeModelCommand().run(List.of("Flight", "--migration", "--factory", "--base=" + app)));
        assertTrue(
                Files.readString(app.resolve("src/main/java/app/models/Flight.java")).contains("extends Cadence<Flight>"));
        try (var migrations = Files.list(app.resolve("src/main/java/database/migrations"))) {
            assertTrue(migrations.anyMatch(path -> path.getFileName().toString().endsWith("_create_flights_table.java")));
        }
        assertTrue(Files.exists(app.resolve("src/main/java/database/factories/FlightFactory.java")));
        assertEquals(0, new MakeMigrationCommand().run(List.of("add_gate_to_flights_table", "--base=" + app)));
        assertEquals(0, new MakeSeederCommand().run(List.of("DatabaseSeeder", "--base=" + app)));
        assertEquals(0, new MakeFactoryCommand().run(List.of("Pilot", "--base=" + app)));
        assertEquals(1, new GrindApplication().run(new String[] {"migrate", "--base=" + temp.resolve("missing")}));
        assertEquals(SeedCommand.DEFAULT_SEEDER, "database.seeders.DatabaseSeeder");
        assertEquals(MigrateCommand.Action.ROLLBACK, MigrateCommand.Action.valueOf("ROLLBACK"));
    }

    @Test
    void keyGenerateFromJava() throws Exception {
        Path app = temp.resolve("keyapp");
        Files.createDirectories(app);
        Files.writeString(app.resolve("settings.gradle.kts"), "rootProject.name = \"keyapp\"\n");
        Files.writeString(app.resolve(".env"), "APP_KEY=\n");
        String shown = KeyGenerateCommand.generateAppKey();
        assertTrue(shown.startsWith("base64:"));
        assertEquals(0, new KeyGenerateCommand(app).run(List.of("--base=" + app)));
        assertTrue(Files.readString(app.resolve(".env")).contains("APP_KEY=base64:"));
        assertEquals(0, new GrindApplication().run(new String[] {"key:generate", "--show"}));
    }

    @Test
    void consoleCommandFromJava() {
        CommandRegistry registry = new CommandRegistry().register(JavaDemoCommand.class);
        assertEquals(0, new CommandExecutor(temp).run(new JavaDemoCommand(), java.util.List.of("Ada")));
        assertEquals(0, Grind.call("demo:java", java.util.List.of("Bea"), registry, temp));
        assertEquals(0, MakeCommandCommand.DISCOVERY.equals("META-INF/kona/commands") ? 0 : 1);
        assertEquals(0, new GrindApplication(registry, temp).run(new String[] {"demo:java", "Cia", "--help"}));
    }

    @Test
    void quietConsoleAndTaskLineFromJava() {
        assertFalse(GrindLogging.wantsVerbose(Map.of()));
        assertTrue(GrindLogging.wantsVerbose(Map.of("verbose", "true")));
        assertTrue(GrindLogging.wantsVerbose(Map.of("v", "true")));
        GrindLogging.hushConsole(true);
        GrindLogging.hushConsole(false, "not-a-logger");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GrindIO io = new GrindIO(new PrintStream(out), new PrintStream(out), Verbosity.NORMAL, false);
        io.taskLine("create_users_table", "DONE", 12L, true);
        io.taskLine("WidgetSeeder", "DONE", 4L);
        io.taskLine("DatabaseSeeder", "DONE");
        io.intro("grind");
        io.outro("bye");
        io.note("n");
        io.note("w", "WARN");
        io.promptFrame("Q", List.of("a"));
        io.promptFrame("Q", List.of("a"), 0);
        io.promptSubmitted("Q", "a");
        assertTrue(out.toString().contains("create_users_table"));
        assertTrue(out.toString().contains("grind"));
        assertTrue(out.toString().contains("DONE"));
        assertTrue(out.toString().contains("12ms"));
        assertEquals("Ran", io.paint("x", "Ran"));
    }

    public static class JavaDemoCommand extends ConsoleCommand {
        @Override
        public String getSignature() {
            return "demo:java {name}";
        }

        @Override
        public String getDescription() {
            return "Java demo command";
        }

        @Override
        public int handle() {
            intro("demo");
            note("hello " + argumentOrFail("name"));
            note("next", "INFO");
            outro("ok");
            info("hello " + argumentOrFail("name"));
            return SUCCESS;
        }
    }
}
