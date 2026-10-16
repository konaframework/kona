package io.konaframework.grind

import io.konaframework.core.Kona
import io.konaframework.cadence.ConnectionConfig
import io.konaframework.cadence.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class GrindCadenceTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() {
        Database.clearGlobal()
        Kona.reset()
    }

    private fun capture(block: () -> Int): Pair<Int, String> {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        try {
            return block() to buffer.toString()
        } finally {
            System.setOut(original)
        }
    }

    private fun grind(vararg args: String): Pair<Int, String> = capture { GrindApplication().run(arrayOf(*args)) }

    private fun read(base: Path, relative: String): String = Files.readString(base.resolve(relative))

    @Test
    fun makeMigrationInfersCreateAlterAndBlank() {
        val base = temp.resolve("app")
        Files.createDirectories(base)
        assertEquals(0, grind("make:migration", "create_flights_table", "--base=$base", "--lang=kotlin").first)
        val create = read(base, "src/main/kotlin/database/migrations/CreateFlightsTable.kt")
        assertTrue(create.contains("class CreateFlightsTable : Migration"))
        assertTrue(create.contains("        schema.create(\"flights\") {\n            id()\n            timestamps()\n        }\n"))
        assertTrue(create.contains("schema.dropIfExists(\"flights\")"))

        grind("make:migration", "add_gate_to_flights_table", "--base=$base", "--lang=kotlin")
        assertTrue(read(base, "src/main/kotlin/database/migrations/AddGateToFlightsTable.kt").contains("schema.table(\"flights\") {"))
        grind("make:migration", "tweak_things", "--base=$base", "--lang=kotlin")
        assertTrue(read(base, "src/main/kotlin/database/migrations/TweakThings.kt").contains("// schema.create(\"table\") { id() }"))
        grind("make:migration", "seats", "--create=seats", "--base=$base", "--lang=java")
        val javaCreate = read(base, "src/main/java/database/migrations/Seats.java")
        assertTrue(javaCreate.contains("public class Seats implements Migration"))
        assertTrue(javaCreate.contains("        schema.create(\"seats\", table -> {\n            table.id();"))
        grind("make:migration", "widen_seats", "--table=seats", "--base=$base", "--lang=java")
        assertTrue(read(base, "src/main/java/database/migrations/WidenSeats.java").contains("schema.table(\"seats\", table -> {"))
        grind("make:migration", "noop_java", "--base=$base", "--lang=java")
        assertTrue(read(base, "src/main/java/database/migrations/NoopJava.java").contains("// schema.dropIfExists(\"table\");"))

        val (code, out) = grind("make:migration", "create_flights_table", "--base=$base", "--lang=kotlin")
        assertEquals(1, code)
        assertEquals("", out)
        assertEquals(1, grind("make:migration").first)
        assertEquals(1, grind("make:migration", "Bad-Name", "--base=$base").first)
        assertEquals(1, grind("make:migration", "../evil", "--base=$base").first)
    }

    @Test
    fun makeModelWithCompanions() {
        val base = temp.resolve("kt")
        Files.createDirectories(base)
        val (code, out) = grind("make:model", "BlogPost", "--all", "--base=$base")
        assertEquals(0, code)
        assertTrue(out.contains("Model created: src/main/kotlin/app/models/BlogPost.kt"))
        assertTrue(read(base, "src/main/kotlin/app/models/BlogPost.kt").contains("companion object : CadenceCompanion<BlogPost>(BlogPost::class.java)"))
        assertTrue(read(base, "src/main/kotlin/database/migrations/CreateBlogPostsTable.kt").contains("schema.create(\"blog_posts\")"))
        assertTrue(read(base, "src/main/kotlin/database/factories/BlogPostFactory.kt").contains("class BlogPostFactory : Factory<BlogPost>(BlogPost::class.java)"))
        assertTrue(read(base, "src/main/kotlin/database/seeders/BlogPostSeeder.kt").contains("class BlogPostSeeder : Seeder()"))

        grind("make:model", "Category", "--migration", "--base=$base")
        assertTrue(Files.exists(base.resolve("src/main/kotlin/database/migrations/CreateCategoriesTable.kt")))
        assertFalse(Files.exists(base.resolve("src/main/kotlin/database/factories/CategoryFactory.kt")))
        grind("make:model", "Box", "--factory", "--base=$base")
        assertTrue(Files.exists(base.resolve("src/main/kotlin/database/factories/BoxFactory.kt")))
        grind("make:model", "Plain", "--seeder", "--base=$base")
        assertTrue(Files.exists(base.resolve("src/main/kotlin/database/seeders/PlainSeeder.kt")))

        val javaBase = temp.resolve("java")
        Files.createDirectories(javaBase.resolve("src/main/java/app"))
        Files.writeString(javaBase.resolve("src/main/java/app/Existing.java"), "class Existing {}")
        grind("make:model", "Flight", "--all", "--base=$javaBase")
        assertTrue(read(javaBase, "src/main/java/app/models/Flight.java").contains("public static final CadenceCompanion<Flight> QUERY = Cadence.of(Flight.class);"))
        assertTrue(read(javaBase, "src/main/java/database/migrations/CreateFlightsTable.java").contains("implements Migration"))
        assertTrue(read(javaBase, "src/main/java/database/factories/FlightFactory.java").contains("super(Flight.class);"))
        assertTrue(read(javaBase, "src/main/java/database/seeders/FlightSeeder.java").contains("public class FlightSeeder extends Seeder"))

        assertEquals(1, grind("make:model", "Flight", "--base=$javaBase").first)
        assertEquals(1, grind("make:model").first)
        assertEquals(1, grind("make:model", "bad name", "--base=$base").first)
    }

    @Test
    fun makeFactoryAndSeeder() {
        val base = temp.resolve("fs")
        Files.createDirectories(base)
        grind("make:factory", "Pilot", "--base=$base", "--lang=kotlin")
        assertTrue(read(base, "src/main/kotlin/database/factories/PilotFactory.kt").contains("import app.models.Pilot"))
        grind("make:factory", "CrewFactory", "--model=Person", "--base=$base", "--lang=java")
        assertTrue(read(base, "src/main/java/database/factories/CrewFactory.java").contains("extends Factory<Person>"))
        assertEquals(1, grind("make:factory").first)
        assertEquals(1, grind("make:factory", "X-Y", "--base=$base").first)
        assertEquals(1, grind("make:factory", "Ok", "--model=no good", "--base=$base").first)

        grind("make:seeder", "Pilot", "--base=$base", "--lang=kotlin")
        assertTrue(Files.exists(base.resolve("src/main/kotlin/database/seeders/PilotSeeder.kt")))
        grind("make:seeder", "DatabaseSeeder", "--base=$base", "--lang=java")
        assertTrue(read(base, "src/main/java/database/seeders/DatabaseSeeder.java").contains("// call(OtherSeeder.class);"))
        assertEquals(1, grind("make:seeder").first)
        assertEquals(1, grind("make:seeder", "1Bad", "--base=$base").first)
    }

    @Test
    fun migrateRollbackResetAndStatusWithBoundDatabase() {
        val project = databaseProject(temp.resolve("bound"), "grind_bound")
        val boot = "--bootstrap=io.konaframework.grind.CadenceFixtureApp"
        val status0 = grind("migrate:status", "--base=$project", boot)
        assertEquals(0, status0.first)
        assertTrue(status0.second.contains("Pending"))
        assertTrue(status0.second.contains("CreateWidgetsTable"))

        val migrated = grind("migrate", "--base=$project", boot)
        assertEquals(0, migrated.first)
        assertTrue(migrated.second.contains("Migrated: CreateWidgetsTable"))
        assertTrue(migrated.second.contains("Migrated: AddColorToWidgetsTable"))
        assertTrue(grind("migrate", "--base=$project", boot).second.contains("Nothing to migrate."))
        val statusRan = grind("migrate:status", "--base=$project", boot).second
        assertTrue(statusRan.contains("Ran"))
        assertTrue(statusRan.contains("CreateWidgetsTable"))

        val seeded = grind("db:seed", "--base=$project", boot, "--class=io.konaframework.grind.FixtureDatabaseSeeder")
        assertEquals(0, seeded.first)
        assertTrue(seeded.second.contains("Seeding: FixtureDatabaseSeeder"))
        assertTrue(seeded.second.contains("Seeding: WidgetSeeder"))
        assertTrue(seeded.second.contains("Database seeding completed successfully."))
        assertEquals(1L, Database.global().table("widgets").count())

        assertEquals(1, grind("migrate:rollback", "--base=$project", boot, "--step=0").first)
        assertEquals(1, grind("migrate:rollback", "--base=$project", boot, "--step=x").first)
        val rolled = grind("migrate:rollback", "--base=$project", boot, "--step=1")
        assertTrue(rolled.second.contains("Rolled back: AddColorToWidgetsTable"))
        assertTrue(rolled.second.contains("Rolled back: CreateWidgetsTable"))
        assertTrue(grind("migrate:rollback", "--base=$project", boot).second.contains("Nothing to rollback."))
        grind("migrate", "--base=$project", boot)
        assertTrue(grind("migrate:reset", "--base=$project", boot).second.contains("Rolled back: CreateWidgetsTable"))
        assertTrue(grind("migrate:reset", "--base=$project", boot).second.contains("Nothing to rollback."))

        assertTrue(grind("migrate", "--base=$project", boot, "--database=other").second.contains("Migrated: CreateWidgetsTable"))
        assertEquals(1, grind("migrate", "--base=$project", boot, "--database=missing").first)
    }

    @Test
    fun migrateBuildsDatabaseFromConfigWhenUnbound() {
        val project = databaseProject(temp.resolve("config"), "grind_config")
        val boot = "--bootstrap=io.konaframework.grind.ConfigOnlyFixtureApp"
        assertTrue(grind("migrate", "--base=$project", boot).second.contains("Migrated: CreateWidgetsTable"))
        val seeded = grind("db:seed", "--base=$project", boot, "--class=io.konaframework.grind.WidgetSeeder")
        assertEquals(0, seeded.first)
        Database().addConnection("check", ConnectionConfig("jdbc:h2:mem:grind_config;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")).use {
            assertEquals(1L, it.connection("check").table("widgets").count())
        }
    }

    @Test
    fun databaseCommandErrors() {
        val project = databaseProject(temp.resolve("errors"), "grind_errors")
        val empty = temp.resolve("nothing")
        Files.createDirectories(empty)
        assertEquals(1, grind("migrate", "--base=$empty").first)
        assertEquals(1, grind("db:seed", "--base=$empty").first)
        assertEquals(1, grind("migrate", "--base=$project", "--bootstrap=io.konaframework.grind.InstanceCreateApp").first)
        assertEquals(1, grind("migrate", "--base=$project", "--bootstrap=io.konaframework.grind.FixtureApp").first)

        val boot = "--bootstrap=io.konaframework.grind.CadenceFixtureApp"
        assertEquals(1, grind("db:seed", "--base=$project", boot).first)
        assertEquals(1, grind("db:seed", "--base=$project", boot, "--class=io.konaframework.grind.NotASeeder").first)
        assertEquals(SeedCommand.DEFAULT_SEEDER, "database.seeders.DatabaseSeeder")
        assertThrows(GrindException::class.java) { MigrateCommand(MigrateCommand.Action.MIGRATE).run(listOf("--base=$empty")) }
    }

    @Test
    fun statusWithNoMigrations() {
        val project = databaseProject(temp.resolve("none"), "grind_none")
        val (code, out) = grind("migrate:status", "--base=$project", "--bootstrap=io.konaframework.grind.EmptyRegistryApp")
        assertEquals(0, code)
        assertTrue(out.contains("No migrations registered."))
    }
}
