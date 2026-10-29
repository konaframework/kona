package org.konaframework.cadence.schema

import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class SchemaWipeDumpTest {
    private lateinit var connection: Connection
    private lateinit var schema: Schema

    @BeforeEach
    fun open() {
        connection = DriverManager.getConnection("jdbc:h2:mem:wipe_${System.nanoTime()};DB_CLOSE_DELAY=-1")
        schema = Schema(connection)
    }

    @AfterEach
    fun close() {
        connection.close()
        SchemaGrammar.clearRegisteredProducts()
    }

    @Test
    fun dropsTablesViewsAndDumpsSql() {
        schema.create("authors") {
            id()
            string("email").unique()
            string("name").nullable()
            integer("age").default(1)
            index("name")
        }
        schema.create("books") {
            id()
            foreignId("author_id").constrained("authors")
            string("title")
        }
        schema.execute(listOf("""CREATE VIEW "book_titles" AS SELECT "title" FROM "books""""))

        assertTrue(schema.tables().any { it.equals("authors", ignoreCase = true) })
        assertTrue(schema.views().any { it.equals("book_titles", ignoreCase = true) })
        val cols = schema.columns("authors")
        assertTrue(cols.any { it.name.equals("email", ignoreCase = true) && !it.nullable })
        assertTrue(cols.any { it.name.equals("name", ignoreCase = true) && it.nullable })
        assertTrue(schema.primaryKey("authors").any { it.equals("id", ignoreCase = true) })
        val fks = schema.foreignKeys("books")
        assertTrue(fks.any { it.column.equals("author_id", ignoreCase = true) })
        assertTrue(fks.any { it.referencedTable.equals("authors", ignoreCase = true) })
        assertTrue(schema.foreignKeys("authors").isEmpty())
        assertTrue(schema.foreignKeys("missing").isEmpty())
        assertNull(schemaForeignKeyFromJdbc(null, "authors", "id"))
        assertNull(schemaForeignKeyFromJdbc("author_id", null, "id"))
        assertEquals(
            SchemaForeignKey("author_id", "authors", "id"),
            schemaForeignKeyFromJdbc("author_id", "authors", null),
        )
        assertEquals(
            SchemaForeignKey("author_id", "authors", "pk"),
            schemaForeignKeyFromJdbc("author_id", "authors", "pk"),
        )
        assertEquals(
            listOf(SchemaForeignKey("author_id", "authors", "id")),
            importedKeysFromRows(
                listOf(
                    Triple(null, "authors", "id"),
                    Triple("author_id", null, "id"),
                    Triple("author_id", "authors", null),
                ),
            ),
        )
        assertTrue(schema.columns("missing").isEmpty())
        assertTrue(schema.primaryKey("missing").isEmpty())

        val dumped = schema.dump()
        assertTrue(dumped.contains("CREATE TABLE"))
        assertTrue(dumped.contains("PRIMARY KEY"))
        assertTrue(dumped.contains("email") || dumped.contains("EMAIL"))

        val droppedViews = schema.dropAllViews()
        assertTrue(droppedViews.any { it.equals("book_titles", ignoreCase = true) })
        assertTrue(schema.views().isEmpty())
        assertEquals(emptyList<String>(), schema.dropAllViews())

        val dropped = schema.dropAllTables()
        assertTrue(dropped.any { it.equals("authors", ignoreCase = true) })
        assertTrue(dropped.any { it.equals("books", ignoreCase = true) })
        assertFalse(schema.hasTable("authors"))
        assertFalse(schema.hasTable("books"))
        assertEquals(emptyList<String>(), schema.dropAllTables())
        assertEquals("", schema.dump())
    }

    @Test
    fun standardGrammarDropsWithoutFkToggle() {
        val plain = Schema(connection, SchemaGrammar.STANDARD)
        plain.create("flags") { string("name") }
        assertTrue(plain.dump().contains("CREATE TABLE"))
        val dropped = plain.dropAllTables()
        assertTrue(dropped.any { it.equals("flags", ignoreCase = true) })
        assertFalse(plain.hasTable("flags"))
    }

    @Test
    fun compiledBulkDropAndTypesListing() {
        schema.create("solo") { id() }
        val bulk = Schema(
            connection,
            object : SchemaGrammar() {
                override fun compileDisableForeignKeyConstraints(): String = "SET REFERENTIAL_INTEGRITY FALSE"
                override fun compileEnableForeignKeyConstraints(): String = "SET REFERENTIAL_INTEGRITY TRUE"
                override fun compileDropAllTables(tables: List<String>): List<String> =
                    listOf("DROP TABLE IF EXISTS " + tables.joinToString(", ") { wrap(it) })
            },
        )
        assertTrue(bulk.dropAllTables().isNotEmpty())
        assertFalse(schema.hasTable("solo"))

        val emptyTypes = Schema(
            connection,
            object : SchemaGrammar() {
                override fun listUserTypesSql(): String = "SELECT 'mood' WHERE 1=0"
            },
        )
        assertEquals(emptyList<String>(), emptyTypes.dropAllTypes())

        val listed = Schema(
            connection,
            object : SchemaGrammar() {
                override fun listUserTypesSql(): String = "SELECT CAST(NULL AS VARCHAR) UNION ALL SELECT 'mood'"
                override fun compileDropTypeIfExists(type: String): String = "SELECT 1"
            },
        )
        assertEquals(listOf("mood"), listed.dropAllTypes())
        assertTrue(SchemaGrammar.POSTGRES.supportsDropTypes())
        assertFalse(SchemaGrammar.H2.supportsDropTypes())
        assertEquals(emptyList<String>(), Schema(connection).dropAllTypes())
    }

    @Test
    fun skipFiltersAndColumnLabels() {
        schema.create("keep") { id() }
        schema.create("other") { id() }
        val skipping = Schema(
            connection,
            object : SchemaGrammar() {
                override fun skipTable(name: String): Boolean = name.equals("keep", ignoreCase = true)
                override fun skipSchema(name: String?): Boolean = name.equals("PUBLIC", ignoreCase = true)
            },
        )
        assertTrue(skipping.tables().isEmpty())

        val email = SchemaColumn("email", "VARCHAR", false, "'x'", 255)
        assertEquals("VARCHAR(255)", email.typeLabel())
        assertEquals("CHAR(1)", SchemaColumn("c", "CHAR", true, "", 1).typeLabel())
        assertEquals("NVARCHAR(10)", SchemaColumn("n", "NVARCHAR", true, "", 10).typeLabel())
        assertEquals("VARCHAR2(20)", SchemaColumn("v", "VARCHAR2", true, "", 20).typeLabel())
        assertEquals("BIGINT", SchemaColumn("id", "BIGINT", false, "", 19).typeLabel())
        assertEquals("VARCHAR", SchemaColumn("z", "VARCHAR", false, "", 0).typeLabel())
        assertFalse(email.nullable)
        assertEquals("'x'", email.defaultValue)
        assertEquals("", SchemaColumn("n", "INT", true, "", 0).defaultValue)

        val grammar = SchemaGrammar.STANDARD
        assertFalse(grammar.skipSchema(null))
        assertFalse(grammar.skipSchema(""))
        assertTrue(grammar.skipSchema("INFORMATION_SCHEMA"))
        assertTrue(grammar.skipSchema("sys"))
        assertTrue(grammar.skipSchema("pg_catalog"))
        assertTrue(grammar.skipSchema("pg_toast_123"))
        assertFalse(grammar.skipSchema("public"))
        assertFalse(grammar.skipTable("users"))
        assertTrue(SchemaGrammar.SQLITE.skipTable("sqlite_sequence"))
        assertEquals("SET REFERENTIAL_INTEGRITY FALSE", SchemaGrammar.H2.compileDisableForeignKeyConstraints())
        assertEquals("SET REFERENTIAL_INTEGRITY TRUE", SchemaGrammar.H2.compileEnableForeignKeyConstraints())
        assertTrue(SchemaGrammar.H2.compileDropAllTables(listOf("a", "b"))!!.first().contains("CASCADE"))
        assertEquals("PRAGMA foreign_keys = OFF", SchemaGrammar.SQLITE.compileDisableForeignKeyConstraints())
        assertEquals("PRAGMA foreign_keys = ON", SchemaGrammar.SQLITE.compileEnableForeignKeyConstraints())
        assertEquals(listOf("DELETE FROM sqlite_sequence"), SchemaGrammar.SQLITE.compileResetSequences())
        assertEquals(emptyList<String>(), SchemaGrammar.STANDARD.compileResetSequences())
        assertEquals("SET FOREIGN_KEY_CHECKS=0", SchemaGrammar.MYSQL.compileDisableForeignKeyConstraints())
        assertEquals("SET FOREIGN_KEY_CHECKS=1", SchemaGrammar.MYSQL.compileEnableForeignKeyConstraints())
        assertEquals("SET FOREIGN_KEY_CHECKS=0", SchemaGrammar.MARIADB.compileDisableForeignKeyConstraints())
        assertTrue(SchemaGrammar.POSTGRES.compileDropAllTables(listOf("a", "b"))!!.first().contains("CASCADE"))
        assertTrue(SchemaGrammar.MYSQL.compileDropAllTables(listOf("a"))!!.first().contains("DROP TABLE"))
        assertTrue(SchemaGrammar.ORACLE.compileDropAllTables(listOf("a"))!!.first().contains("CASCADE CONSTRAINTS"))
        assertTrue(SchemaGrammar.SQLSERVER.compileDisableForeignKeyConstraints()!!.contains("NOCHECK"))
        assertTrue(SchemaGrammar.SQLSERVER.compileEnableForeignKeyConstraints()!!.contains("CHECK CONSTRAINT"))
        assertTrue(SchemaGrammar.POSTGRES.listUserTypesSql()!!.contains("pg_type"))
        assertEquals("DROP VIEW IF EXISTS \"v\"", grammar.compileDropViewIfExists("v"))
        assertEquals("DROP TYPE IF EXISTS \"t\"", grammar.compileDropTypeIfExists("t"))
        assertEquals(null, grammar.compileDisableForeignKeyConstraints())
        assertEquals(null, grammar.compileEnableForeignKeyConstraints())
        assertEquals(null, grammar.compileDropAllTables(listOf("a")))
        assertEquals(null, grammar.listUserTypesSql())
    }

    @Test
    fun sqliteWipeSkipsSequenceAndResetsIt() {
        val url = "jdbc:sqlite:file:wipe_${System.nanoTime()}?mode=memory&cache=shared"
        Database().addConnection("default", ConnectionConfig(url)).use { db ->
            db.connection().withConnection { jdbc ->
                val sqlite = Schema(jdbc)
                sqlite.create("items") {
                    id()
                    string("name")
                }
                jdbc.createStatement().use { it.execute("""INSERT INTO "items" ("name") VALUES ('a')""") }
                assertTrue(sqlite.hasTable("sqlite_sequence") || sqlite.tables().none { it.startsWith("sqlite_") })
                val dropped = sqlite.dropAllTables()
                assertTrue(dropped.any { it.equals("items", ignoreCase = true) })
                assertFalse(dropped.any { it.startsWith("sqlite_") })
                assertFalse(sqlite.hasTable("items"))
            }
        }
        Database().addConnection(
            "default",
            ConnectionConfig("jdbc:sqlite:file:wipenoseq_${System.nanoTime()}?mode=memory&cache=shared"),
        ).use { db ->
            db.connection().withConnection { jdbc ->
                val sqlite = Schema(jdbc)
                sqlite.create("flags") { string("name") }
                assertTrue(sqlite.dropAllTables().any { it.equals("flags", ignoreCase = true) })
            }
        }
    }
}
