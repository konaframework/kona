package io.konaframework.cadence

import io.konaframework.cadence.Grammar
import io.konaframework.cadence.relations.BelongsToMany
import io.konaframework.cadence.relations.HasMany
import io.konaframework.cadence.relations.MorphMap
import io.konaframework.cadence.relations.MorphTo
import io.konaframework.cadence.relations.Relation
import io.konaframework.cadence.schema.Blueprint
import io.konaframework.cadence.schema.MySqlSchemaGrammar
import io.konaframework.cadence.schema.Schema
import io.konaframework.cadence.schema.SchemaGrammar
import io.konaframework.cadence.schema.SqliteSchemaGrammar
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class B6Author : Cadence<B6Author>() {
    override val table = "authors"
    override val timestamps = false
    override val fillable = setOf("name", "display_name")

    fun books(): HasMany<B6Book> = hasMany(B6Book::class.java, "author_id", "id")

    fun genres(): BelongsToMany<B6Genre> =
        belongsToMany(B6Genre::class.java, "author_genre", "author_id", "genre_id")

    fun getDisplayNameAttribute(): String =
        (getRawAttribute("display_name") as? String) ?: "Anon"

    fun setDisplayNameAttribute(value: Any?) {
        setRawAttribute("display_name", value?.toString()?.uppercase())
    }

    /** Wrong arity on purpose — ensures mutator lookup rejects non-1-arg methods. */
    fun setExtraAttribute(a: Any?, b: Any?) {
        setRawAttribute("extra", a)
    }

    fun getAAttribute(): String = "A"

    companion object : CadenceCompanion<B6Author>(B6Author::class.java)
}

class B6Book : Cadence<B6Book>() {
    override val table = "books"
    override val timestamps = true
    override val fillable = setOf("title", "author_id", "votes")

    companion object : CadenceCompanion<B6Book>(B6Book::class.java)
}

class B6Genre : Cadence<B6Genre>() {
    override val table = "genres"
    override val timestamps = false
    override val fillable = setOf("name")

    companion object : CadenceCompanion<B6Genre>(B6Genre::class.java)
}

class B6Comment : Cadence<B6Comment>() {
    override val table = "comments"
    override val timestamps = false
    override val fillable = setOf("body", "commentable_type", "commentable_id")

    fun commentable(): MorphTo = morphTo("commentable")

    companion object : CadenceCompanion<B6Comment>(B6Comment::class.java)
}

class CadenceB6ParityTest {
    @BeforeEach
    fun setUp() {
        TestDb.create()
        Database.global().withConnection { connection ->
            val schema = Schema(connection)
            schema.create("authors") {
                id()
                string("name")
                string("display_name").nullable()
            }
            schema.create("books") {
                id()
                foreignId("author_id")
                string("title")
                integer("votes")
                timestamps()
            }
            schema.create("genres") {
                id()
                string("name")
            }
            schema.create("author_genre") {
                id()
                foreignId("author_id")
                foreignId("genre_id")
            }
            schema.create("comments") {
                id()
                string("body")
                string("commentable_type")
                bigInteger("commentable_id")
            }
        }
        Relation.morphMap(
            mapOf(
                "authors" to B6Author::class.java,
                "books" to B6Book::class.java,
            ),
        )
        val ada = B6Author.create(mapOf("name" to "Ada", "display_name" to "ada"))
        val bob = B6Author.create(mapOf("name" to "Bob"))
        B6Book.create(mapOf("title" to "A1", "author_id" to ada.getKey(), "votes" to 3))
        B6Book.create(mapOf("title" to "A2", "author_id" to ada.getKey(), "votes" to 7))
        B6Book.create(mapOf("title" to "B1", "author_id" to bob.getKey(), "votes" to 1))
    }

    @AfterEach
    fun tearDown() {
        MorphMap.clear()
        Cadence.reguard()
        Database.clearGlobal()
    }

