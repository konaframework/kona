package org.konaframework.cadence

import org.konaframework.cadence.schema.Blueprint
import org.konaframework.cadence.schema.MariaDbSchemaGrammar
import org.konaframework.cadence.schema.MySqlSchemaGrammar
import org.konaframework.cadence.schema.OracleSchemaGrammar
import org.konaframework.cadence.schema.PostgresSchemaGrammar
import org.konaframework.cadence.schema.SchemaGrammar
import org.konaframework.cadence.schema.SqlServerSchemaGrammar
import org.konaframework.cadence.schema.SqliteSchemaGrammar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Compile-level coverage for every first-party engine (M25). */
class M25DialectTest {
    @Test
    fun grammarForUrlCoversEveryEngine() {
        assertSame(Grammar.H2, Grammar.forUrl("jdbc:h2:mem:x"))
        assertSame(Grammar.SQLITE, Grammar.forUrl("jdbc:sqlite:file.db"))
        assertSame(Grammar.POSTGRES, Grammar.forUrl("jdbc:postgresql://h/db"))
        assertSame(Grammar.MYSQL, Grammar.forUrl("jdbc:mysql://h/db"))
        assertSame(Grammar.MARIADB, Grammar.forUrl("jdbc:mariadb://h/db"))
        assertSame(Grammar.ORACLE, Grammar.forUrl("jdbc:oracle:thin:@//h/db"))
        assertSame(Grammar.SQLSERVER, Grammar.forUrl("jdbc:sqlserver://h"))
        assertSame(Grammar.SQLSERVER, Grammar.forUrl("jdbc:microsoft:sqlserver://h"))
        assertSame(Grammar.SQLSERVER, Grammar.forUrl("jdbc:jtds:sqlserver://h"))
        assertEquals("[users].[id]", Grammar.SQLSERVER.wrap("users.id"))
        assertEquals(Dialect.POSTGRES, Grammar.dialectForProduct("PostgreSQL"))
        assertEquals(Dialect.SQLSERVER, Grammar.dialectForProduct("Microsoft SQL Server"))
        assertEquals(Dialect.ORACLE, Grammar.dialectForProduct("Oracle"))
        assertEquals(Dialect.MARIADB, Grammar.dialectForProduct("MariaDB"))
        assertSame(Grammar.ORACLE, Grammar.of(Dialect.ORACLE))
        assertSame(Grammar.ANSI, Grammar.of(Dialect.STANDARD))
    }

