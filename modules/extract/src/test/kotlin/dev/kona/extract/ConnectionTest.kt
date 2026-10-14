package dev.kona.extract

import dev.kona.core.Application
import dev.kona.core.Config
import dev.kona.core.Env
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.function.Function
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue

class ConnectionTest {
    enum class Kind { BIG }

    @AfterEach
    fun reset() {
        Database.clearGlobal()
        Env.clear()
    }

    @Test
    fun grammarQuotesIdentifiersOnly() {
        assertEquals("\"users\".\"id\"", Grammar.ANSI.wrap(" users.id "))
        assertEquals("*", Grammar.ANSI.wrap("*"))
        assertEquals("\"u\".*", Grammar.ANSI.wrap("u.*"))
        assertEquals("\"users\".\"name\" as \"n\"", Grammar.ANSI.wrap("users.name AS n"))
        assertEquals("\"a\"\"b\"", Grammar.ANSI.wrap("a\"b"))
        assertEquals("`a```", Grammar.MYSQL.wrap("a`"))
        assertEquals("users.id as k", Grammar.NONE.wrap("users.id as k"))
        assertEquals("\"a\", \"b\"", Grammar.ANSI.columnize(listOf("a", "b")))
        assertSame(Grammar.MYSQL, Grammar.forUrl("jdbc:mysql://h/db"))
        assertSame(Grammar.MYSQL, Grammar.forUrl("jdbc:mariadb://h/db"))
        assertSame(Grammar.ANSI, Grammar.forUrl("jdbc:h2:mem:x"))
    }

    @Test
    fun connectionConfigFromMapsAndEnv() {
        val defaults = ConnectionConfig("jdbc:h2:mem:a")
        assertNull(defaults.username)
        assertNull(defaults.password)
        assertNull(defaults.driver)
        assertEquals(ConnectionConfig.DEFAULT_POOL_SIZE, defaults.poolSize)
        assertTrue(defaults.quoteIdentifiers)
        assertSame(Grammar.ANSI, defaults.grammar())
        assertSame(Grammar.NONE, ConnectionConfig("jdbc:h2:mem:a", quoteIdentifiers = false).grammar())
        assertThrows<IllegalArgumentException> { ConnectionConfig("jdbc:h2:mem:a", poolSize = 0) }

        val full = ConnectionConfig.fromMap(
            mapOf(
                "url" to "jdbc:h2:mem:b", "username" to "sa", "password" to "secret", "driver" to "org.h2.Driver",
                "pool_size" to " 3 ", "quote_identifiers" to "false",
            ),
        )
        assertEquals("jdbc:h2:mem:b", full.url)
        assertEquals("sa", full.username)
        assertEquals("secret", full.password)
        assertEquals("org.h2.Driver", full.driver)
        assertEquals(3, full.poolSize)
        assertFalse(full.quoteIdentifiers)
        assertFalse(full.toString().contains("secret"))
        assertTrue(full.toString().contains("jdbc:h2:mem:b"))

        val camel = ConnectionConfig.fromMap(mapOf("url" to "u", "poolSize" to 4, "quoteIdentifiers" to true))
        assertEquals(4, camel.poolSize)
        assertTrue(camel.quoteIdentifiers)
        assertEquals(10, ConnectionConfig.fromMap(mapOf("url" to "u", "pool_size" to "many")).poolSize)
        assertEquals(10, ConnectionConfig.fromMap(mapOf("url" to "u", "pool_size" to listOf(1))).poolSize)
        val missing = assertThrows<ExtractException> { ConnectionConfig.fromMap(mapOf("username" to "x")) }
        assertTrue(missing.message!!.contains("url"))

        val env = ConnectionConfig.fromEnv(mapOf("DB_URL" to "jdbc:h2:mem:c", "DB_USERNAME" to "sa", "DB_POOL_SIZE" to "2"))
        assertEquals("jdbc:h2:mem:c", env.url)
        assertEquals("sa", env.username)
        assertEquals(2, env.poolSize)
        val lookup = ConnectionConfig.fromEnv(Function<String, String?> { if (it == "DB_URL") "jdbc:h2:mem:d" else null })
        assertEquals("jdbc:h2:mem:d", lookup.url)
    }

