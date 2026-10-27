package org.konaframework.grind

import org.konaframework.core.Kona
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.schema.Schema
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

    private fun migrationFile(base: Path, descriptive: String, lang: String = "kotlin"): Path {
        val dir = base.resolve(Names.languageRoot(lang)).resolve("database/migrations")
        val ext = Names.extension(lang)
        val snake = Names.snake(descriptive)
        val match = Files.list(dir).use { stream ->
            stream.filter { path ->
                val name = path.fileName.toString()
                name == "$descriptive.$ext" ||
                    name == "$snake.$ext" ||
                    name.endsWith("_$descriptive.$ext") ||
                    name.endsWith("_$snake.$ext")
            }.findFirst().orElseThrow {
                IllegalStateException("No migration *$snake.$ext under $dir")
            }
        }
        return match
    }

    @Test
    fun makeMigrationInfersCreateAlterAndBlank() {
        val base = temp.resolve("app")
        Files.createDirectories(base)
        val (created, createOut) = grind("make:migration", "create_flights_table", "--base=$base", "--lang=kotlin")
        assertEquals(0, created)
        assertTrue(createOut.contains("Registered in META-INF/kona/migrations"))
        val createFile = migrationFile(base, "CreateFlightsTable")
        assertTrue(createFile.fileName.toString().matches(Regex("""\d{4}_\d{2}_\d{2}_\d{6}_create_flights_table\.kt""")))
        val create = Files.readString(createFile)
        val createStem = createFile.fileName.toString().removeSuffix(".kt")
        assertTrue(create.contains("@MigrationName(\"$createStem\")"))
        assertTrue(create.contains("class CreateFlightsTable : Migration"))
        assertTrue(create.contains("        schema.create(\"flights\") {\n            id()\n            timestamps()\n        }\n"))
        assertTrue(create.contains("schema.dropIfExists(\"flights\")"))
        assertTrue(read(base, "src/main/resources/META-INF/kona/migrations").contains("database.migrations.CreateFlightsTable"))

        grind("make:migration", "AddGateToFlightsTable", "--base=$base", "--lang=kotlin")
        assertTrue(Files.readString(migrationFile(base, "AddGateToFlightsTable")).contains("schema.table(\"flights\") {"))
        grind("make:migration", "tweak_things", "--base=$base", "--lang=kotlin")
        assertTrue(Files.readString(migrationFile(base, "TweakThings")).contains("// schema.create(\"table\") { id() }"))
        grind("make:migration", "seats", "--create=seats", "--base=$base", "--lang=java")
        val javaCreate = Files.readString(migrationFile(base, "Seats", "java"))
        assertTrue(javaCreate.contains("class Seats implements Migration"))
        assertFalse(javaCreate.contains("public class"))
        assertTrue(javaCreate.contains("        schema.create(\"seats\", table -> {\n            table.id();"))
        grind("make:migration", "widen_seats", "--table=seats", "--base=$base", "--lang=java")
        assertTrue(Files.readString(migrationFile(base, "WidenSeats", "java")).contains("schema.table(\"seats\", table -> {"))
        grind("make:migration", "noop_java", "--base=$base", "--lang=java")
        assertTrue(Files.readString(migrationFile(base, "NoopJava", "java")).contains("// schema.dropIfExists(\"table\");"))

        val clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-20T14:15:30Z"), java.time.ZoneOffset.UTC)
        val stamped = temp.resolve("stamped")
        Files.createDirectories(stamped)
        assertEquals(
            0,
            MakeMigrationCommand(clock).run(listOf("CreateFlightsTable", "--base=$stamped", "--lang=kotlin")),
        )
        val flightsStem = "2026_10_20_141530_create_flights_table"
        val flightsFile = stamped.resolve("src/main/kotlin/database/migrations/$flightsStem.kt")
        assertTrue(Files.isRegularFile(flightsFile))
        assertTrue(Files.readString(flightsFile).contains("class CreateFlightsTable : Migration"))
        val duplicate = assertThrows(GrindException::class.java) {
            MakeMigrationCommand(clock).run(listOf("create_flights_table", "--base=$stamped", "--lang=kotlin"))
        }
        assertEquals("Migration class CreateFlightsTable already exists", duplicate.message)

        assertEquals(
            0,
            MakeMigrationCommand(clock).run(listOf("AddStatusToUsersTable", "--base=$stamped", "--lang=kotlin")),
        )
        assertTrue(
            Files.isRegularFile(
                stamped.resolve("src/main/kotlin/database/migrations/2026_10_20_141530_add_status_to_users_table.kt"),
            ),
        )
        assertEquals(
            0,
            MakeMigrationCommand(clock).run(
                listOf("2026_10_20_141540_create_gates_table", "--base=$stamped", "--lang=kotlin"),
            ),
        )
        assertTrue(
            Files.isRegularFile(
                stamped.resolve("src/main/kotlin/database/migrations/2026_10_20_141540_create_gates_table.kt"),
            ),
        )

        val destDir = stamped.resolve("src/main/kotlin/database/migrations")
        Files.writeString(destDir.resolve("2026_10_20_141550_create_airports_table.kt"), "class Placeholder\n")
        assertEquals(
            0,
            MakeMigrationCommand(clock).run(
                listOf("M2026_10_20_141550_CreateAirportsTable", "--base=$stamped", "--lang=kotlin"),
            ),
        )
        assertTrue(Files.isRegularFile(destDir.resolve("2026_10_20_141551_create_airports_table.kt")))
        assertTrue(
            Files.readString(destDir.resolve("2026_10_20_141551_create_airports_table.kt"))
                .contains("@MigrationName(\"2026_10_20_141551_create_airports_table\")"),
        )

        val meta = stamped.resolve("src/main/resources/META-INF/kona/migrations")
        Files.delete(flightsFile)
        val recreateOut = capture {
            MakeMigrationCommand(clock).run(listOf("CreateFlightsTable", "--base=$stamped", "--lang=kotlin"))
        }
        assertEquals(0, recreateOut.first)
        assertFalse(recreateOut.second.contains("Registered in META-INF/kona/migrations"))
        assertEquals(
            1,
            Files.readString(meta).lineSequence().count { it.trim() == "database.migrations.CreateFlightsTable" },
        )
        assertTrue(Files.readString(meta).contains("database.migrations.CreateAirportsTable"))

        Files.writeString(meta, Files.readString(meta).trimEnd())
        assertEquals(
            0,
            MakeMigrationCommand(clock).run(listOf("CreateHangarsTable", "--base=$stamped", "--lang=kotlin")),
        )
        assertTrue(Files.readString(meta).contains("database.migrations.CreateHangarsTable"))

        assertEquals(1, grind("make:migration").first)
        assertEquals(1, grind("make:migration", "Bad-Name", "--base=$base").first)
        assertEquals(1, grind("make:migration", "foo/bar", "--base=$base").first)
        assertEquals(1, grind("make:migration", "../evil", "--base=$base").first)
        assertEquals(1, grind("make:migration", "..evil", "--base=$base").first)
        assertEquals(1, grind("make:migration", "foo\\bar", "--base=$base").first)
        assertEquals(1, grind("make:migration", "", "--base=$base").first)
        assertEquals(1, grind("make:migration", "2026_13_01_000000_create_x_table", "--base=$base").first)
        assertEquals(1, grind("make:migration", "createFlightsTable", "--base=$base", "--lang=kotlin").first)
        assertEquals(0, grind("make:migration", "createHangarsTable", "--base=$base", "--lang=kotlin").first)
        assertTrue(Files.exists(migrationFile(base, "CreateHangarsTable")))
    }

    @Test
    fun makeModelWithCompanions() {
        val base = temp.resolve("kt")
        Files.createDirectories(base)
        val (code, out) = grind("make:model", "BlogPost", "--all", "--base=$base")
        assertEquals(0, code)
        assertTrue(out.contains("Model created: src/main/kotlin/app/models/BlogPost.kt"))
        assertTrue(read(base, "src/main/kotlin/app/models/BlogPost.kt").contains("companion object : CadenceCompanion<BlogPost>(BlogPost::class.java)"))
        assertTrue(Files.readString(migrationFile(base, "CreateBlogPostsTable")).contains("schema.create(\"blog_posts\")"))
        assertTrue(read(base, "src/main/kotlin/database/factories/BlogPostFactory.kt").contains("class BlogPostFactory : Factory<BlogPost>(BlogPost::class.java)"))
        assertTrue(read(base, "src/main/kotlin/database/seeders/BlogPostSeeder.kt").contains("class BlogPostSeeder : Seeder()"))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/controllers/BlogPostController.kt")))
        val blogController = read(base, "src/main/kotlin/app/http/controllers/BlogPostController.kt")
        assertTrue(blogController.contains("class BlogPostController : BaseResourceController()"))
        assertTrue(blogController.contains("fun index"))
        assertTrue(blogController.contains("fun create"))
        assertTrue(blogController.contains("validate(request, StoreBlogPostRequest())"))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/requests/StoreBlogPostRequest.kt")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/requests/UpdateBlogPostRequest.kt")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/policies/BlogPostPolicy.kt")))
        assertTrue(
            read(base, "src/main/resources/META-INF/kona/migrations")
                .contains("CreateBlogPostsTable"),
        )

        grind("make:model", "Category", "--migration", "--base=$base")
        assertTrue(Files.exists(migrationFile(base, "CreateCategoriesTable")))
        assertFalse(Files.exists(base.resolve("src/main/kotlin/database/factories/CategoryFactory.kt")))
        grind("make:model", "Box", "--factory", "--base=$base")
        assertTrue(Files.exists(base.resolve("src/main/kotlin/database/factories/BoxFactory.kt")))
        grind("make:model", "Plain", "--seeder", "--base=$base")
        assertTrue(Files.exists(base.resolve("src/main/kotlin/database/seeders/PlainSeeder.kt")))

        grind("make:model", "Ticket", "-mcr", "--base=$base")
        assertTrue(Files.exists(migrationFile(base, "CreateTicketsTable")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/controllers/TicketController.kt")))
        val ticketController = read(base, "src/main/kotlin/app/http/controllers/TicketController.kt")
        assertTrue(ticketController.contains("class TicketController : BaseResourceController()"))
        assertTrue(ticketController.contains("fun store"))

        grind("make:model", "Note", "-c", "--base=$base")
        val noteController = read(base, "src/main/kotlin/app/http/controllers/NoteController.kt")
        assertTrue(noteController.contains("fun index"))
        assertFalse(noteController.contains("BaseResourceController"))
        assertFalse(noteController.contains("fun store"))

        // --resource alone (no -c) still creates a resource controller
        grind("make:model", "Shelf", "--resource", "--base=$base")
        val shelfController = read(base, "src/main/kotlin/app/http/controllers/ShelfController.kt")
        assertTrue(shelfController.contains("class ShelfController : BaseResourceController()"))
        assertTrue(shelfController.contains("fun store"))

        grind("make:model", "Article", "--policy", "--base=$base")
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/policies/ArticlePolicy.kt")))
        assertTrue(read(base, "src/main/kotlin/app/policies/ArticlePolicy.kt").contains("import app.models.Article"))

        grind("make:model", "Widget", "--api", "--requests", "--base=$base")
        val widgetController = read(base, "src/main/kotlin/app/http/controllers/WidgetController.kt")
        assertTrue(widgetController.contains("class WidgetController : BaseResourceController()"))
        assertTrue(widgetController.contains("validate(request, StoreWidgetRequest())"))
        assertTrue(widgetController.contains("validate(request, UpdateWidgetRequest())"))
        assertTrue(widgetController.contains("override fun store(request: Request)"))
        assertTrue(widgetController.contains("override fun update(request: Request)"))
        assertFalse(widgetController.contains("fun create"))
        assertFalse(widgetController.contains("fun edit"))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/requests/StoreWidgetRequest.kt")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/requests/UpdateWidgetRequest.kt")))

        grind("make:model", "Tag", "-R", "--base=$base")
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/requests/StoreTagRequest.kt")))
        assertTrue(read(base, "src/main/kotlin/app/http/controllers/TagController.kt").contains("fun index"))

        val javaBase = temp.resolve("java")
        Files.createDirectories(javaBase.resolve("src/main/java/app"))
        Files.writeString(javaBase.resolve("src/main/java/app/Existing.java"), "class Existing {}")
        grind("make:model", "Flight", "--all", "--base=$javaBase")
        assertTrue(read(javaBase, "src/main/java/app/models/Flight.java").contains("public static final CadenceCompanion<Flight> QUERY = Cadence.of(Flight.class);"))
        assertTrue(Files.readString(migrationFile(javaBase, "CreateFlightsTable", "java")).contains("implements Migration"))
        assertTrue(read(javaBase, "src/main/java/database/factories/FlightFactory.java").contains("super(Flight.class);"))
        assertTrue(read(javaBase, "src/main/java/database/seeders/FlightSeeder.java").contains("public class FlightSeeder extends Seeder"))
        assertTrue(Files.exists(javaBase.resolve("src/main/java/app/http/controllers/FlightController.java")))
        val flightController = read(javaBase, "src/main/java/app/http/controllers/FlightController.java")
        assertTrue(flightController.contains("extends BaseResourceController"))
        assertTrue(flightController.contains("validate(request, new StoreFlightRequest())"))
        assertTrue(Files.exists(javaBase.resolve("src/main/java/app/http/requests/StoreFlightRequest.java")))
        assertTrue(Files.exists(javaBase.resolve("src/main/java/app/policies/FlightPolicy.java")))

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
        val boot = "--bootstrap=org.konaframework.grind.CadenceFixtureApp"
        val status0 = grind("migrate:status", "--base=$project", boot)
        assertEquals(0, status0.first)
        assertTrue(status0.second.contains("Pending"))
        assertTrue(status0.second.contains("CreateWidgetsTable"))

        val migrated = grind("migrate", "--base=$project", boot)
        assertEquals(0, migrated.first)
        assertTrue(migrated.second.contains("Running migrations."))
        assertTrue(migrated.second.contains("CreateWidgetsTable"))
        assertTrue(migrated.second.contains("AddColorToWidgetsTable"))
        assertTrue(migrated.second.contains("DONE"))
        assertTrue(grind("migrate", "--base=$project", boot).second.contains("Nothing to migrate."))
        val statusRan = grind("migrate:status", "--base=$project", boot).second
        assertTrue(statusRan.contains("Ran"))
        assertTrue(statusRan.contains("Batch"))
        assertTrue(statusRan.contains("CreateWidgetsTable"))

        val seeded = grind("db:seed", "--base=$project", boot, "--class=org.konaframework.grind.FixtureDatabaseSeeder")
        assertEquals(0, seeded.first)
        assertTrue(seeded.second.contains("Seeding: FixtureDatabaseSeeder"))
        assertTrue(seeded.second.contains("Seeding: WidgetSeeder"))
        assertTrue(seeded.second.contains("Database seeding completed successfully."))
        assertEquals(1L, Database.global().table("widgets").count())

        assertEquals(1, grind("migrate:rollback", "--base=$project", boot, "--step=0").first)
        assertEquals(1, grind("migrate:rollback", "--base=$project", boot, "--step=x").first)
        val rolled = grind("migrate:rollback", "--base=$project", boot, "--step=1")
        assertTrue(rolled.second.contains("Rolling back migrations."))
        assertTrue(rolled.second.contains("AddColorToWidgetsTable"))
        assertTrue(rolled.second.contains("CreateWidgetsTable"))
        assertTrue(rolled.second.contains("DONE"))
        assertTrue(grind("migrate:rollback", "--base=$project", boot).second.contains("Nothing to rollback."))
        grind("migrate", "--base=$project", boot)
        val resetOut = grind("migrate:reset", "--base=$project", boot).second
        assertTrue(resetOut.contains("CreateWidgetsTable"))
        assertTrue(resetOut.contains("DONE"))
        assertTrue(grind("migrate:reset", "--base=$project", boot).second.contains("Nothing to rollback."))

        assertTrue(grind("migrate", "--base=$project", boot, "--database=other").second.contains("CreateWidgetsTable"))
        assertEquals(1, grind("migrate", "--base=$project", boot, "--database=missing").first)
    }

    @Test
    fun migrateBuildsDatabaseFromConfigWhenUnbound() {
        val project = databaseProject(temp.resolve("config"), "grind_config")
        val boot = "--bootstrap=org.konaframework.grind.ConfigOnlyFixtureApp"
        val migrated = grind("migrate", "--base=$project", boot, "--verbose")
        assertTrue(migrated.second.contains("CreateWidgetsTable"))
        assertTrue(migrated.second.contains("DONE"))
        val quiet = grind("migrate", "--base=$project", boot, "--quiet")
        assertEquals(0, quiet.first)
        assertFalse(quiet.second.contains("DONE"))
        val seeded = grind("db:seed", "--base=$project", boot, "--class=org.konaframework.grind.WidgetSeeder", "-v")
        assertEquals(0, seeded.first)
        Database().addConnection("check", ConnectionConfig("jdbc:h2:mem:grind_config;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")).use {
            assertEquals(1L, it.connection("check").table("widgets").count())
        }
    }

    @Test
    fun migrateSeedRunsSeederInSameProcess() {
        val project = databaseProject(temp.resolve("seed_flag"), "grind_seed_flag")
        val boot = "--bootstrap=org.konaframework.grind.CadenceFixtureApp"
        val (code, out) = grind(
            "migrate",
            "--seed",
            "--base=$project",
            boot,
            "--class=org.konaframework.grind.FixtureDatabaseSeeder",
        )
        assertEquals(0, code)
        assertTrue(out.contains("CreateWidgetsTable"))
        assertTrue(out.contains("DONE"))
        assertTrue(out.contains("Seeding database."))
        assertTrue(out.contains("Seeding: FixtureDatabaseSeeder"))
        assertTrue(out.contains("Seeding: WidgetSeeder"))
        assertTrue(out.contains("Database seeding completed successfully."))
        assertEquals(1L, Database.global().table("widgets").count())

        // --seeder alias (same process) after reset
        grind("migrate:reset", "--base=$project", boot)
        val aliased = grind(
            "migrate",
            "--seeder",
            "--base=$project",
            boot,
            "--class=org.konaframework.grind.WidgetSeeder",
        )
        assertEquals(0, aliased.first)
        assertTrue(aliased.second.contains("Seeding: WidgetSeeder"))
        assertEquals(1L, Database.global().table("widgets").count())

        // Rollback / status must not seed
        grind("migrate:reset", "--base=$project", boot)
        grind("migrate", "--base=$project", boot)
        val rolled = grind("migrate:rollback", "--seed", "--base=$project", boot)
        assertFalse(rolled.second.contains("Seeding:"))
        assertTrue(MigrateCommand.wantsSeed(mapOf("seed" to "true")))
        assertTrue(MigrateCommand.wantsSeed(mapOf("seeder" to "1")))
        assertTrue(MigrateCommand.wantsSeed(mapOf("seeder" to "")))
        assertTrue(MigrateCommand.wantsSeed(mapOf("seeder" to "yes")))
        assertFalse(MigrateCommand.wantsSeed(mapOf("seeder" to "false")))
        assertFalse(MigrateCommand.wantsSeed(emptyMap()))
    }

    @Test
    fun migrateSeedWithFileH2SurvivesSeparateProcessOpen() {
        val dbDir = temp.resolve("file_h2_db")
        Files.createDirectories(dbDir)
        val url = "jdbc:h2:file:${dbDir.resolve("kona").toAbsolutePath()};DATABASE_TO_LOWER=TRUE"
        val project = fixtureProject(temp.resolve("file_seed"), "GRIND_DB_URL=$url\n")
        val boot = "--bootstrap=org.konaframework.grind.FileH2FixtureApp"
        val (code, out) = grind(
            "migrate",
            "--seed",
            "--base=$project",
            boot,
            "--class=org.konaframework.grind.WidgetSeeder",
        )
        assertEquals(0, code)
        assertTrue(out.contains("CreateWidgetsTable"))
        assertTrue(out.contains("Seeding: WidgetSeeder"))
        Database.clearGlobal()
        Database().addConnection("check", ConnectionConfig(url)).use {
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
        assertEquals(1, grind("migrate", "--base=$project", "--bootstrap=org.konaframework.grind.InstanceCreateApp").first)
        assertEquals(1, grind("migrate", "--base=$project", "--bootstrap=org.konaframework.grind.FixtureApp").first)

        val boot = "--bootstrap=org.konaframework.grind.CadenceFixtureApp"
        assertEquals(1, grind("db:seed", "--base=$project", boot).first)
        assertEquals(1, grind("db:seed", "--base=$project", boot, "--class=org.konaframework.grind.NotASeeder").first)
        assertEquals(SeedCommand.DEFAULT_SEEDER, "database.seeders.DatabaseSeeder")
        assertThrows(GrindException::class.java) { MigrateCommand(MigrateCommand.Action.MIGRATE).run(listOf("--base=$empty")) }
    }

    @Test
    fun freshRefreshWipeShowTableAndDump() {
        val project = databaseProject(temp.resolve("fresh"), "grind_fresh")
        val boot = "--bootstrap=org.konaframework.grind.CadenceFixtureApp"
        assertEquals(0, grind("migrate", "--base=$project", boot).first)
        Database.global().table("widgets").insert(mapOf("name" to "keep"))
        assertEquals(1L, Database.global().table("widgets").count())

        val fresh = grind("migrate:fresh", "--base=$project", boot)
        assertEquals(0, fresh.first)
        assertTrue(fresh.second.contains("Dropping all tables."))
        assertTrue(fresh.second.contains("Dropped all tables successfully."))
        assertTrue(fresh.second.contains("Running migrations."))
        assertTrue(fresh.second.contains("CreateWidgetsTable"))
        assertTrue(fresh.second.contains("DONE"))
        assertEquals(0L, Database.global().table("widgets").count())

        val seeded = grind(
            "migrate:fresh",
            "--seed",
            "--drop-views",
            "--drop-types",
            "--base=$project",
            boot,
            "--class=org.konaframework.grind.WidgetSeeder",
        )
        assertEquals(0, seeded.first)
        assertTrue(seeded.second.contains("Seeding: WidgetSeeder"))
        assertEquals(1L, Database.global().table("widgets").count())

        grind("migrate", "--base=$project", boot)
        assertEquals(
            0,
            grind("db:seed", "--seeder=org.konaframework.grind.WidgetSeeder", "--base=$project", boot).first,
        )
        val namedSeeder = grind(
            "migrate:fresh",
            "--seeder=org.konaframework.grind.WidgetSeeder",
            "--base=$project",
            boot,
        )
        assertEquals(0, namedSeeder.first)
        assertTrue(namedSeeder.second.contains("Seeding: WidgetSeeder"))

        val refresh = grind("migrate:refresh", "--base=$project", boot)
        assertEquals(0, refresh.first)
        assertTrue(refresh.second.contains("Refreshing the database."))
        assertTrue(refresh.second.contains("CreateWidgetsTable"))
        val refreshStep = grind("migrate:refresh", "--step=1", "--seed", "--base=$project", boot, "--class=org.konaframework.grind.WidgetSeeder")
        assertEquals(0, refreshStep.first)
        assertTrue(refreshStep.second.contains("Seeding: WidgetSeeder"))

        val install = grind("migrate:install", "--base=$project", boot)
        assertEquals(0, install.first)
        assertTrue(install.second.contains("Migration table created successfully."))

        assertEquals(0, grind("db:show", "--database=other", "--verbose", "--base=$project", boot).first)
        val show = grind("db:show", "--base=$project", boot)
        assertEquals(0, show.first)
        assertTrue(show.second.contains("Connection"))
        assertTrue(show.second.contains("H2") || show.second.contains("Driver"))
        assertTrue(show.second.contains("widgets"))

        val table = grind("db:table", "widgets", "--base=$project", boot)
        assertEquals(0, table.first)
        assertTrue(table.second.contains("name") || table.second.contains("NAME"))
        assertEquals(1, grind("db:table", "--base=$project", boot).first)
        assertEquals(1, grind("db:table", "   ", "--base=$project", boot).first)
        assertEquals(1, grind("db:table", "missing", "--base=$project", boot).first)
        // Bypass SignatureParser so table() still sees an empty positional list.
        assertThrows(GrindException::class.java) {
            DatabaseInspectCommand(DatabaseInspectCommand.Action.TABLE).run(listOf("--base=$project", boot))
        }

        val dump = grind("schema:dump", "--base=$project", boot)
        assertEquals(0, dump.first)
        assertTrue(dump.second.contains("Schema dumped to database/schema.sql"))
        val sql = Files.readString(project.resolve("database/schema.sql"))
        assertTrue(sql.contains("CREATE TABLE"))
        val custom = grind("schema:dump", "--path=tmp/out.sql", "--base=$project", boot)
        assertEquals(0, custom.first)
        assertTrue(Files.readString(project.resolve("tmp/out.sql")).contains("CREATE TABLE"))

        val wiped = grind("db:wipe", "--drop-views", "--base=$project", boot)
        assertEquals(0, wiped.first)
        assertTrue(wiped.second.contains("Dropped all tables successfully."))
        val widgetsGone = Database.global().connection().withConnection { jdbc ->
            Schema(jdbc).hasTable("widgets")
        }
        assertFalse(widgetsGone)
        val emptyShow = grind("db:show", "--base=$project", boot)
        assertEquals(0, emptyShow.first)
        val emptyDump = grind("schema:dump", "--path=database/empty.sql", "--base=$project", boot)
        assertEquals(0, emptyDump.first)
        assertEquals("", Files.readString(project.resolve("database/empty.sql")))

        assertEquals(0, grind("migrate:fresh", "--database=other", "--base=$project", boot).first)
        assertEquals(1, grind("migrate:fresh", "--database=missing", "--base=$project", boot).first)

        val prod = "--bootstrap=org.konaframework.grind.ProductionCadenceApp"
        assertEquals(1, grind("migrate:fresh", "--base=$project", prod).first)
        assertEquals(0, grind("migrate:fresh", "--force", "--base=$project", prod).first)
        assertEquals(0, grind("migrate:status", "--base=$project", prod).first)

        assertTrue(MigrateCommand.wantsSeed(mapOf("seeder" to "org.foo.Bar")))
        assertEquals("org.foo.Bar", MigrateCommand.seederClass(mapOf("seeder" to "org.foo.Bar")))
        assertEquals(SeedCommand.DEFAULT_SEEDER, MigrateCommand.seederClass(mapOf("seed" to "true")))
        assertFalse(MigrateCommand.wantsFlag(emptyMap(), "drop-views"))
        assertTrue(MigrateCommand.wantsFlag(mapOf("drop-views" to "true"), "drop-views"))
        assertFalse(MigrateCommand.flagEnabled(null))
        assertFalse(MigrateCommand.flagEnabled("no"))
        assertFalse(MigrateCommand.flagEnabled("0"))
        assertFalse(MigrateCommand.wantsSeed(mapOf("seeder" to "0")))
        assertFalse(MigrateCommand.wantsSeed(mapOf("seeder" to "no")))
        assertEquals(SeedCommand.DEFAULT_SEEDER, MigrateCommand.seederClass(mapOf("seeder" to "true")))
        assertEquals(SeedCommand.DEFAULT_SEEDER, MigrateCommand.seederClass(mapOf("seeder" to "yes")))
        assertEquals(SeedCommand.DEFAULT_SEEDER, MigrateCommand.seederClass(mapOf("seeder" to "1")))
        assertEquals(SeedCommand.DEFAULT_SEEDER, MigrateCommand.seederClass(mapOf("seeder" to "")))
        assertEquals(MigrateCommand.Action.FRESH, MigrateCommand.Action.valueOf("FRESH"))
        assertEquals(MigrateCommand.Action.REFRESH, MigrateCommand.Action.valueOf("REFRESH"))
        assertEquals(MigrateCommand.Action.INSTALL, MigrateCommand.Action.valueOf("INSTALL"))
        assertEquals(MigrateCommand.Action.WIPE, MigrateCommand.Action.valueOf("WIPE"))
        assertTrue(ListCommand.COMMANDS.any { it.first == "migrate:fresh" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "db:wipe" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "db:show" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "schema:dump" })
        assertEquals(DatabaseInspectCommand.DEFAULT_DUMP_PATH, "database/schema.sql")
        assertEquals(DatabaseInspectCommand.Action.SHOW, DatabaseInspectCommand.Action.valueOf("SHOW"))
    }

    @Test
    fun statusWithNoMigrations() {
        val project = databaseProject(temp.resolve("none"), "grind_none")
        val (code, out) = grind("migrate:status", "--base=$project", "--bootstrap=org.konaframework.grind.EmptyRegistryApp")
        assertEquals(0, code)
        assertTrue(out.contains("No migrations registered."))
        val installed = grind("migrate:install", "--base=$project", "--bootstrap=org.konaframework.grind.EmptyRegistryApp")
        assertEquals(0, installed.first)
        assertTrue(installed.second.contains("Migration table created successfully."))
        assertEquals(0, grind("db:wipe", "--base=$project", "--bootstrap=org.konaframework.grind.EmptyRegistryApp").first)
        assertEquals(0, grind("schema:dump", "--path=", "--base=$project", "--bootstrap=org.konaframework.grind.EmptyRegistryApp").first)
        val emptyFresh = grind("migrate:fresh", "--base=$project", "--bootstrap=org.konaframework.grind.EmptyRegistryApp")
        assertEquals(0, emptyFresh.first)
        assertTrue(emptyFresh.second.contains("Nothing to migrate."))
    }

    @Test
    fun resolveRegistryDiscoversMetaInfWhenUnbound() {
        val root = temp.resolve("discover")
        val meta = root.resolve("META-INF/kona")
        Files.createDirectories(meta)
        Files.writeString(meta.resolve("migrations"), CreateWidgetsTable::class.java.name + "\n")
        val loader = java.net.URLClassLoader(arrayOf(root.toUri().toURL()), javaClass.classLoader)
        val app = org.konaframework.core.Application.builder(root).build()
        val registry = MigrateCommand.resolveRegistry(app, loader)
        assertEquals(listOf("CreateWidgetsTable"), registry.names)

        val emptyLoader = java.net.URLClassLoader(arrayOf(temp.resolve("empty-meta").also { Files.createDirectories(it) }.toUri().toURL()), javaClass.classLoader)
        assertThrows(GrindException::class.java) {
            MigrateCommand.resolveRegistry(org.konaframework.core.Application.builder(temp.resolve("empty-app")).build(), emptyLoader)
        }
    }
}