    @Test
    fun schemaGrammarsPerEngine() {
        assertTrue(SchemaGrammar.forProduct("PostgreSQL") is PostgresSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("Oracle") is OracleSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("Microsoft SQL Server") is SqlServerSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("MariaDB") is MariaDbSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("MySQL") is MySqlSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("SQLite") is SqliteSchemaGrammar)

        val bp = Blueprint("users")
        bp.id()
        bp.string("email", 120)
        bp.text("bio")
        bp.longText("essay")
        bp.json("meta")
        bp.boolean("active")
        bp.timestamp("joined_at")
        bp.date("born_on")
        bp.dateTime("happened_at")

        val pg = SchemaGrammar.POSTGRES
        assertTrue(pg.type(bp.columns.first { it.name == "id" }).contains("IDENTITY"))
        assertEquals("JSONB", pg.type(bp.columns.first { it.name == "meta" }))
        assertEquals("DATE", pg.type(bp.columns.first { it.name == "born_on" }))
        assertEquals("TIMESTAMP", pg.type(bp.columns.first { it.name == "happened_at" }))
        assertTrue(pg.compileDropIndex("users", "idx").contains("IF EXISTS"))

        val oracle = SchemaGrammar.ORACLE
        assertTrue(oracle.type(bp.columns.first { it.name == "id" }).contains("IDENTITY"))
        assertEquals("VARCHAR2(120)", oracle.type(bp.columns.first { it.name == "email" }))
        assertEquals("CLOB", oracle.type(bp.columns.first { it.name == "bio" }))
        assertEquals("NUMBER(1)", oracle.type(bp.columns.first { it.name == "active" }))
        assertEquals("DATE", oracle.type(bp.columns.first { it.name == "born_on" }))
        assertEquals("TIMESTAMP", oracle.type(bp.columns.first { it.name == "happened_at" }))
        assertEquals("1", oracle.literal(true))
        assertTrue(oracle.compileDropIfExists("users").contains("SQLCODE != -942"))
        assertTrue(oracle.compileRename("a", "b").contains("RENAME TO"))

        val sqlsrv = SchemaGrammar.SQLSERVER
        assertTrue(sqlsrv.type(bp.columns.first { it.name == "id" }).contains("IDENTITY"))
        assertEquals("NVARCHAR(120)", sqlsrv.type(bp.columns.first { it.name == "email" }))
        assertEquals("NVARCHAR(MAX)", sqlsrv.type(bp.columns.first { it.name == "essay" }))
        assertEquals("BIT", sqlsrv.type(bp.columns.first { it.name == "active" }))
        assertEquals("DATE", sqlsrv.type(bp.columns.first { it.name == "born_on" }))
        assertEquals("DATETIME2", sqlsrv.type(bp.columns.first { it.name == "happened_at" }))
        assertTrue(sqlsrv.compileDropIndex("users", "idx").contains(" ON "))
        assertTrue(sqlsrv.compileRename("a", "b").contains("sp_rename"))
        assertTrue(sqlsrv.compileDropIfExists("users").startsWith("DROP TABLE IF EXISTS"))

        val mysql = SchemaGrammar.MYSQL
        assertEquals("DATE", mysql.type(bp.columns.first { it.name == "born_on" }))
        assertEquals("DATETIME", mysql.type(bp.columns.first { it.name == "happened_at" }))
        assertTrue(mysql.compileDropIndex("users", "idx").contains(" ON "))
        assertTrue(mysql.compileRename("a", "b").startsWith("RENAME TABLE"))

        val sqlite = SchemaGrammar.SQLITE
        val alter = Blueprint("users")
        alter.string("nick")
        alter.dropColumn("bio")
        alter.foreign("user_id").references("id").on("accounts")
        val alterSql = sqlite.compileAlter(alter)
        assertTrue(alterSql.any { it.contains("ADD COLUMN") })
        assertTrue(alterSql.any { it.contains("DROP COLUMN") })
        assertFalse(alterSql.any { it.contains("FOREIGN KEY") })

        assertTrue(SchemaGrammar.forProduct("Oracle", Grammar.NONE) is OracleSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("Microsoft SQL Server", Grammar.NONE) is SqlServerSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("PostgreSQL", Grammar.NONE) is PostgresSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("MariaDB", Grammar.NONE) is MariaDbSchemaGrammar)
    }