    @Test
    fun withAvgMaxMinAndLoadAggregates() {
        val rows = B6Author.withAvg("books", "votes")
            .withMax("books", "votes")
            .withMin("books", "votes")
            .orderBy("id")
            .get()
        assertEquals(5.0, (rows[0].getRawAttribute("books_avg_votes") as Number).toDouble(), 0.001)
        assertEquals(7, (rows[0].getRawAttribute("books_max_votes") as Number).toInt())
        assertEquals(3, (rows[0].getRawAttribute("books_min_votes") as Number).toInt())
        assertEquals(1.0, (rows[1].getRawAttribute("books_avg_votes") as Number).toDouble(), 0.001)

        val aliased = B6Author.withAvg("books as avg_votes:votes").where("name", "Ada").first()!!
        assertEquals(5.0, (aliased.getRawAttribute("avg_votes") as Number).toDouble(), 0.001)

        val ada = B6Author.where("name", "Ada").first()!!
        ada.loadAvg("books:votes")
        ada.loadMax("books:votes")
        ada.loadMin("books:votes")
        assertEquals(5.0, (ada.getRawAttribute("books_avg_votes") as Number).toDouble(), 0.001)
        assertEquals(7, (ada.getRawAttribute("books_max_votes") as Number).toInt())
        assertEquals(3, (ada.getRawAttribute("books_min_votes") as Number).toInt())

        assertThrows(IllegalArgumentException::class.java) { B6Author.withAvg("books") }
        assertThrows(IllegalArgumentException::class.java) { B6Author.withMax("books:") }

        assertEquals(
            7,
            (B6Author.withMax("books", "votes").withMin("books:votes").where("name", "Ada").first()!!
                .getRawAttribute("books_max_votes") as Number).toInt(),
        )
        assertEquals(
            3,
            (B6Author.withMax("books", "votes").withMin("books:votes").where("name", "Ada").first()!!
                .getRawAttribute("books_min_votes") as Number).toInt(),
        )
        assertSame(
            B6Author.withMin("books", "votes").where("name", "Ada").first()!!.getKey(),
            B6Author.Companion.withMin("books", "votes").where("name", "Ada").first()!!.getKey(),
        )
        assertEquals(
            7,
            (B6Author.Companion.withMax("books", "votes").where("name", "Ada").first()!!
                .getRawAttribute("books_max_votes") as Number).toInt(),
        )
        assertEquals("Ada", B6Author.Companion.where("name", "Ada").sole()["name"])
        assertEquals(
            3,
            (B6Author.withMin("books:votes").where("name", "Ada").first()!!
                .getRawAttribute("books_min_votes") as Number).toInt(),
        )
        assertThrows(MultipleRecordsFoundException::class.java) { B6Author.sole() }

        val ghost = B6Author()
        assertSame(ghost, ghost.loadAvg("books:votes"))
        assertSame(ghost, ghost.loadMax("books:votes"))
        assertSame(ghost, ghost.loadMin("books:votes"))

        val adaGone = B6Author.where("name", "Ada").first()!!
        Database.global().table("authors").where("id", adaGone.getKey()).delete()
        assertSame(adaGone, adaGone.loadAvg("books:votes"))
    }

    @Test
    fun soleKeyedPluckUnionCrossJoin() {
        assertEquals("Ada", B6Author.where("name", "Ada").sole()["name"])
        assertThrows(ModelNotFoundException::class.java) { B6Author.where("name", "Nope").sole() }
        assertThrows(MultipleRecordsFoundException::class.java) { B6Author.query().sole() }

        val keyed = B6Author.query().orderBy("id").pluck("name", "id")
        assertEquals("Ada", keyed[1L] ?: keyed[1])
        assertEquals(2, keyed.size)
        assertEquals(listOf("Ada", "Bob"), B6Author.query().orderBy("id").pluck("name"))

        val collection = CadenceCollection(B6Author.query().orderBy("id").get())
        assertEquals("Ada", collection.pluck("name", "id").values.first())

        val left = Database.global().table("authors").select("name").where("name", "Ada")
        val right = Database.global().table("authors").select("name").where("name", "Bob")
        val unioned = left.union(right).orderBy("name").get()
        assertEquals(2, unioned.size)
        val unionAll = Database.global().table("authors").select("name").where("name", "Ada")
            .unionAll(Database.global().table("authors").select("name").where("name", "Ada"))
            .get()
        assertEquals(2, unionAll.size)
        assertTrue(
            Database.global().table("authors").select("name")
                .union(
                    Database.global().table("authors").select("name").distinct().where("name", "Bob"),
                )
                .toSql().contains("distinct"),
        )

        val crossed = Database.global().table("authors").crossJoin("genres").limit(1).toSql()
        assertTrue(crossed.contains("cross join"))
        B6Genre.create(mapOf("name" to "SciFi"))
        assertTrue(Database.global().table("authors").crossJoin("genres").count() >= 2)
        assertTrue(B6Author.query().crossJoin("genres").toSql().contains("cross join"))
        assertTrue(
            B6Author.query().select("authors.name").where("name", "Ada")
                .union(Database.global().table("authors").select("name").where("name", "Bob"))
                .toSql().contains("union"),
        )
        assertTrue(
            B6Author.query().select("authors.name").where("name", "Ada")
                .unionAll(Database.global().table("authors").select("name").where("name", "Ada"))
                .toSql().contains("union all"),
        )

        assertEquals("Ada", Database.global().table("authors").where("name", "Ada").sole()["name"])
        assertThrows(CadenceException::class.java) {
            Database.global().table("authors").where("name", "Nope").sole()
        }
        assertThrows(CadenceException::class.java) {
            Database.global().table("authors").sole()
        }
        val keyedMissing = Database.global().table("authors").select("name").pluck("name", "id")
        assertEquals(2, keyedMissing.size)
    }

