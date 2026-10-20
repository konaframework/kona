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
import org.konaframework.core.Kona;
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
        Files.createDirectories(linkBase.resolve("public"));
        assertEquals(0, new StorageLinkCommand().run(List.of("--base=" + linkBase)));
        assertTrue(Files.isSymbolicLink(linkBase.resolve("public/storage")));
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
        assertEquals(0, new MakeControllerCommand().run(List.of("Order", "--resource", "--lang=java", "--base=" + app)));
        assertTrue(
                Files.readString(app.resolve("src/main/java/app/http/controllers/OrderController.java"))
                        .contains("extends BaseResourceController"));
        assertEquals(0, new MakeMigrationCommand().run(List.of("add_gate_to_flights_table", "--base=" + app)));
        assertEquals(0, new MakeSeederCommand().run(List.of("DatabaseSeeder", "--base=" + app)));
        assertEquals(0, new MakeFactoryCommand().run(List.of("Pilot", "--base=" + app)));
        assertEquals(1, new GrindApplication().run(new String[] {"migrate", "--base=" + temp.resolve("missing")}));
        assertEquals(1, new GrindApplication().run(new String[] {"migrate:fresh", "--base=" + temp.resolve("missing")}));
        assertEquals(1, new GrindApplication().run(new String[] {"db:wipe", "--base=" + temp.resolve("missing")}));
        assertEquals(1, new GrindApplication().run(new String[] {"db:show", "--base=" + temp.resolve("missing")}));
        assertEquals(SeedCommand.DEFAULT_SEEDER, "database.seeders.DatabaseSeeder");
        assertEquals(MigrateCommand.Action.ROLLBACK, MigrateCommand.Action.valueOf("ROLLBACK"));
        assertEquals(MigrateCommand.Action.FRESH, MigrateCommand.Action.valueOf("FRESH"));
        assertEquals(MigrateCommand.Action.REFRESH, MigrateCommand.Action.valueOf("REFRESH"));
        assertEquals(MigrateCommand.Action.INSTALL, MigrateCommand.Action.valueOf("INSTALL"));
        assertEquals(MigrateCommand.Action.WIPE, MigrateCommand.Action.valueOf("WIPE"));
        assertEquals(DatabaseInspectCommand.Action.DUMP, DatabaseInspectCommand.Action.valueOf("DUMP"));
        assertEquals(DatabaseInspectCommand.Action.TABLE, DatabaseInspectCommand.Action.valueOf("TABLE"));
        assertEquals("database/schema.sql", DatabaseInspectCommand.DEFAULT_DUMP_PATH);
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
    void routeCommandsFromJava() throws Exception {
        Path app = temp.resolve("routed");
        Files.createDirectories(app.resolve("build/classes/kotlin/main"));
        Files.writeString(app.resolve("settings.gradle.kts"), "rootProject.name = \"routed\"\n");
        String boot = "--bootstrap=org.konaframework.grind.FixtureApp";
        assertEquals(
                0,
                new RouteCommand(RouteCommand.Action.LIST)
                        .run(List.of("--base=" + app, boot, "--json")));
        assertEquals(
                0,
                new GrindApplication()
                        .run(new String[] {"route:list", "--base=" + app, boot, "--quiet"}));
        assertEquals(0, new RouteCommand(RouteCommand.Action.CACHE).run(List.of("--base=" + app, boot)));
        assertTrue(Files.isRegularFile(app.resolve(RouteCommand.DEFAULT_CACHE_PATH)));
        assertEquals(0, new RouteCommand(RouteCommand.Action.CLEAR).run(List.of("--base=" + app)));
        assertEquals(RouteCommand.Action.LIST, RouteCommand.Action.valueOf("LIST"));
        assertEquals(RouteCommand.Action.CACHE, RouteCommand.Action.valueOf("CACHE"));
        assertEquals(RouteCommand.Action.CLEAR, RouteCommand.Action.valueOf("CLEAR"));
        assertEquals("bootstrap/cache/routes.json", RouteCommand.DEFAULT_CACHE_PATH);
        Kona.reset();
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
        Prompts prompts =
                new Prompts(
                        new java.io.ByteArrayInputStream("Ada\n\ny\n2\n".getBytes()),
                        new PrintStream(out),
                        false);
        assertEquals("Ada", prompts.ask("Name"));
        assertEquals("Kona", prompts.ask("Name", "Kona"));
        assertTrue(prompts.confirm("Ok?", false));
        assertEquals("redis", prompts.choice("Driver", List.of("array", "redis"), 0));
        new Prompts();
        new Prompts(new java.io.ByteArrayInputStream(new byte[0]));
        new Prompts(new java.io.ByteArrayInputStream(new byte[0]), new PrintStream(out));
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
