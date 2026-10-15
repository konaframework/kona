package dev.kona.extract.schema

import dev.kona.extract.Grammar
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class SchemaTest {
    private lateinit var connection: Connection
    private lateinit var schema: Schema

    @BeforeEach
    fun open() {
        connection = DriverManager.getConnection("jdbc:h2:mem:schema_${System.nanoTime()};DB_CLOSE_DELAY=-1")
        schema = Schema(connection)
    }

    @AfterEach
    fun close() {
        connection.close()
    }

    @Test
    fun detectsH2Grammar() {
        assertSame(SchemaGrammar.H2, schema.grammar)
        assertSame(SchemaGrammar.STANDARD, SchemaGrammar.forProduct("PostgreSQL"))
        assertSame(SchemaGrammar.STANDARD, SchemaGrammar.POSTGRES)
        assertSame(SchemaGrammar.H2, SchemaGrammar.forProduct("h2"))
        val bare = SchemaGrammar.forProduct("H2", Grammar.NONE)
        assertTrue(bare is H2SchemaGrammar)
        assertEquals("users", bare.wrap("users"))
        val mysqlQuoted = SchemaGrammar.forProduct("PostgreSQL", Grammar.MYSQL)
        assertFalse(mysqlQuoted is H2SchemaGrammar)
        assertEquals("`t`", mysqlQuoted.wrap("t"))
        assertSame(Grammar.ANSI, SchemaGrammar().identifiers)
    }

    @Test
    fun createsTablesWithEveryColumnTypeAndRoundTrips() {
        schema.create("users") {
            id()
            string("email", 120).unique()
            string("name").index()
            text("bio").nullable()
            integer("age").default(18)
            bigInteger("visits").default(0L)
            boolean("active").default(true)
            boolean("admin").default(false)
            string("role").default("it's member")
            string("nickname").nullable().default(null)
            timestamp("joined_at").useCurrent()
            timestamps()
            softDeletes()
        }
        schema.create("posts") {
            id()
            foreignId("user_id").constrained().cascadeOnDelete()
            foreignId("editor_id").nullable().constrained("users").nullOnDelete().onUpdate("cascade")
            string("title")
            index(listOf("user_id", "title"), "posts_owner_title")
            unique("title", "editor_id")
        }

        assertTrue(schema.hasTable("users"))
        assertTrue(schema.hasTable("USERS"))
        assertFalse(schema.hasTable("missing"))
        assertTrue(schema.hasColumn("posts", "user_id"))
        assertFalse(schema.hasColumn("posts", "nope"))

        val long = "x".repeat(5000)
        connection.createStatement().use { st ->
            st.execute("""INSERT INTO "users" ("email", "name", "bio") VALUES ('a@b.c', 'Ann', '$long')""")
            st.executeQuery("""SELECT "id", "age", "visits", "active", "admin", "role", "bio", "joined_at", "nickname" FROM "users"""").use { rs ->
                assertTrue(rs.next())
                assertEquals(1L, rs.getLong(1))
                assertEquals(18, rs.getInt(2))
                assertEquals(0L, rs.getLong(3))
                assertTrue(rs.getBoolean(4))
                assertFalse(rs.getBoolean(5))
                assertEquals("it's member", rs.getString(6))
                assertEquals(long, rs.getObject(7))
                assertTrue(rs.getTimestamp(8) != null)
                assertEquals(null, rs.getString(9))
            }
            st.execute("""INSERT INTO "posts" ("user_id", "editor_id", "title") VALUES (1, 1, 'Hi')""")
            assertThrows(SQLException::class.java) {
                st.execute("""INSERT INTO "posts" ("user_id", "title") VALUES (99, 'Bad')""")
            }
            assertThrows(SQLException::class.java) {
                st.execute("""INSERT INTO "users" ("email", "name") VALUES ('a@b.c', 'Dup')""")
            }
            st.execute("""DELETE FROM "users"""")
            st.executeQuery("""SELECT COUNT(*) FROM "posts"""").use { rs ->
                rs.next()
                assertEquals(0, rs.getInt(1))
            }
        }
    }

    @Test
    fun altersRenamesAndDrops() {
        schema.create("items", TableDefinition { t ->
            t.id()
            t.string("sku")
            t.string("legacy")
            t.index("sku")
        })
        schema.create("owners") { id() }
        schema.table("items") {
            dropIndex("items_sku_index")
            dropColumn("legacy")
            integer("qty").default(1)
            foreignId("owner_id").nullable()
            foreign("owner_id").references("id").on("owners").onDelete("restrict")
            unique("sku")
        }
        assertFalse(schema.hasColumn("items", "legacy"))
        assertTrue(schema.hasColumn("items", "qty"))
        schema.table("items", TableDefinition { t -> t.text("notes").nullable() })
        assertTrue(schema.hasColumn("items", "notes"))

        schema.rename("items", "products")
        assertTrue(schema.hasTable("products"))
        assertFalse(schema.hasTable("items"))
        schema.drop("products")
        assertFalse(schema.hasTable("products"))
        schema.dropIfExists("products")
        schema.dropIfExists("owners")
        assertThrows(SQLException::class.java) { schema.drop("owners") }
    }

    @Test
    fun standardGrammarSql() {
        val grammar = SchemaGrammar.STANDARD
        val blueprint = Blueprint("a\"b").apply {
            id()
            text("body")
            string("code", 10).nullable()
        }
        assertEquals(
            listOf(
                "CREATE TABLE \"a\"\"b\" (\"id\" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "\"body\" TEXT NOT NULL, \"code\" VARCHAR(10) NULL)",
            ),
            grammar.compileCreate(blueprint),
        )
        assertEquals("DROP INDEX \"i\"", grammar.compileDropIndex("t", "i"))
        assertEquals("ALTER TABLE \"a\" RENAME TO \"b\"", grammar.compileRename("a", "b"))
        assertEquals("3.5", grammar.literal(3.5))
        assertThrows(IllegalArgumentException::class.java) { grammar.compileCreate(Blueprint("empty")) }
    }

    @Test
    fun blueprintValidationAndAccessors() {
        val blueprint = Blueprint("t")
        assertThrows(IllegalArgumentException::class.java) { blueprint.string("s", 0) }
        assertThrows(IllegalArgumentException::class.java) { blueprint.index() }
        val column = blueprint.timestamp("seen").nullable(false)
        assertFalse(column.isNullable)
        assertFalse(column.hasDefault)
        assertEquals(null, column.defaultValue)
        val key = blueprint.foreign("x_id")
        assertEquals("t_x_id_foreign", key.name)
        assertEquals("t", key.table)
        assertEquals("x_id", key.column)
        assertEquals(null, key.onDeleteAction)
        assertEquals(null, key.onUpdateAction)
        assertEquals("", key.onTable)
        assertEquals("id", key.referencesColumn)
        assertEquals(IndexDefinition("t_a_unique", listOf("a"), true), blueprint.unique("a"))
        assertEquals(1, blueprint.indexes.size)
        blueprint.dropColumn("a", "b")
        assertEquals(listOf("a", "b"), blueprint.dropColumns)
        assertEquals("deleted", blueprint.softDeletes("deleted").name)
        val morphs = Blueprint("comments").apply {
            morphs("commentable")
            nullableMorphs("taggable")
            uuidMorphs("owner")
            morphs("named", "comments_named_idx")
            nullableMorphs("soft", "comments_soft_idx")
            uuidMorphs("uuid_named", "comments_uuid_idx")
            // Explicit null indexName hits the full JVM overload body (closing braces).
            morphs("explicit", null)
            nullableMorphs("explicit_null", null)
            uuidMorphs("explicit_uuid", null)
        }
        assertTrue(morphs.columns.any { it.name == "commentable_type" })
        assertTrue(morphs.columns.any { it.name == "commentable_id" })
        assertTrue(morphs.columns.first { it.name == "taggable_type" }.isNullable)
        assertEquals(36, morphs.columns.first { it.name == "owner_id" }.length)
        assertTrue(morphs.indexes.any { it.name == "comments_named_idx" })
        assertTrue(morphs.indexes.any { it.name == "comments_soft_idx" })
        assertTrue(morphs.indexes.any { it.name == "comments_uuid_idx" })
    }
}