    @Test
    fun pushUnguardWithoutTimestampsAndAccessors() {
        val ada = B6Author.where("name", "Ada").first()!!
        assertEquals("ADA", ada.getAttribute("display_name"))
        ada.setAttribute("display_name", "ada lovelace")
        assertEquals("ADA LOVELACE", ada.getRawAttribute("display_name"))

        ada.load("books")
        val books = ada.getRelation("books") as List<*>
        val book = books.first() as B6Book
        book.setAttribute("title", "A1-rev")
        assertTrue(ada.push())
        assertEquals("A1-rev", B6Book.find(book.getKey())!!["title"])

        Cadence.unguard()
        assertTrue(B6Author().isFillable("secret"))
        Cadence.reguard()
        assertFalse(B6Author().isFillable("secret"))

        val created = Cadence.runUnguarded {
            B6Author().forceFill(mapOf("name" to "Cid")).also { it.save() }
        }
        assertTrue(created.exists())
        val viaSupplier = Cadence.unguarded(
            java.util.function.Supplier {
                B6Author().forceFill(mapOf("name" to "Dee")).also { it.save() }
            },
        )
        assertTrue(viaSupplier.exists())

        val before = B6Book.where("title", "A1-rev").first()!!.getAttribute("updated_at")
        Cadence.runWithoutTimestamps {
            val b = B6Book.where("title", "A1-rev").first()!!
            b.setAttribute("votes", 99)
            b.save()
        }
        assertEquals(before, B6Book.where("title", "A1-rev").first()!!.getAttribute("updated_at"))
        assertEquals(99, B6Book.where("title", "A1-rev").first()!!["votes"])
        Cadence.withoutTimestamps(
            java.util.function.Supplier {
                val b = B6Book.where("title", "A1-rev").first()!!
                b.setAttribute("votes", 100)
                b.save()
            },
        )
        assertEquals(100, B6Book.where("title", "A1-rev").first()!!["votes"])
        assertEquals(before, B6Book.where("title", "A1-rev").first()!!.getAttribute("updated_at"))

        // Single relation push path (not a collection).
        val solo = B6Author.where("name", "Bob").first()!!
        solo.setRelation("profile", B6Book.where("title", "B1").first())
        assertTrue(solo.push())

        Cadence.unguard()
        assertTrue(Cadence.isUnguarded())
        assertFalse(B6Author().isGuarded("anything"))
        assertFalse(B6Author().isTotallyGuarded())
        Cadence.reguard()
        assertFalse(Cadence.isUnguarded())
        assertFalse(Cadence.isIgnoringTimestamps())
        // Cadence.getAppendedAttribute is excluded as an accessor for key "appended".
        assertNull(B6Author().getAttribute("appended"))
        assertNull(B6Author().getAppendedAttribute("missing"))
        assertEquals("Anon", B6Author().getAttribute("display-name"))
        assertEquals("Anon", B6Author().getAttribute("display name"))
        // Mutator miss → normal write.
        val plain = B6Author.where("name", "Bobby").first() ?: B6Author.where("name", "Bob").first()!!
        plain.setAttribute("name", "Bob2")
        assertEquals("Bob2", plain.getRawAttribute("name"))
        plain.setRelation("emptyBooks", emptyList<B6Book>())
        plain.setRelation("flag", "not-a-model")
        assertTrue(plain.push())
        val draft = B6Author()
        draft.setAttribute("extra", "x")
        assertEquals("x", draft.getRawAttribute("extra"))
        assertEquals("A", draft.getAttribute("a"))
    }

