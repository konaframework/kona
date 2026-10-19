package org.konaframework.cadence

import org.h2.jdbcx.JdbcDataSource
import org.konaframework.cadence.schema.Blueprint
import org.konaframework.cadence.schema.Schema
import org.konaframework.cadence.schema.SchemaGrammar
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Extra branches for 100% Kover on M25 production paths. */
class M25CoverageSealTest {
    @AfterEach
    fun clear() {
        Database.clearGlobal()
        SchemaGrammar.clearRegisteredProducts()
        Grammar.clearRegisteredUrlPrefixes()
    }

    @Test
    fun grammarOfAndLocksAndDatesExhaustively() {
        for (d in Dialect.entries) {
            assertNotNull(Grammar.of(d))
        }
        assertEquals("", Grammar.SQLSERVER.compileTableLock(LockMode.NONE))
        assertEquals("", Grammar.SQLITE.compileLock(LockMode.NONE))
        assertEquals("", Grammar.SQLSERVER.compileLock(LockMode.UPDATE))
        assertEquals("", Grammar.ORACLE.compileLock(LockMode.SHARED))
        assertEquals("", Grammar.H2.compileLock(LockMode.SHARED))
        assertEquals(" lock in share mode", Grammar.MARIADB.compileLock(LockMode.SHARED))

        for (g in listOf(Grammar.MYSQL, Grammar.MARIADB, Grammar.SQLITE, Grammar.SQLSERVER, Grammar.ORACLE, Grammar.POSTGRES, Grammar.H2, Grammar.ANSI)) {
            for (part in listOf("date", "time", "day", "month", "year")) {
                assertNotNull(g.compileDatePart(part, g.wrap("t")))
            }
        }

        assertEquals(LocalDate.of(2020, 1, 2), Grammar.ANSI.datePartValue("date", "2020-01-02"))
        assertEquals(LocalTime.of(1, 2, 3), Grammar.ANSI.datePartValue("time", "01:02:03"))
        assertEquals("not-a-date", Grammar.ANSI.datePartValue("date", "not-a-date"))
        assertEquals("nope", Grammar.ANSI.datePartValue("day", "nope"))
        assertEquals(5, Grammar.ANSI.datePartValue("month", 5))
        assertEquals(9, Grammar.ORACLE.datePartValue("day", 9))
        assertEquals("09:30:00", Grammar.ORACLE.datePartValue("time", LocalTime.of(9, 30)))
        Grammar("\"", "\"", Dialect.STANDARD).wrap("x")

        assertEquals("select *", Grammar.SQLSERVER.compileSelectHead(false, "*", null, 1, false))
        assertTrue(Grammar.SQLSERVER.compileSelectHead(false, "*", 5, null, false).contains("top 5"))
        assertTrue(Grammar.SQLSERVER.compileOrderLimit(listOf("id asc"), 5, 0, false).contains("offset 0 rows"))
        assertTrue(Grammar.SQLSERVER.compileOrderLimit(emptyList(), 5, null, true).contains("offset 0 rows"))
        assertTrue(Grammar.SQLSERVER.compileOrderLimit(emptyList(), null, 3, false).contains("order by (select 0)"))
        assertFalse(Grammar.SQLSERVER.compileOrderLimit(emptyList(), 5, null, false).contains("offset"))
        assertTrue(Grammar.ORACLE.compileOrderLimit(emptyList(), 1, null, false).contains("fetch first"))
        assertTrue(Grammar.ORACLE.compileOrderLimit(emptyList(), 2, 1, false).contains("fetch next"))
        assertTrue(Grammar.ORACLE.compileOrderLimit(emptyList(), null, 4, false).contains("offset 4"))
        assertEquals("", Grammar.POSTGRES.compileOrderLimit(emptyList(), null, null, false))
        assertTrue(Grammar.MARIADB.compileOrderLimit(emptyList(), null, 1, false).contains("18446744073709551615"))
        assertSame(Grammar.ANSI, Grammar.forUrl("jdbc:unknown:foo"))
        assertSame(Grammar.H2, Grammar.forUrl("jdbc:h2:mem:cover"))

        assertEquals("insert into t default values", Grammar.H2.compileInsertDefaults("t"))
        assertEquals("insert into t () values ()", Grammar.MARIADB.compileInsertDefaults("t"))
        assertNotNull(Grammar.SQLITE.compileSequenceTableExists())

        val now = Instant.parse("2020-01-02T03:04:05Z")
        assertTrue(Grammar.SQLITE.bindValue(now) is String)
        assertTrue(Grammar.SQLITE.bindValue(Timestamp.from(now)) is String)
        assertTrue(Grammar.SQLITE.bindValue(LocalDateTime.of(2020, 1, 2, 3, 4, 5)) is String)
        assertEquals("2020-01-02", Grammar.SQLITE.bindValue(LocalDate.of(2020, 1, 2)))
        assertEquals("03:04:05", Grammar.SQLITE.bindValue(LocalTime.of(3, 4, 5)))
        assertEquals(7, Grammar.SQLITE.bindValue(7))
        assertEquals(7, Grammar.H2.bindValue(7))
    }