    @Test
    fun queryPagingLocksAndDatesPerEngine() {
        assertEquals(
            " order by (select 0) offset 5 rows fetch next 10 rows only",
            Grammar.SQLSERVER.compileOrderLimit(emptyList(), 10, 5, false),
        )
        assertTrue(Grammar.SQLSERVER.compileSelectHead(false, "*", 5, null, false).contains("top 5"))
        assertEquals(
            " offset 2 rows fetch next 3 rows only",
            Grammar.ORACLE.compileOrderLimit(emptyList(), 3, 2, false),
        )
        assertEquals(" fetch first 3 rows only", Grammar.ORACLE.compileOrderLimit(emptyList(), 3, null, false))
        assertEquals(" limit 10 offset 2", Grammar.POSTGRES.compileOrderLimit(emptyList(), 10, 2, false))
        assertTrue(Grammar.MYSQL.compileOrderLimit(emptyList(), null, 2, false).contains("18446744073709551615"))
        assertTrue(Grammar.SQLITE.compileOrderLimit(emptyList(), null, 2, false).contains("limit -1"))

        assertEquals(" for update", Grammar.POSTGRES.compileLock(LockMode.UPDATE))
        assertEquals(" for share", Grammar.POSTGRES.compileLock(LockMode.SHARED))
        assertEquals(" lock in share mode", Grammar.MYSQL.compileLock(LockMode.SHARED))
        assertEquals("", Grammar.SQLITE.compileLock(LockMode.UPDATE))
        assertEquals("", Grammar.H2.compileLock(LockMode.SHARED))
        assertTrue(Grammar.SQLSERVER.compileTableLock(LockMode.UPDATE).contains("updlock"))
        assertTrue(Grammar.SQLSERVER.compileTableLock(LockMode.SHARED).contains("holdlock"))
        assertEquals("", Grammar.POSTGRES.compileTableLock(LockMode.UPDATE))

        assertEquals("date(`born`)", Grammar.MYSQL.compileDatePart("date", "`born`"))
        assertEquals("day(`born`)", Grammar.MYSQL.compileDatePart("day", "`born`"))
        assertEquals("day([born])", Grammar.SQLSERVER.compileDatePart("day", "[born]"))
        assertEquals("trunc(\"born\")", Grammar.ORACLE.compileDatePart("date", "\"born\""))
        assertEquals("to_char(\"born\", 'HH24:MI:SS')", Grammar.ORACLE.compileDatePart("time", "\"born\""))
        assertTrue(Grammar.SQLITE.compileDatePart("day", "\"born\"").contains("strftime"))
        assertEquals("cast(\"born\" as date)", Grammar.POSTGRES.compileDatePart("date", "\"born\""))

        assertEquals("09:30:00", Grammar.ORACLE.datePartValue("time", java.time.LocalTime.of(9, 30)))
        assertEquals(12, Grammar.MYSQL.datePartValue("day", "12"))
        assertEquals("delete from t", Grammar.SQLITE.compileTruncate("t").single())
        assertTrue(Grammar.POSTGRES.compileTruncate("t").single().contains("restart identity"))
        assertEquals("insert into t () values ()", Grammar.MYSQL.compileInsertDefaults("t"))
        assertThrows<CadenceException> { Grammar.ORACLE.compileInsertDefaults("t") }
        assertEquals(listOf("id"), Grammar.ORACLE.generatedKeyColumns("id")!!.toList())
        assertEquals(null, Grammar.H2.generatedKeyColumns("id"))
        assertTrue(Grammar.SQLITE.compileSequenceReset()!!.contains("sqlite_sequence"))
        assertEquals(null, Grammar.H2.compileSequenceReset())
    }

    @Test
    fun nativeUpsertEveryEngine() {
        val cols = listOf("email", "name")
        val mysql = NativeUpsert.compileSql("MySQL", Grammar.MYSQL, "`t`", cols, listOf("email"), listOf("name"), 2)!!
        assertTrue(mysql.contains("on duplicate key update"))
        val sqlite = NativeUpsert.compileSql("SQLite", Grammar.SQLITE, "t", cols, listOf("email"), listOf("name"), 1)!!
        assertTrue(sqlite.contains("on conflict"))
        val pg = NativeUpsert.compileSql("PostgreSQL", Grammar.POSTGRES, "t", cols, listOf("email"), emptyList(), 1)!!
        assertTrue(pg.contains("do nothing"))
        val h2 = NativeUpsert.compileSql("H2", Grammar.H2, "t", cols, listOf("email"), listOf("name"), 1)!!
        assertTrue(h2.contains("on conflict"))
        val sqlsrv = NativeUpsert.compileSql("Microsoft SQL Server", Grammar.SQLSERVER, "[t]", cols, listOf("email"), listOf("name"), 1)!!
        assertTrue(sqlsrv.contains("merge into"))
        assertTrue(sqlsrv.endsWith(";"))
        val sqlsrvNoUpdate = NativeUpsert.compileSql(
            "Microsoft SQL Server",
            Grammar.SQLSERVER,
            "[t]",
            cols,
            listOf("email"),
            emptyList(),
            1,
        )!!
        assertTrue(sqlsrvNoUpdate.contains("when not matched"))
        assertFalse(sqlsrvNoUpdate.contains("when matched then update"))
        val oracle = NativeUpsert.compileSql("Oracle", Grammar.ORACLE, "t", cols, listOf("email"), listOf("name"), 2)!!
        assertTrue(oracle.contains("from dual"))
        assertTrue(oracle.contains("union all"))
        assertEquals(null, NativeUpsert.compileSql("OtherDB", Grammar.ANSI, "t", cols, listOf("email"), listOf("name"), 1))
    }
}