    @Test
    fun belongsToManyFirstOrCreateAndMorphToWhereHas() {
        val ada = B6Author.where("name", "Ada").first()!!
        val sci = ada.genres().firstOrCreate(mapOf("name" to "SciFi"), mapOf("name" to "SciFi"))
        assertTrue(sci.exists())
        assertEquals(sci.getKey(), ada.genres().firstOrCreate(mapOf("name" to "SciFi")).getKey())
        assertEquals(1, ada.genres().count())
        val fantasy = ada.genres().firstOrCreate(mapOf("name" to "Fantasy"))
        assertEquals("Fantasy", fantasy["name"])

        val book = B6Book.where("title", "A1-rev").first() ?: B6Book.where("title", "A1").first()!!
        val comment = B6Comment.create(
            mapOf(
                "body" to "hi",
                "commentable_type" to "books",
                "commentable_id" to book.getKey(),
            ),
        )
        assertEquals(1, B6Comment.whereHas("commentable").count())
        assertEquals(0, B6Comment.whereDoesntHave("commentable").where("id", comment.getKey()).count())

        MorphMap.clear()
        assertThrows(CadenceException::class.java) { B6Comment.whereHas("commentable").count() }

        Relation.morphMap(mapOf("books" to B6Book::class.java))
        assertThrows(CadenceException::class.java) {
            B6Comment.query().withCount("commentable").get()
        }
        assertEquals(
            1,
            B6Comment.whereHas("commentable") { q -> q.where("title", "A1") }.count(),
        )
        assertEquals(0, B6Comment.orWhereHas("commentable").where("body", "missing").count())
    }

    @Test
    fun mysqlAndSqliteSchemaGrammars() {
        assertTrue(SchemaGrammar.forProduct("MySQL") is MySqlSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("MariaDB") is MySqlSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("SQLite") is SqliteSchemaGrammar)
        assertSame(SchemaGrammar.MYSQL, SchemaGrammar.forProduct("mysql"))
        assertSame(SchemaGrammar.SQLITE, SchemaGrammar.forProduct("sqlite"))

        val mysql = SchemaGrammar.MYSQL
        val sqlite = SchemaGrammar.SQLITE
        val bp = Blueprint("t")
        bp.id()
        bp.string("name")
        bp.boolean("flag")
        val idType = mysql.type(bp.columns.first { it.name == "id" })
        assertTrue(idType.contains("AUTO_INCREMENT"))
        assertEquals("1", mysql.literal(true))
        assertEquals("0", mysql.literal(false))
        assertEquals("'x'", mysql.literal("x"))
        assertTrue(sqlite.type(bp.columns.first { it.name == "id" }).contains("AUTOINCREMENT"))
        assertEquals("INTEGER", sqlite.type(bp.columns.first { it.name == "flag" }))
        assertTrue(sqlite.compileDropIndex("t", "idx").contains("IF EXISTS"))
        val ddl = mysql.compileCreate(bp).first()
        assertTrue(ddl.contains("AUTO_INCREMENT"))
        assertNotNull(ddl)
        assertTrue(sqlite.compileCreate(bp).first().contains("AUTOINCREMENT"))
        assertTrue(SchemaGrammar.forProduct("MySQL", Grammar.NONE) is MySqlSchemaGrammar)
        assertTrue(SchemaGrammar.forProduct("SQLite", Grammar.NONE) is SqliteSchemaGrammar)
        assertEquals("TINYINT(1)", mysql.type(bp.columns.first { it.name == "flag" }))
        bp.timestamp("ts")
        assertEquals("TIMESTAMP", mysql.type(bp.columns.first { it.name == "ts" }))
    }