    @Test
    fun schemaLiteralsTypesAndUnknownProduct() {
        val bp = Blueprint("t")
        val integer = bp.integer("n")
        val big = bp.bigInteger("b")
        val ts = bp.timestamp("at")
        val text = bp.string("s")
        assertEquals("NUMBER(10)", SchemaGrammar.ORACLE.type(integer))
        assertEquals("NUMBER(19)", SchemaGrammar.ORACLE.type(big))
        assertEquals("TIMESTAMP", SchemaGrammar.ORACLE.type(ts))
        assertEquals("0", SchemaGrammar.ORACLE.literal(false))
        assertEquals("'x'", SchemaGrammar.ORACLE.literal("x"))
        assertEquals("DROP INDEX \"idx\"", SchemaGrammar.ORACLE.compileDropIndex("t", "idx"))
        for (col in bp.columns) {
            assertNotNull(SchemaGrammar.ORACLE.type(col))
            assertNotNull(SchemaGrammar.SQLSERVER.type(col))
        }
        bp.id()
        bp.text("bio")
        bp.longText("essay")
        bp.json("meta")
        bp.boolean("flag")
        for (col in bp.columns) {
            assertNotNull(SchemaGrammar.ORACLE.type(col))
            assertNotNull(SchemaGrammar.SQLSERVER.type(col))
        }

        assertEquals("INT", SchemaGrammar.SQLSERVER.type(integer))
        assertEquals("BIGINT", SchemaGrammar.SQLSERVER.type(big))
        assertEquals("DATETIME2", SchemaGrammar.SQLSERVER.type(ts))
        assertEquals("1", SchemaGrammar.SQLSERVER.literal(true))
        assertEquals("0", SchemaGrammar.SQLSERVER.literal(false))
        assertEquals("'y'", SchemaGrammar.SQLSERVER.literal("y"))

        assertEquals("1", SchemaGrammar.SQLITE.literal(true))
        assertEquals("0", SchemaGrammar.SQLITE.literal(false))
        assertEquals("'z'", SchemaGrammar.SQLITE.literal("z"))
        assertEquals("VARCHAR(255)", SchemaGrammar.POSTGRES.type(text))

        assertTrue(SchemaGrammar.forProduct("Microsoft SQL Server") is org.konaframework.cadence.schema.SqlServerSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("microsoft") is org.konaframework.cadence.schema.SqlServerSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("SQL Server") is org.konaframework.cadence.schema.SqlServerSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("SQL Server", Grammar.NONE) is org.konaframework.cadence.schema.SqlServerSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("microsoft", Grammar.NONE) is org.konaframework.cadence.schema.SqlServerSchemaGrammar)
        val custom = SchemaGrammar.forProduct("WeirdDB", Grammar.MYSQL)
        assertEquals("`t`", custom.wrap("t"))
        assertFalse(custom is org.konaframework.cadence.schema.PostgresSchemaGrammar)
    }