    @Test
    fun databaseManagesNamedLazyPools() {
        val url = TestDb.url()
        val database = Database()
            .addConnection("default", ConnectionConfig(url, "sa", "", driver = "org.h2.Driver", poolSize = 1))
            .addConnection("other", ConnectionConfig(TestDb.url()))
        assertEquals("default", database.defaultConnection)
        assertTrue(database.hasConnection("other"))
        assertFalse(database.hasConnection("nope"))
        assertEquals(setOf("default", "other"), database.connectionNames())

        val first = database.connection()
        assertSame(first, database.connection("default"))
        assertEquals("default", first.name)
        assertTrue(database.withConnection { it.isValid(1) })
        assertTrue(database.withConnection("other") { it.isValid(1) })
        assertEquals(1, database.connection("other").select("select 1 as one").single()["one"])

        database.connection().statement("create table t (id int)")
        assertEquals(0L, database.table("t").count())
        assertEquals(5, database.transaction { it.table("t").insert(mapOf("id" to 5)); 5 })

        database.defaultConnection = "other"
        assertEquals("other", database.connection().name)
        database.defaultConnection = "default"

        // Replacing a resolved connection closes the old pool.
        database.addConnection("default", ConnectionConfig(url, "sa", ""))
        assertNotSame(first, database.connection())
        assertEquals(listOf(5), database.table("t").pluck("id"))

        database.disconnect("default")
        database.disconnect("never-opened")
        assertTrue(database.hasConnection("default"))
        assertEquals(listOf(5), database.table("t").pluck("id"))

        val plain = JdbcDataSource().apply { setURL(url); user = "sa" }
        database.addConnection("raw", plain)
        database.addConnection("raw", plain, Grammar.NONE)
        assertSame(Grammar.NONE, database.connection("raw").grammar)
        database.addConnection("other", plain)
        assertSame(plain, database.connection("other").dataSource)
        assertSame(Grammar.ANSI, database.connection("other").grammar)

        val unknown = assertThrows<ExtractException> { database.connection("nope") }
        assertEquals("Database connection [nope] not configured.", unknown.message)
        database.close()
        database.close()
    }

    @Test
    fun globalDatabase() {
        assertFalse(Database.hasGlobal())
        assertThrows<ExtractException> { Database.global() }
        val database = Database("main")
        assertEquals("main", database.defaultConnection)
        assertSame(database, database.setAsGlobal())
        assertTrue(Database.hasGlobal())
        assertSame(database, Database.global())
        Database.clearGlobal()
        assertFalse(Database.hasGlobal())
    }

    @Test
    fun databaseFromConfigShapes() {
        val url = TestDb.url()
        val named = Database.fromMap(
            mapOf("default" to "primary", "connections" to mapOf("primary" to mapOf("url" to url), "replica" to mapOf("url" to url))),
        )
        assertEquals("primary", named.defaultConnection)
        assertEquals(setOf("primary", "replica"), named.connectionNames())
        assertEquals(1, named.connection().select("select 1 as one").size)
        named.close()

        val inline = Database.fromMap(mapOf("default" to mapOf("url" to url)))
        assertEquals("default", inline.defaultConnection)
        assertEquals(setOf("default"), inline.connectionNames())

        val bad = assertThrows<ExtractException> { Database.fromMap(mapOf("connections" to mapOf("x" to "nope"))) }
        assertTrue(bad.message!!.contains("[x]"))

        assertTrue(Database.fromMap(mapOf("connections" to "not-a-map")).connectionNames().isEmpty())
        assertTrue(Database.fromMap(emptyMap<String, Any?>()).connectionNames().isEmpty())

        Env.set("DB_URL", url)
        Env.set("DB_POOL_SIZE", "2")
        val fromEnv = Database.fromMap(emptyMap<String, Any?>())
        assertEquals(setOf("default"), fromEnv.connectionNames())
        assertEquals(1, fromEnv.connection().select("select 1 as one").size)
        fromEnv.close()
        Env.clear()

        val config = Config()
        config.set("database.default", "main")
        config.set("database.connections.main.url", url)
        config.set("database.connections.main.pool_size", "1")
        val fromConfig = Database.fromConfig(config)
        assertEquals("main", fromConfig.defaultConnection)
        assertEquals(1, fromConfig.connection().select("select 1 as one").size)
        fromConfig.close()
        assertTrue(Database.fromConfig(Config()).connectionNames().isEmpty())
    }

