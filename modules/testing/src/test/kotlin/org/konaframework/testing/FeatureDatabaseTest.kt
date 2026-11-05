package org.konaframework.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceCompanion
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.SoftDeletes
import org.konaframework.cadence.migrations.Migration
import org.konaframework.cadence.migrations.MigrationRegistry
import org.konaframework.http.HttpServiceProvider
import java.nio.file.Path

class FeatureDatabaseTest {
    @TempDir
    lateinit var base: Path

    @Test
    fun databaseAssertionsCoverHasMissingCountAndSoftDeletes() {
        val database = Database().addConnection(
            "default",
            ConnectionConfig(
                "jdbc:h2:mem:feat_db_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                poolSize = 2,
            ),
        )
        database.connection().statement(
            "CREATE TABLE posts (id INT PRIMARY KEY, title VARCHAR(100), deleted_at TIMESTAMP)",
        )
        database.connection().statement("INSERT INTO posts VALUES (1, 'Hello', NULL)")
        database.connection().statement("INSERT INTO posts VALUES (2, 'Gone', CURRENT_TIMESTAMP)")

        val asserts = DatabaseAssertions(database)
        asserts.assertDatabaseHas("posts", mapOf("id" to 1, "title" to "Hello"))
            .assertDatabaseMissing("posts", mapOf("title" to "Nope"))
            .assertDatabaseCount("posts", 2)
            .assertSoftDeleted("posts", mapOf("id" to 2))
            .assertNotSoftDeleted("posts", mapOf("id" to 1))
            .assertSoftDeleted("posts", mapOf("id" to 2), "deleted_at")

        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertDatabaseHas("posts", mapOf("title" to "Nope"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertDatabaseMissing("posts", mapOf("id" to 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertDatabaseCount("posts", 9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertSoftDeleted("posts", mapOf("id" to 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertNotSoftDeleted("posts", mapOf("id" to 2))
        }

        database.connection().statement("CREATE TABLE empty_table (id INT)")
        asserts.assertDatabaseEmpty("empty_table")
        assertThrows(IllegalArgumentException::class.java) { asserts.assertDatabaseEmpty("posts") }

        val post = SoftPost().also {
            it.setAttribute("id", 1)
            it.setAttribute("title", "Hello")
        }
        // Model helpers need a global DB for Cadence queries; seed attributes only for key lookup.
        DatabaseAssertions(database).assertDatabaseHas("posts", mapOf("id" to post.getAttribute("id")))

        val soft = SoftPost().also {
            it.setAttribute("id", 2)
            it.setAttribute("title", "Gone")
        }
        asserts.assertSoftDeleted(soft)
        asserts.assertNotSoftDeleted(SoftPost().also { it.setAttribute("id", 1) })
        asserts.assertModelExists(SoftPost().also { it.setAttribute("id", 1) })
        asserts.assertModelMissing(SoftPost().also { it.setAttribute("id", 99) })
        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertModelExists(SoftPost().also { it.setAttribute("id", 99) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertModelMissing(SoftPost().also { it.setAttribute("id", 1) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertSoftDeleted(SoftPost().also { it.setAttribute("id", 1) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            asserts.assertNotSoftDeleted(SoftPost().also { it.setAttribute("id", 2) })
        }

        // Null column match
        database.connection().statement("CREATE TABLE flags (id INT, note VARCHAR(10))")
        database.connection().statement("INSERT INTO flags VALUES (1, NULL)")
        asserts.assertDatabaseHas("flags", mapOf("id" to 1, "note" to null))

        // Named connection
        database.addConnection(
            "other",
            ConnectionConfig(
                "jdbc:h2:mem:feat_other_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                poolSize = 1,
            ),
        )
        database.connection("other").statement("CREATE TABLE items (id INT)")
        DatabaseAssertions(database, "other").assertDatabaseEmpty("items")
    }

    @Test
    fun testDatabaseMigratesOnceAndRollsBack(@TempDir dir: Path) {
        val app = TestApplication.create(dir, HttpServiceProvider())
        val database = Database().addConnection(
            "default",
            ConnectionConfig(
                "jdbc:h2:mem:feat_tx_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                poolSize = 2,
            ),
        )
        app.container.instance(Database::class.java, database)
        val registry = MigrationRegistry().add(
            "2024_01_01_create_widgets",
            object : Migration {
                override fun up(schema: org.konaframework.cadence.schema.Schema) {
                    schema.create("widgets") {
                        integer("id")
                        string("name")
                    }
                }

                override fun down(schema: org.konaframework.cadence.schema.Schema) {
                    schema.drop("widgets")
                }
            },
        )
        app.container.instance(MigrationRegistry::class.java, registry)

        val first = TestDatabase(app, migrate = true, lazily = false)
        first.begin()
        assertTrue(first.isStarted())
        first.database().connection().table("widgets").insert(mapOf("id" to 1, "name" to "A"))
        first.assertions().assertDatabaseHas("widgets", mapOf("id" to 1))
        first.rollback()
        assertFalse(first.isStarted())
        assertEquals(0L, database.connection().table("widgets").count())

        val lazy = TestDatabase(app, migrate = true, lazily = true)
        lazy.begin()
        assertFalse(lazy.isStarted())
        lazy.ensureStarted()
        assertTrue(lazy.isStarted())
        lazy.ensureStarted()
        lazy.database().connection().table("widgets").insert(mapOf("id" to 2, "name" to "B"))
        lazy.rollback()

        val txOnly = TestDatabase(app, migrate = false, lazily = false)
        txOnly.begin()
        txOnly.database().connection().table("widgets").insert(mapOf("id" to 3, "name" to "C"))
        txOnly.rollback()
        assertEquals(0L, database.connection().table("widgets").count())

        // No registry → migrateOnce is a no-op
        val plain = TestApplication.create(dir.resolve("plain"), HttpServiceProvider())
        plain.container.instance(Database::class.java, database)
        val noMig = TestDatabase(plain, migrate = true, lazily = false)
        noMig.begin()
        noMig.rollback()

        // rollback when not started
        TestDatabase(app).rollback()
    }
}

class SoftPost : Cadence<SoftPost>(), SoftDeletes {
    override val table = "posts"
    override val primaryKey = "id"
    override val timestamps = false
    override val deletedAtColumn = "deleted_at"

    companion object : CadenceCompanion<SoftPost>(SoftPost::class.java)
}