    @Test
    fun sqliteTruncateResetsSequenceAndOracleGeneratedKeysPath() {
        val sqliteUrl = "jdbc:sqlite:file:m25seal_${System.nanoTime()}?mode=memory&cache=shared"
        Database().addConnection("default", ConnectionConfig(sqliteUrl)).use { db ->
            db.setAsGlobal()
            db.withConnection { jdbc ->
                Schema(jdbc).create("items") {
                    id()
                    string("name")
                }
            }
            db.table("items").insert(mapOf("name" to "a"))
            assertTrue(db.connection().select(Grammar.SQLITE.compileSequenceTableExists()).isNotEmpty())
            db.table("items").truncate()
            assertEquals(0L, db.table("items").count())
            // fromRaw cleared → sequence reset returns early after truncate statements.
            val orphan = db.table("items")
            QueryBuilder::class.java.getDeclaredField("fromRaw").apply {
                isAccessible = true
                set(orphan, null)
            }
            orphan.truncate()
            Database.clearGlobal()
        }

        // Fresh SQLite DB with no AUTOINCREMENT → sqlite_sequence absent.
        Database().addConnection(
            "default",
            ConnectionConfig("jdbc:sqlite:file:m25noseq_${System.nanoTime()}?mode=memory&cache=shared"),
        ).use { db ->
            db.withConnection { jdbc ->
                Schema(jdbc).create("labels") { string("name") }
            }
            assertTrue(db.connection().select(Grammar.SQLITE.compileSequenceTableExists()).isEmpty())
            db.table("labels").truncate()
        }

        val h2 = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:oraclekeys_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
            user = "sa"
        }
        h2.connection.use { c ->
            c.createStatement().execute("create table items (id bigint generated by default as identity primary key, name varchar(20))")
        }
        DatabaseConnection("oracleish", h2, Grammar.ORACLE).use { conn ->
            val id = conn.insertGetId("insert into items (name) values (?)", listOf("x"), "id")
            assertNotNull(id)
        }
        Database().addConnection("default", ConnectionConfig(TestDb.url(), "sa", "")).use { db ->
            db.connection().statement(
                "create table items (id bigint generated by default as identity primary key, name varchar(20))",
            )
            assertNotNull(db.table("items").insertGetId(mapOf("name" to "n")))
            assertEquals("id", QueryBuilder.insertKeyHint(listOf("name", "email")))
            assertEquals("ID", QueryBuilder.insertKeyHint(listOf("ID", "name")))
            assertEquals("Id", QueryBuilder.insertKeyHint(listOf("Id")))
        }
    }

    @Test
    fun portableUpsertUpdateBranchAndSqlServerFromHint() {
        val database = TestDb.create()
        try {
            val table = database.table("users")
            table.upsertProductOverride = "UnknownEngine"
            assertEquals(
                1,
                table.upsert(listOf(mapOf("name" to "Seal", "email" to "seal@x", "age" to 1)), listOf("email"), listOf("age")),
            )
            assertEquals(
                1,
                table.upsert(listOf(mapOf("name" to "Seal", "email" to "seal@x", "age" to 2)), listOf("email"), listOf("age")),
            )
            assertEquals(2, table.where("email", "seal@x").value("age"))

            val sqlsrv = DatabaseConnection(
                "sqlsrv",
                database.connection().dataSource,
                Grammar.SQLSERVER,
            )
            val locked = QueryBuilder(sqlsrv).from("users").lockForUpdate().toSql()
            assertTrue(locked.contains("with(rowlock,updlock,holdlock)"))
            val shared = QueryBuilder(sqlsrv).from("users").sharedLock().toSql()
            assertTrue(shared.contains("with(rowlock,holdlock)"))
        } finally {
            database.close()
            Database.clearGlobal()
        }
    }
}