    @Test
    fun rawStatementsBindingAndTypes() {
        val connection = TestDb.create().connection()
        assertTrue(connection.insert("insert into files (body, data, kind, at) values (?, ?, ?, ?)", listOf("text", byteArrayOf(1, 2), Kind.BIG, Instant.EPOCH)))
        val id = connection.insertGetId("insert into files (body, data, kind, at) values (?, ?, ?, ?)", listOf(null, null, null, null))
        assertEquals(2L, id)
        assertNull(connection.insertGetId("insert into plain (name) values (?)", listOf("p")))

        val row = connection.selectOne("select * from files where id = ?", listOf(1))!!
        assertEquals("text", row["body"])
        assertArrayEquals(byteArrayOf(1, 2), row["data"] as ByteArray)
        assertEquals("BIG", row["kind"])
        assertEquals(Timestamp.from(Instant.EPOCH), row["at"])
        assertNull(connection.selectOne("select * from files where id = ?", listOf(99)))
        assertEquals(2, connection.select("select * from files").size)

        assertEquals(1, connection.update("update files set kind = ? where id = ?", listOf("x", 2)))
        assertEquals(1, connection.affectingStatement("update files set kind = ? where id = ?", listOf("y", 2)))
        assertEquals(1, connection.delete("delete from files where id = ?", listOf(2)))
        assertTrue(connection.statement("create table extra (id int)"))
        assertTrue(connection.insert("insert into extra values (1)"))
        assertEquals(1, connection.update("update extra set id = 2"))
        assertEquals(1, connection.affectingStatement("update extra set id = 3"))
        assertEquals(1, connection.delete("delete from extra"))
        assertEquals(3L, connection.insertGetId("insert into files (kind) values ('z')"))
        assertEquals(2, connection.query().from("files").count().toInt())
        assertNull(connection.selectOne("select * from extra"))

        val failure = assertThrows<QueryException> { connection.select("select * from missing where id = ?", listOf(1)) }
        assertEquals("select * from missing where id = ?", failure.sql)
        assertEquals(listOf<Any?>(1), failure.bindings)
        assertTrue(failure.message!!.contains("(SQL: select * from missing"))
    }

    @Test
    fun transactionsCommitRollbackAndNest() {
        val connection = TestDb.create().connection()
        assertFalse(connection.inTransaction())
        connection.transaction { tx ->
            assertTrue(tx.inTransaction())
            tx.table("tags").insert(mapOf("code" to "a"))
            tx.transaction { inner -> inner.table("tags").insert(mapOf("code" to "b")) }
            val outer = tx.withConnection { it }
            assertSame(outer, tx.withConnection { it })
        }
        assertFalse(connection.inTransaction())
        assertEquals(2L, connection.table("tags").count())

        val error = assertThrows<IllegalStateException> {
            connection.transaction<Unit> {
                it.table("tags").insert(mapOf("code" to "c"))
                throw IllegalStateException("boom")
            }
        }
        assertEquals("boom", error.message)
        assertEquals(2L, connection.table("tags").count())
        assertTrue(connection.withConnection { it.autoCommit })

        DatabaseConnection("plain", JdbcDataSource().apply { setURL(TestDb.url()) }).close()
    }

    @Test
    fun dbFacadeUsesGlobalDatabase() {
        val database = TestDb.create()
        val other = TestDb.url()
        database.addConnection("other", ConnectionConfig(other))
        assertSame(database.connection(), DB.connection())
        assertSame(database.connection("other"), DB.connection("other"))
        assertTrue(DB.statement("create table d (id int, name varchar(10))"))
        assertTrue(DB.statement("insert into d values (?, ?)", listOf(9, "nine")))
        assertTrue(DB.insert("insert into d values (1, 'one')"))
        assertTrue(DB.insert("insert into d values (?, ?)", listOf(2, "two")))
        assertEquals(3, DB.select("select * from d").size)
        assertEquals("two", DB.select("select name from d where id = ?", listOf(2)).single()["name"])
        assertEquals(3, DB.update("update d set name = 'x'"))
        assertEquals(1, DB.update("update d set name = ? where id = ?", listOf("y", 1)))
        assertEquals(1, DB.delete("delete from d where id = ?", listOf(9)))
        assertEquals(1, DB.transaction { it.table("d").where("id", 1).delete() })
        assertEquals(1, DB.delete("delete from d"))
        assertEquals(0L, DB.table("d").count())
        database.close()
    }

    @Test
    fun serviceProviderRegistersGlobalDatabase(@TempDir dir: Path) {
        val url = TestDb.url()
        val app = Application.builder(dir)
            .configure { it.set("database.connections.default.url", url) }
            .withProvider(ExtractServiceProvider())
            .build()
        val database = app.make(Database::class.java)
        assertSame(database, Database.global())
        assertSame(database, app.container.resolve<Database>("db"))
        assertEquals(1, DB.select("select 1 as one").size)
        database.close()
    }
}