    @Test
    fun nativeUpsertOnH2() {
        Database.global().withConnection { connection ->
            Schema(connection).create("votes") {
                id()
                string("email").unique()
                string("name")
            }
        }
        val table = Database.global().table("votes")
        assertEquals(
            1,
            table.upsert(listOf(mapOf("email" to "a@x", "name" to "A")), listOf("email")),
        )
        assertEquals(
            1,
            table.upsert(listOf(mapOf("email" to "a@x", "name" to "B")), listOf("email"), listOf("name")),
        )
        assertEquals("B", table.where("email", "a@x").value("name"))
        assertEquals(
            0,
            table.upsert(listOf(mapOf("email" to "a@x", "name" to "C")), listOf("email"), emptyList()),
        )
        assertEquals("B", table.where("email", "a@x").value("name"))
        // Empty column map / mismatched keys fall back (native returns null).
        assertEquals(0, table.upsert(listOf(emptyMap()), listOf("email")))
        assertEquals(
            2,
            table.upsert(
                listOf(
                    mapOf("email" to "b@x", "name" to "B1"),
                    mapOf("email" to "c@x", "name" to "C1"),
                ),
                listOf("email"),
            ),
        )
        // No unique index on conflict target → native fails, portable path runs.
        assertEquals(
            1,
            Database.global().table("authors").upsert(
                listOf(mapOf("name" to "Eve", "display_name" to "eve")),
                listOf("name"),
            ),
        )
        assertTrue(
            NativeUpsert.rowsHaveUniformKeys(
                listOf(mapOf("email" to "a"), mapOf("email" to "b")),
            ),
        )
        assertFalse(
            NativeUpsert.rowsHaveUniformKeys(
                listOf(mapOf("email" to "a", "name" to "A"), mapOf("email" to "b")),
            ),
        )
        assertFalse(NativeUpsert.rowsHaveUniformKeys(emptyList()))

        // Oracle dialect → native compile null → portable path.
        val oracle = Database.global().table("votes")
        oracle.upsertProductOverride = "Oracle"
        assertEquals(
            1,
            oracle.upsert(listOf(mapOf("email" to "or@x", "name" to "Or")), listOf("email")),
        )
        // MySQL SQL against H2 → native execute fails → portable path.
        val mysqlForce = Database.global().table("votes")
        mysqlForce.upsertProductOverride = "MySQL"
        assertEquals(
            1,
            mysqlForce.upsert(listOf(mapOf("email" to "my@x", "name" to "My")), listOf("email")),
        )
        // Mismatched keys skip native.
        val mixed = Database.global().table("votes")
        assertEquals(
            1,
            mixed.upsert(
                listOf(
                    mapOf("email" to "mix@x", "name" to "Mix"),
                    mapOf("email" to "a@x"),
                ),
                listOf("email"),
            ),
        )

        val mysqlSql = NativeUpsert.compileSql(
            "MySQL",
            Grammar.MYSQL,
            "`votes`",
            listOf("email", "name"),
            listOf("email"),
            listOf("name"),
            1,
        )!!
        assertTrue(mysqlSql.contains("on duplicate key update"))
        val mysqlNoUpdate = NativeUpsert.compileSql(
            "MariaDB",
            Grammar.MYSQL,
            "`votes`",
            listOf("email", "name"),
            listOf("email"),
            emptyList(),
            2,
        )!!
        assertTrue(mysqlNoUpdate.contains("on duplicate key update"))
        assertNull(
            NativeUpsert.compileSql(
                "Oracle",
                Grammar.ANSI,
                "votes",
                listOf("email"),
                listOf("email"),
                listOf("email"),
                1,
            ),
        )
        assertNotNull(
            NativeUpsert.compileSql(
                "PostgreSQL",
                Grammar.ANSI,
                "votes",
                listOf("email", "name"),
                listOf("email"),
                listOf("name"),
                1,
            ),
        )
        // Cover empty-updateCols postgres/h2 arm.
        assertTrue(
            NativeUpsert.compileSql(
                "H2",
                Grammar.ANSI,
                "votes",
                listOf("email", "name"),
                listOf("email"),
                emptyList(),
                1,
            )!!.contains("do nothing"),
        )
        assertThrows(CadenceException::class.java) {
            Database.global().connection().query().sole()
        }
        // Pluck fallbacks when key/value columns are absent from the row map.
        val plucked = Database.global().table("authors").select("name").pluck("name", "id")
        assertTrue(plucked.isNotEmpty())
        val pluckedVal = Database.global().table("authors").select("id").pluck("name", "id")
        assertTrue(pluckedVal.isNotEmpty())
        // Union arm with default select list (*).
        assertTrue(
            Database.global().table("authors").where("name", "Ada")
                .union(Database.global().table("authors").where("name", "Bob"))
                .toSql().contains("union"),
        )
        Cadence.runWithoutTimestamps {
            B6Author.where("name", "Bob").first()!!.also {
                it.setAttribute("name", "Bobby")
                it.save()
            }
            B6Book.create(mapOf("title" to "Quiet", "author_id" to 1, "votes" to 0))
        }
        // studly() empty segments from repeated separators
        assertEquals("Anon", B6Author().getAttribute("display__name"))
    }
}
