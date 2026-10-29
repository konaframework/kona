package org.konaframework.grind

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CastType
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.SoftDeletes
import org.konaframework.cadence.schema.Schema
import org.konaframework.cadence.schema.SchemaColumn
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FireflyResourceGenerateTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() {
        Database.clearGlobal()
    }

    @Test
    fun stubNameIsSingleTextField() {
        val stub = FireflyResourceGenerate.stubName()
        assertEquals(listOf("TextInput"), stub.form.map { it.type })
        assertEquals(listOf("name"), stub.form.map { it.name })
        assertEquals(listOf("TextColumn"), stub.table.map { it.type })
        assertTrue(stub.table[0].callExpr().contains(".searchable().sortable()"))
        assertEquals(listOf("TextEntry"), stub.infolist.map { it.type })
        assertEquals(2, stub.formColumns)
        assertEquals(2, stub.infolistColumns)
    }

    @Test
    fun mapColumnsUsesJdbcTypesAndCasts() {
        val model = GeneratePost()
        val columns = listOf(
            SchemaColumn("id", "BIGINT", false, "", 19),
            SchemaColumn("title", "VARCHAR", false, "", 255),
            SchemaColumn("body", "CLOB", true, "", 0),
            SchemaColumn("published", "BOOLEAN", false, "", 1),
            SchemaColumn("published_at", "TIMESTAMP", true, "", 0),
            SchemaColumn("due_on", "DATE", true, "", 0),
            SchemaColumn("created_at", "TIMESTAMP", true, "", 0),
            SchemaColumn("updated_at", "TIMESTAMP", true, "", 0),
            SchemaColumn("deleted_at", "TIMESTAMP", true, "", 0),
        )
        val fields = FireflyResourceGenerate.mapColumns(columns, model, setOf("id"))
        assertEquals(
            listOf("title", "body", "published", "published_at", "due_on"),
            fields.form.map { it.name },
        )
        assertEquals(
            listOf("TextInput", "Textarea", "Toggle", "DateTimePicker", "DatePicker"),
            fields.form.map { it.type },
        )
        assertFalse(fields.form.any { it.name == "id" || it.name == "created_at" || it.name == "deleted_at" })
        assertTrue(fields.table.any { it.name == "title" && it.type == "TextColumn" })
        assertTrue(fields.table.any { it.name == "title" && it.callExpr().contains(".searchable().sortable()") })
        assertTrue(fields.table.any { it.name == "published" && it.type == "IconColumn" })
        assertTrue(fields.table.any { it.name == "published" && it.callExpr().endsWith(".boolean()") })
        assertTrue(
            fields.table.any {
                it.name == "published_at" &&
                    it.callExpr().contains(".dateTime()") &&
                    it.callExpr().contains(".toggleable().toggledHiddenByDefault()")
            },
        )
        assertTrue(
            fields.table.any {
                it.name == "created_at" &&
                    it.callExpr().contains(".dateTime()") &&
                    it.callExpr().contains(".toggleable().toggledHiddenByDefault()")
            },
        )
        assertTrue(fields.table.any { it.name == "due_on" && it.callExpr().contains(".date()") })
        assertTrue(fields.infolist.any { it.name == "published" && it.type == "IconEntry" })
        assertTrue(fields.infolist.any { it.name == "published" && it.callExpr().endsWith(".boolean()") })
        assertTrue(fields.infolist.any { it.name == "due_on" && it.callExpr().endsWith(".date()") })
        assertTrue(fields.infolist.any { it.name == "published_at" && it.callExpr().endsWith(".dateTime()") })
        assertFalse(fields.table.any { it.name == "id" })
        assertEquals(2, fields.formColumns)
    }

    @Test
    fun mapColumnsMapsSqliteIntegerBooleansAndForeignKeys() {
        val model = object : Cadence<GeneratePost>() {
            override val table: String = "tasks"
            override val timestamps: Boolean = false
            override val fillable: Set<String> = setOf(
                "project_id", "active", "is_done", "has_cover", "can_edit", "was_sent",
                "should_notify", "ready_flag", "title", "sku",
            )
        }
        val fields = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "INTEGER", false, "", 0),
                SchemaColumn("project_id", "INTEGER", false, "", 0),
                SchemaColumn("title", "TEXT", false, "", 0),
                SchemaColumn("active", "INTEGER", false, "", 0),
                SchemaColumn("is_done", "INTEGER", false, "", 0),
                SchemaColumn("has_cover", "INT", false, "", 0),
                SchemaColumn("can_edit", "BIGINT", false, "", 0),
                SchemaColumn("was_sent", "INTEGER", false, "", 0),
                SchemaColumn("should_notify", "INTEGER", false, "", 0),
                SchemaColumn("ready_flag", "INTEGER", false, "", 0),
                SchemaColumn("sku", "VARCHAR", false, "", 32),
                SchemaColumn("notes", "TEXT", true, "", 0),
            ),
            model,
            setOf("id"),
            mapOf("project_id" to org.konaframework.cadence.schema.SchemaForeignKey("project_id", "projects", "id")),
        )
        assertTrue(fields.form.any { it.name == "project_id" && it.type == "Select" })
        assertTrue(
            fields.form.any {
                it.name == "project_id" &&
                    it.callExpr().contains(".label(\"Project\").relationship(\"project\", \"name\")")
            },
        )
        assertTrue(
            fields.table.any {
                it.name == "project.name" && it.callExpr() == "TextColumn.make(\"project.name\").label(\"Project\")"
            },
        )
        assertTrue(
            fields.infolist.any {
                it.name == "project.name" && it.callExpr() == "TextEntry.make(\"project.name\").label(\"Project\")"
            },
        )
        assertTrue(fields.form.any { it.name == "active" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "is_done" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "has_cover" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "can_edit" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "was_sent" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "should_notify" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "ready_flag" && it.type == "Toggle" })
        assertTrue(fields.table.any { it.name == "active" && it.callExpr().endsWith(".boolean()") })
        assertTrue(fields.table.any { it.name == "title" && it.callExpr().contains(".searchable().sortable()") })
        // Guess FK from column name when JDBC reports none.
        val guessed = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("author_id", "BIGINT", false, "", 19),
                SchemaColumn("label", "VARCHAR", false, "", 255),
            ),
            object : Cadence<GeneratePost>() {
                override val table: String = "posts"
                override val timestamps: Boolean = false
                override val fillable: Set<String> = setOf("author_id", "label")
            },
            setOf("id"),
        )
        assertTrue(
            guessed.form.any {
                it.callExpr().contains("Select.make(\"author_id\").label(\"Author\").relationship(\"author\", \"name\")")
            },
        )
        assertTrue(
            guessed.table.any {
                it.name == "author.name" && it.callExpr().contains("TextColumn.make(\"author.name\")")
            },
        )
        assertTrue(guessed.table.any { it.name == "label" && it.callExpr().contains(".searchable().sortable()") })
        // First data column searchable when no name/title/label; FK relation from table stem without trailing s.
        val firstCol = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("sku", "VARCHAR", false, "", 32),
                SchemaColumn("org_id", "BIGINT", false, "", 19),
            ),
            object : Cadence<GeneratePost>() {
                override val table: String = "items"
                override val timestamps: Boolean = false
                override val fillable: Set<String> = setOf("sku", "org_id")
            },
            setOf("id"),
            mapOf("org_id" to org.konaframework.cadence.schema.SchemaForeignKey("org_id", "org", "id")),
        )
        assertTrue(firstCol.table.any { it.name == "sku" && it.callExpr().contains(".searchable().sortable()") })
        assertTrue(firstCol.form.any { it.callExpr().contains(".label(\"Org\").relationship(\"org\", \"name\")") })
        assertTrue(firstCol.table.any { it.callExpr().contains("TextColumn.make(\"org.name\")") })
        // Non-integer boolean-ish names stay text; bare `_id` is not guessed as a relation.
        val plain = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("active", "VARCHAR", false, "", 8),
                SchemaColumn("_id", "BIGINT", false, "", 19),
            ),
            object : Cadence<GeneratePost>() {
                override val table: String = "flags"
                override val timestamps: Boolean = false
                override val fillable: Set<String> = setOf("active", "_id")
            },
            setOf("id"),
        )
        assertTrue(plain.form.any { it.name == "active" && it.type == "TextInput" })
        assertTrue(plain.form.any { it.name == "_id" && it.type == "TextInput" })
        assertFalse(plain.form.any { it.name == "_id" && it.type == "Select" })
        // Timestamp-named VARCHAR still toggle-hidden; FK column without `_id` uses table stem.
        val stamps = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("created_at", "VARCHAR", true, "", 32),
                SchemaColumn("team", "BIGINT", false, "", 19),
                SchemaColumn("owner", "BIGINT", false, "", 19),
            ),
            object : Cadence<GeneratePost>() {
                override val table: String = "jobs"
                override val timestamps: Boolean = true
                override val fillable: Set<String> = setOf("team", "owner")
            },
            setOf("id"),
            mapOf(
                "team" to org.konaframework.cadence.schema.SchemaForeignKey("team", "people", "id"),
                "owner" to org.konaframework.cadence.schema.SchemaForeignKey("owner", "s", "id"),
                "boss" to org.konaframework.cadence.schema.SchemaForeignKey("boss", "users", "id"),
            ),
        )
        assertTrue(
            stamps.table.any {
                it.name == "created_at" &&
                    it.callExpr().contains(".dateTime()") &&
                    it.callExpr().contains(".toggleable().toggledHiddenByDefault()")
            },
        )
        assertTrue(
            stamps.infolist.any {
                it.name == "created_at" && it.callExpr().endsWith(".dateTime()")
            },
        )
        assertTrue(stamps.form.any { it.callExpr().contains("Select.make(\"team\").label(\"People\").relationship(\"people\", \"name\")") })
        assertTrue(stamps.form.any { it.callExpr().contains("Select.make(\"owner\").label(\"S\").relationship(\"s\", \"name\")") })
        // Fillable must include boss for the relationName(users→user) path.
        val bosses = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("boss", "BIGINT", false, "", 19),
            ),
            object : Cadence<GeneratePost>() {
                override val table: String = "jobs"
                override val timestamps: Boolean = false
                override val fillable: Set<String> = setOf("boss")
            },
            setOf("id"),
            mapOf("boss" to org.konaframework.cadence.schema.SchemaForeignKey("boss", "users", "id")),
        )
        assertTrue(bosses.form.any { it.callExpr().contains("Select.make(\"boss\").label(\"User\").relationship(\"user\", \"name\")") })
        val shortFk = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("_id", "BIGINT", false, "", 19),
            ),
            object : Cadence<GeneratePost>() {
                override val table: String = "jobs"
                override val timestamps: Boolean = false
                override val fillable: Set<String> = setOf("_id")
            },
            setOf("id"),
            mapOf("_id" to org.konaframework.cadence.schema.SchemaForeignKey("_id", "users", "id")),
        )
        assertTrue(shortFk.form.any { it.callExpr().contains("Select.make(\"_id\").label(\"User\").relationship(\"user\", \"name\")") })
        // INTEGER that is not a boolean hint stays TextInput.
        val notBool = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("count", "INTEGER", false, "", 0),
            ),
            object : Cadence<GeneratePost>() {
                override val table: String = "stats"
                override val timestamps: Boolean = false
                override val fillable: Set<String> = setOf("count")
            },
            setOf("id"),
        )
        assertTrue(notBool.form.any { it.name == "count" && it.type == "TextInput" })
    }

    @Test
    fun mapColumnsRespectsFillable() {
        val model = GeneratePost()
        val columns = listOf(
            SchemaColumn("id", "BIGINT", false, "", 19),
            SchemaColumn("title", "VARCHAR", false, "", 255),
            SchemaColumn("secret", "VARCHAR", true, "", 255),
        )
        val fields = FireflyResourceGenerate.mapColumns(columns, model, setOf("id"))
        assertEquals(listOf("title"), fields.form.map { it.name })
        assertEquals(listOf("title", "secret"), fields.table.map { it.name })
    }

    @Test
    fun fromModelReadsLiveTable() {
        val database = Database().addConnection(
            Database.DEFAULT,
            ConnectionConfig("jdbc:h2:mem:firefly_gen;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
        )
        database.use { db ->
            db.connection().withConnection { jdbc ->
                Schema(jdbc).create("posts") {
                    id()
                    string("title")
                    text("body").nullable()
                    boolean("published").default(false)
                    timestamp("published_at").nullable()
                    timestamps()
                }
            }
            val fields = FireflyResourceGenerate.fromModel(GeneratePost(), db)
            assertTrue(fields.form.any { it.name == "title" && it.type == "TextInput" })
            assertTrue(fields.form.any { it.name == "body" && it.type == "Textarea" })
            assertTrue(fields.form.any { it.name == "published" && it.type == "Toggle" })
            assertTrue(fields.form.any { it.name == "published_at" && it.type == "DateTimePicker" })
            assertFalse(fields.form.any { it.name == "id" })
            assertFalse(fields.form.any { it.name == "created_at" })
        }
    }

    @Test
    fun loadModelRequiresCadenceUnderAppModels() {
        assertThrows(GrindException::class.java) {
            FireflyResourceGenerate.loadModel(javaClass.classLoader, "MissingThing")
        }
        assertThrows(GrindException::class.java) {
            FireflyResourceGenerate.loadModel(javaClass.classLoader, "NotCadence")
        }
        val widget = FireflyResourceGenerate.loadModel(javaClass.classLoader, "Widget")
        assertEquals("widgets", widget.table)
    }

    @Test
    fun resourceSourceEmitsGeneratedComponents() {
        val fields = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("title", "VARCHAR", false, "", 255),
                SchemaColumn("published", "BOOLEAN", false, "", 1),
            ),
            GeneratePost(),
            setOf("id"),
        )
        val kotlin = resourceSource(
            "PostResource",
            "Post",
            "kotlin",
            true,
            "app.panels.admin.resources.post",
            "app.panels.admin.resources.post.pages",
            fields,
        )
        assertTrue(kotlin.contains("Toggle.make(\"published\")"))
        assertTrue(kotlin.contains("TextInput.make(\"title\")"))
        assertTrue(kotlin.contains("IconColumn.make(\"published\").boolean()"))
        assertTrue(kotlin.contains("IconEntry.make(\"published\").boolean()"))
        assertTrue(kotlin.contains("schema.columns(2).components("))
        assertTrue(kotlin.contains("TextColumn.make(\"title\").searchable().sortable()"))
        assertTrue(kotlin.contains("import org.konaframework.firefly.forms.Toggle"))
        assertTrue(kotlin.contains("import org.konaframework.firefly.tables.IconColumn"))
        assertTrue(kotlin.contains("import org.konaframework.firefly.infolists.IconEntry"))
        val java = resourceSource(
            "PostResource",
            "Post",
            "java",
            false,
            "app.panels.admin.resources.post",
            "app.panels.admin.resources.post.pages",
            fields,
        )
        assertTrue(java.contains("Toggle.make(\"published\")"))
        assertTrue(java.contains("IconColumn.make(\"published\").boolean()"))
        assertTrue(java.contains("IconEntry.make(\"published\").boolean()"))
        assertTrue(java.contains("new ListPost()"))
    }

    @Test
    fun generateFlagWritesIntrospectedResource() {
        val project = databaseProject(temp.resolve("gen_resource"), "grind_firefly_gen")
        val boot = "--bootstrap=org.konaframework.grind.CadenceFixtureApp"
        // widgets table: id + name (+ color after second migration). Model is on the test classpath.
        assertEquals(0, GrindApplication().run(arrayOf("migrate", "--base=$project", boot)))
        val code = MakeFireflyResourceCommand().run(
            listOf(
                "Widget",
                "--simple",
                "--generate",
                "--base=$project",
                "--lang=kotlin",
                boot,
            ),
        )
        assertEquals(0, code)
        val source = Files.readString(
            project.resolve("src/main/kotlin/app/panels/admin/resources/widget/WidgetResource.kt"),
        )
        assertTrue(source.contains("package app.panels.admin.resources.widget"))
        assertTrue(source.contains("TextInput.make(\"name\")"))
        assertTrue(source.contains("TextColumn.make(\"name\")") || source.contains("TextColumn.make(\"color\")"))
        assertFalse(source.contains("TextInput.make(\"id\")"))
        assertTrue(Files.exists(project.resolve("src/main/kotlin/app/panels/admin/resources/widget/pages/ManageWidget.kt")))
    }

    @Test
    fun generateFailsClosedWhenModelMissing() {
        val project = databaseProject(temp.resolve("gen_missing"), "grind_firefly_missing")
        val boot = "--bootstrap=org.konaframework.grind.CadenceFixtureApp"
        assertEquals(0, GrindApplication().run(arrayOf("migrate", "--base=$project", boot)))
        assertThrows(GrindException::class.java) {
            MakeFireflyResourceCommand().run(
                listOf("Ghost", "--generate", "--base=$project", "--lang=kotlin", boot),
            )
        }
        assertFalse(Files.exists(project.resolve("src/main/kotlin/app/panels/admin/resources/ghost/GhostResource.kt")))
    }

    @Test
    fun fromModelFailsWhenTableMissing() {
        val database = Database().addConnection(
            Database.DEFAULT,
            ConnectionConfig("jdbc:h2:mem:firefly_missing_table;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
        )
        database.use { db ->
            val ex = assertThrows(GrindException::class.java) {
                FireflyResourceGenerate.fromModel(GeneratePost(), db)
            }
            assertTrue(ex.message!!.contains("does not exist"))
        }
    }

    @Test
    fun mapColumnsCoversEdgeKindsAndFallbacks() {
        val model = object : Cadence<GeneratePost>() {
            override val table: String = "edge"
            override val timestamps: Boolean = false
            override val fillable: Set<String> = emptySet()
            override val casts: Map<String, Any> = mapOf("payload" to CastType.JSON)
        }
        // Empty primary set → uses model.primaryKey; only PK column → empty form throws.
        val onlyPk = assertThrows(GrindException::class.java) {
            FireflyResourceGenerate.mapColumns(
                listOf(SchemaColumn("id", "BIGINT", false, "", 19)),
                model,
                emptySet(),
            )
        }
        assertTrue(onlyPk.message!!.contains("No form fields"))
        val fields = FireflyResourceGenerate.mapColumns(
            listOf(
                SchemaColumn("id", "BIGINT", false, "", 19),
                SchemaColumn("active", "BOOLEAN", false, "", 1),
                SchemaColumn("flag", "BIT", false, "", 1),
                SchemaColumn("tiny", "TINYINT", false, "", 1),
                SchemaColumn("oracle_flag", "NUMBER", false, "", 1),
                SchemaColumn("numeric_flag", "NUMERIC", false, "", 1),
                SchemaColumn("decimal_flag", "DECIMAL", false, "", 1),
                SchemaColumn("wide_number", "NUMBER", false, "", 10),
                SchemaColumn("float_one", "FLOAT", false, "", 1),
                SchemaColumn("wide_tiny", "TINYINT", false, "", 4),
                SchemaColumn("when_dt2", "DATETIME2", true, "", 0),
                SchemaColumn("payload", "VARCHAR", true, "", 255),
                SchemaColumn("blobby", "BLOB", true, "", 0),
                SchemaColumn("large", "CHARACTER LARGE OBJECT", true, "", 0),
                SchemaColumn("notes", "TEXT", true, "", 0),
                SchemaColumn("doc", "CLOB", true, "", 0),
                SchemaColumn("meta", "JSON", true, "", 0),
                SchemaColumn("when_tz", "TIMESTAMPTZ", true, "", 0),
                SchemaColumn("when_dt", "DATETIME", true, "", 0),
                SchemaColumn("when_ts", "TIMESTAMP", true, "", 0),
                SchemaColumn("essay", "VARCHAR", true, "", 20_000),
                SchemaColumn("label", "CHAR", true, "", 12_000),
                SchemaColumn("wide_int", "INTEGER", true, "", 20_000),
                SchemaColumn("happened", "VARCHAR", true, "", 255),
            ),
            object : Cadence<GeneratePost>() {
                override val table: String = "edge"
                override val timestamps: Boolean = false
                override val fillable: Set<String> = emptySet()
                override val casts: Map<String, Any> = mapOf(
                    "payload" to CastType.JSON,
                    "happened" to CastType.INSTANT,
                )
            },
            setOf("id"),
        )
        assertTrue(fields.form.any { it.name == "active" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "flag" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "tiny" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "oracle_flag" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "numeric_flag" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "decimal_flag" && it.type == "Toggle" })
        assertTrue(fields.form.any { it.name == "wide_number" && it.type == "TextInput" })
        assertTrue(fields.form.any { it.name == "float_one" && it.type == "TextInput" })
        assertTrue(fields.infolist.any { it.name == "tiny" && it.callExpr() == "IconEntry.make(\"tiny\").boolean()" })
        assertTrue(fields.table.any { it.name == "tiny" && it.callExpr() == "IconColumn.make(\"tiny\").boolean()" })
        assertTrue(fields.form.any { it.name == "wide_tiny" && it.type == "TextInput" })
        assertTrue(fields.form.any { it.name == "when_dt2" && it.type == "DateTimePicker" })
        assertTrue(fields.form.any { it.name == "payload" && it.type == "Textarea" })
        assertTrue(fields.form.any { it.name == "blobby" && it.type == "Textarea" })
        assertTrue(fields.form.any { it.name == "large" && it.type == "Textarea" })
        assertTrue(fields.form.any { it.name == "notes" && it.type == "Textarea" })
        assertTrue(fields.form.any { it.name == "doc" && it.type == "Textarea" })
        assertTrue(fields.form.any { it.name == "meta" && it.type == "Textarea" })
        assertTrue(fields.form.any { it.name == "when_tz" && it.type == "DateTimePicker" })
        assertTrue(fields.form.any { it.name == "when_dt" && it.type == "DateTimePicker" })
        assertTrue(fields.form.any { it.name == "when_ts" && it.type == "DateTimePicker" })
        assertTrue(fields.form.any { it.name == "essay" && it.type == "Textarea" })
        assertTrue(fields.form.any { it.name == "label" && it.type == "Textarea" })
        assertTrue(fields.form.any { it.name == "wide_int" && it.type == "TextInput" })
        assertTrue(fields.form.any { it.name == "happened" && it.type == "DateTimePicker" })
        // Named connection path + empty-column guard.
        val database = Database().addConnection(
            "other",
            ConnectionConfig("jdbc:h2:mem:firefly_named;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
        )
        database.defaultConnection = "other"
        database.use { db ->
            db.connection("other").withConnection { jdbc ->
                Schema(jdbc).create("edge") { id(); string("title") }
            }
            val named = object : Cadence<GeneratePost>() {
                override val table: String = "edge"
                override val connectionName: String? = "other"
                override val timestamps: Boolean = false
            }
            val generated = FireflyResourceGenerate.fromModel(named, db, "other")
            assertTrue(generated.form.any { it.name == "title" })
            // connectionName on the model without explicit override.
            val viaModelConn = FireflyResourceGenerate.fromModel(named, db)
            assertTrue(viaModelConn.form.any { it.name == "title" })
        }
    }
}

/** Cadence model used by generate tests (`app.models.Widget` for the live DB path). */
class GeneratePost : Cadence<GeneratePost>(), SoftDeletes {
    override val table: String = "posts"
    override val fillable: Set<String> = setOf("title", "body", "published", "published_at", "due_on")
    override val casts: Map<String, Any> = mapOf(
        "published" to CastType.BOOLEAN,
        "published_at" to CastType.DATETIME,
    )
}
