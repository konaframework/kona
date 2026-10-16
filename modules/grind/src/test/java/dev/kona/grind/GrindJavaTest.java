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

        Path nested = app.resolve("src/main/java/app/http/controllers");
        Files.createDirectories(nested);
        assertEquals(app, ServeCommand.findProjectRoot(nested));
        assertNull(ServeCommand.findProjectRoot(temp));

        ProcessRunner runner =
                (command, dir, env) -> command.equals(List.of(app.resolve("gradlew").toString(), "run")) ? 0 : 1;
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
        assertTrue(Files.exists(app.resolve("src/main/java/database/migrations/CreateFlightsTable.java")));
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
            info("hello " + argumentOrFail("name"));
            return SUCCESS;
        }
    }
}
