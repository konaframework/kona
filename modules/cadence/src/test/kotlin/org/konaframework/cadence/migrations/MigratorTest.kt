package org.konaframework.cadence.migrations

import org.konaframework.cadence.schema.Schema
import org.konaframework.cadence.schema.SchemaGrammar
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class CreateUsersTable : Migration {
    override fun up(schema: Schema) {
        schema.create("users") {
            id()
            string("email").unique()
            timestamps()
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("users")
    }
}

class CreatePostsTable : Migration {
    override fun up(schema: Schema) {
        schema.create("posts") {
            id()
            foreignId("user_id").constrained()
            string("title")
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("posts")
    }
}

class MigratorTest {
    private lateinit var connection: Connection

    @BeforeEach
    fun open() {
        connection = DriverManager.getConnection("jdbc:h2:mem:migrate_${System.nanoTime()};DB_CLOSE_DELAY=-1")
    }

    @AfterEach
    fun close() {
        connection.close()
    }

    @Test
    fun migratesInBatchesAndRollsBack() {
        val registry = MigrationRegistry(CreateUsersTable())
        val migrator = Migrator(connection, registry)
        assertEquals(listOf("CreateUsersTable"), migrator.pending())
        assertEquals(listOf("CreateUsersTable"), migrator.migrate())
        assertEquals(emptyList<String>(), migrator.migrate())
        assertTrue(migrator.schema.hasTable("extract_migrations"))

        registry.add(CreatePostsTable())
        registry.add("2026_add_flag", object : Migration {
            override fun up(schema: Schema) = schema.table("users") { boolean("flag").default(false) }
            override fun down(schema: Schema) = schema.table("users") { dropColumn("flag") }
        })
        assertEquals(listOf("CreatePostsTable", "2026_add_flag"), migrator.migrate())
        assertEquals(
            listOf(
                MigrationStatus("CreateUsersTable", 1),
                MigrationStatus("CreatePostsTable", 2),
                MigrationStatus("2026_add_flag", 2),
            ),
            migrator.status(),
        )
        assertTrue(migrator.status().all { it.ran })

        assertEquals(listOf("2026_add_flag", "CreatePostsTable"), migrator.rollback())
        assertFalse(migrator.schema.hasTable("posts"))
        assertTrue(migrator.schema.hasTable("users"))
        assertEquals(listOf("CreatePostsTable", "2026_add_flag"), migrator.pending())

        migrator.migrate()
        assertEquals(listOf("2026_add_flag", "CreatePostsTable", "CreateUsersTable"), migrator.rollback(5))
        assertEquals(emptyList<String>(), migrator.rollback())
        migrator.migrate()
        assertEquals(3, migrator.ran().size)
        assertEquals(listOf("2026_add_flag", "CreatePostsTable", "CreateUsersTable"), migrator.reset())
        assertFalse(migrator.schema.hasTable("users"))
        assertThrows(IllegalArgumentException::class.java) { migrator.rollback(0) }
    }

    @Test
    fun migrateNotifiesEvents() {
        val migrator = Migrator(connection, MigrationRegistry(CreateUsersTable()))
        val starts = mutableListOf<String>()
        val finishes = mutableListOf<String>()
        val events = object : MigrationEvents {
            override fun starting(name: String) {
                starts += name
            }

            override fun finished(name: String, millis: Long) {
                finishes += name
                assertTrue(millis >= 0)
            }
        }
        assertEquals(listOf("CreateUsersTable"), migrator.migrate(events))
        assertEquals(listOf("CreateUsersTable"), starts)
        assertEquals(listOf("CreateUsersTable"), finishes)
        assertEquals(emptyList<String>(), migrator.migrate(object : MigrationEvents {}))
        starts.clear()
        finishes.clear()
        assertEquals(listOf("CreateUsersTable"), migrator.rollback(1, events))
        assertEquals(listOf("CreateUsersTable"), starts)
        migrator.migrate(object : MigrationEvents {})
        assertEquals(listOf("CreateUsersTable"), migrator.reset(object : MigrationEvents {}))
    }

    @Test
    fun failedMigrationRollsBackAndReportsName() {
        val registry = MigrationRegistry().add("broken", object : Migration {
            override fun up(schema: Schema) = schema.execute(listOf("CREATE TABLE \"oops\" (bad syntax"))
            override fun down(schema: Schema) = Unit
        })
        val migrator = Migrator(connection, registry, SchemaGrammar.H2, "runs")
        val ex = assertThrows(MigrationException::class.java) { migrator.migrate() }
        assertTrue(ex.message!!.startsWith("Migration [broken] failed"))
        assertEquals(listOf("broken"), migrator.pending())
        assertEquals("runs", migrator.table)
        assertTrue(connection.autoCommit)
    }

    @Test
    fun rollbackOfUnknownMigrationFails() {
        Migrator(connection, MigrationRegistry(CreateUsersTable())).migrate()
        val other = Migrator(connection, MigrationRegistry())
        val ex = assertThrows(MigrationException::class.java) { other.rollback() }
        assertEquals("Migration not found: CreateUsersTable", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun registryRules() {
        val registry = MigrationRegistry(CreateUsersTable())
        assertThrows(IllegalArgumentException::class.java) { registry.add(CreateUsersTable()) }
        assertThrows(IllegalArgumentException::class.java) { registry.add(" ", CreatePostsTable()) }
        assertNull(registry["nope"])
        assertFalse(MigrationStatus("x", null).ran)
    }

    @Test
    fun registrySortsTimestampedNames() {
        val later = object : Migration {
            override fun up(schema: Schema) = Unit
            override fun down(schema: Schema) = Unit
        }
        val earlier = object : Migration {
            override fun up(schema: Schema) = Unit
            override fun down(schema: Schema) = Unit
        }
        val registry = MigrationRegistry()
            .add("M2026_10_20_120000_Later", later)
            .add("CreateUsersTable", CreateUsersTable())
            .add("M2026_10_20_110000_Earlier", earlier)
        assertEquals(
            listOf(
                "CreateUsersTable",
                "M2026_10_20_110000_Earlier",
                "M2026_10_20_120000_Later",
            ),
            registry.names,
        )
        val migrator = Migrator(connection, registry)
        assertEquals(
            listOf("CreateUsersTable", "M2026_10_20_110000_Earlier", "M2026_10_20_120000_Later"),
            migrator.pending(),
        )
        assertEquals(
            listOf("CreateUsersTable", "M2026_10_20_110000_Earlier", "M2026_10_20_120000_Later"),
            migrator.migrate(),
        )
    }

    @Test
    fun registrySortsAnnotatedFileStems() {
        val registry = MigrationRegistry(NamedLater(), NamedUsers())
        assertEquals(
            listOf(
                "2000_01_01_000000_create_users_table",
                "2026_10_20_120000_create_posts_table",
            ),
            registry.names,
        )
    }
}

@MigrationName("2026_10_20_120000_create_posts_table")
class NamedLater : Migration {
    override fun up(schema: Schema) = Unit
    override fun down(schema: Schema) = Unit
}

@MigrationName("2000_01_01_000000_create_users_table")
class NamedUsers : Migration {
    override fun up(schema: Schema) = Unit
    override fun down(schema: Schema) = Unit
}
