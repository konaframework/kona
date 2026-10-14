package dev.kona.extract

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QueryBuilderTest {
    private lateinit var database: Database
    private lateinit var db: DatabaseConnection

    @BeforeEach
    fun setUp() {
        database = TestDb.create()
        db = database.connection()
        db.table("users").insert(
            listOf(
                mapOf("name" to "Ada", "email" to "ada@x.io", "age" to 36),
                mapOf("name" to "Grace", "email" to null, "age" to 45),
                mapOf("name" to "Linus", "age" to 28),
                mapOf("name" to "Ada", "email" to "ada2@x.io", "age" to 20),
            ),
        )
        db.table("posts").insert(
            listOf(
                mapOf("user_id" to 1, "title" to "Engines", "votes" to 10),
                mapOf("user_id" to 1, "title" to "Notes", "votes" to 5),
                mapOf("user_id" to 2, "title" to "Cobol", "votes" to 7),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
        Database.clearGlobal()
    }

    private fun names(query: QueryBuilder) = query.pluck("name")

    @Test
    fun selectsAndDebugSql() {
        val query = db.table("users").select("id", "name as n").addSelect("users.email").distinct()
        assertEquals("select distinct \"id\", \"name\" as \"n\", \"users\".\"email\" from \"users\"", query.toSql())
        assertEquals(4, query.get().size)
        assertEquals("Ada", query.copy().orderBy("id").first()!!["n"])

        val raw = db.table("users").selectRaw("count(*) as c").selectRaw("cast(? as varchar) as tag", listOf("t")).groupBy("name").having("name", "=", "Ada")
        assertEquals("select count(*) as c, cast(? as varchar) as tag from \"users\" group by \"name\" having \"name\" = ?", raw.toSql())
        assertEquals(listOf<Any?>("t", "Ada"), raw.getBindings())
        val row = raw.get().single()
        assertEquals(2L, row["c"])
        assertEquals("t", row["tag"])

        // select() replaces columns and select bindings.
        val replaced = db.table("users").selectRaw("? as x", listOf(1)).select("name")
        assertEquals("select \"name\" from \"users\"", replaced.toSql())
        assertTrue(replaced.getBindings().isEmpty())
        assertEquals("select * from \"users\"", db.query().table("users").toSql())

        val none = assertThrows<ExtractException> { db.query().get() }
        assertTrue(none.message!!.contains("No table"))
    }

    @Test
    fun whereClausesBindValues() {
        val query = db.table("users")
            .where("name", "Ada")
            .where("age", ">", 30)
            .orWhere("name", "Grace")
            .orWhere("age", "<=", 20)
        assertEquals(
            "select * from \"users\" where \"name\" = ? and \"age\" > ? or \"name\" = ? or \"age\" <= ?",
            query.toSql(),
        )
        assertEquals(listOf<Any?>("Ada", 30, "Grace", 20), query.getBindings())
        assertEquals(listOf<Any?>("Ada", "Grace", "Ada"), names(query.orderBy("id")))

        val injection = db.table("users").where("name", "' or 1=1 --")
        assertEquals(0L, injection.count())

        val grouped = db.table("users")
            .where("age", ">", 21)
            .where { it.where("name", "Ada").orWhere("name", "Linus") }
            .orWhere { it.whereNull("email").where("age", 45) }
            .where { }
        assertEquals(
            "select * from \"users\" where \"age\" > ? and (\"name\" = ? or \"name\" = ?) or (\"email\" is null and \"age\" = ?)",
            grouped.toSql(),
        )
        assertEquals(listOf<Any?>("Ada", "Grace", "Linus"), names(grouped.orderBy("id")))

        val illegal = assertThrows<IllegalArgumentException> { db.table("users").where("a", "; drop", 1) }
        assertTrue(illegal.message!!.contains("Illegal operator"))
        assertEquals(1L, db.table("users").where("name", " LIKE ", "Gr%").count())
        assertEquals(1L, db.table("users").where("name", "not like", "A%").where("name", "<>", "Grace").count())
    }

    @Test
    fun nullAndInAndBetweenAndRaw() {
        assertEquals("select * from \"users\" where \"email\" is null", db.table("users").where("email", null).toSql())
        assertEquals("select * from \"users\" where \"email\" is not null", db.table("users").where("email", "!=", null).toSql())
        assertEquals("select * from \"users\" where \"email\" is not null", db.table("users").where("email", "<>", null).toSql())
        assertEquals(listOf<Any?>(null), db.table("users").where("age", ">", null).getBindings())
        assertEquals(2L, db.table("users").whereNull("email").count())
        assertEquals(2L, db.table("users").whereNotNull("email").count())
        assertEquals(3L, db.table("users").whereNull("email").orWhereNull("age").orWhere("age", 36).count())
        assertEquals(3L, db.table("users").where("age", 45).orWhereNotNull("email").count())
        assertEquals(2L, db.table("users").where("email", "=", null).count())
        assertEquals(2L, db.table("users").where("email", "!=", null).count())

        val inQuery = db.table("users").whereIn("name", listOf("Ada", "Linus"))
        assertEquals("select * from \"users\" where \"name\" in (?, ?)", inQuery.toSql())
        assertEquals(3L, inQuery.count())
        assertEquals(1L, db.table("users").whereNotIn("name", listOf("Ada", "Linus")).count())
        assertEquals(4L, db.table("users").whereIn("name", listOf("Grace")).orWhereIn("name", listOf("Ada", "Linus")).count())
        assertEquals(2L, db.table("users").whereIn("name", listOf("Grace")).orWhereNotIn("name", listOf("Ada", "Grace")).count())
        assertEquals("select * from \"users\" where 0 = 1", db.table("users").whereIn("name", emptyList<String>()).toSql())
        assertEquals(0L, db.table("users").whereIn("name", emptyList<String>()).count())
        assertEquals(4L, db.table("users").whereNotIn("name", emptySet<String>()).count())
        assertEquals("select * from \"users\" where 1 = 1", db.table("users").whereNotIn("name", emptyList<String>()).toSql())

        assertEquals(2L, db.table("users").whereBetween("age", 28, 36).count())
        assertEquals(1L, db.table("users").whereRaw("age > 40").count())
        assertEquals(1L, db.table("users").whereRaw("age > ?", listOf(40)).count())
        assertEquals(2L, db.table("users").whereRaw("age > ?", listOf(40)).orWhereRaw("age < 21").count())
        assertEquals(3L, db.table("users").whereRaw("age > ?", listOf(40)).orWhereRaw("age < ?", listOf(30)).count())
    }

    @Test
    fun joinsOrderingAndPaging() {
        val joined = db.table("users")
            .join("posts", "users.id", "posts.user_id")
            .select("users.name", "posts.title")
            .orderBy("posts.votes", "DESC")
        assertEquals(
            "select \"users\".\"name\", \"posts\".\"title\" from \"users\" inner join \"posts\" on \"users\".\"id\" = \"posts\".\"user_id\" order by \"posts\".\"votes\" desc",
            joined.toSql(),
        )
        assertEquals(listOf<Any?>("Engines", "Cobol", "Notes"), joined.pluck("title"))
        assertEquals(3L, db.table("users").join("posts", "users.id", ">=", "posts.user_id").where("users.id", 2).count())
        assertEquals(5L, db.table("users").leftJoin("posts", "users.id", "posts.user_id").count())
        assertEquals(2L, db.table("users").leftJoin("posts", "users.id", "=", "posts.user_id").whereNull("posts.id").count())

        assertThrows<IllegalArgumentException> { db.table("users").orderBy("name", "sideways") }
        assertEquals(listOf<Any?>("Linus", "Grace", "Ada", "Ada"), names(db.table("users").orderByDesc("name")))
        assertEquals(listOf<Any?>(20, 28, 36, 45), db.table("users").orderBy("age").pluck("age"))
        assertEquals("select * from \"users\" order by \"created_at\" desc", db.table("users").latest().toSql())
        assertEquals("select * from \"users\" order by \"created_at\" asc", db.table("users").oldest().toSql())
        assertEquals("select * from \"users\" order by \"age\" desc, \"id\" asc", db.table("users").latest("age").oldest("id").toSql())

        val paged = db.table("users").orderBy("id").limit(2).offset(1)
        assertEquals("select * from \"users\" order by \"id\" asc limit 2 offset 1", paged.toSql())
        assertEquals(listOf<Any?>("Grace", "Linus"), names(paged))
        assertEquals(listOf<Any?>("Linus", "Ada"), names(db.table("users").orderBy("id").skip(2)))
        assertEquals(listOf<Any?>("Ada"), names(db.table("users").orderBy("id").take(1)))
        assertEquals("select * from \"users\" offset 0", db.table("users").limit(5).limit(-1).offset(-3).toSql())
        assertEquals(listOf<Any?>("Linus", "Ada"), names(db.table("users").orderBy("id").forPage(2, 2)))
    }

    @Test
    fun readsAndAggregates() {
        val users = db.table("users")
        assertEquals(4, users.get().size)
        assertEquals("Ada", users.copy().orderBy("id").first()!!["name"])
        assertNull(db.table("users").where("name", "Nobody").first())
        assertEquals("Grace", db.table("users").find(2)!!["name"])
        assertEquals(3L, db.table("users").find("Linus", "name")!!["id"])
        assertEquals("Grace", db.table("users").where("age", 45).value("name"))
        assertNull(db.table("users").where("age", 99).value("name"))

        assertEquals(4L, users.count())
        assertEquals(3L, db.table("users").select("name").groupBy("name").count())
        assertEquals(3L, db.table("users").select("name").distinct().count())
        assertEquals(45, users.max("age"))
        assertEquals(20, users.min("age"))
        assertEquals(129L, (users.sum("age") as Number).toLong())
        assertEquals(32, (users.avg("age") as Number).toInt())
        assertEquals(2L, db.table("users").where("name", "Ada").orderBy("id").limit(1).offset(1).count())

        assertTrue(users.exists())
        assertFalse(users.doesntExist())
        assertFalse(db.table("users").where("age", 99).exists())
        assertTrue(db.table("users").where("age", 99).doesntExist())
    }

    @Test
    fun paginatesWithTotals() {
        val page = db.table("users").orderBy("id").paginate(3, 2)
        assertEquals(1, page.data.size)
        assertEquals("Ada", page.data.single()["name"])
        assertEquals(4L, page.total)
        assertEquals(3, page.perPage)
        assertEquals(2, page.currentPage)
        assertEquals(2, page.lastPage)
        assertFalse(page.hasMorePages())

        val first = db.table("users").paginate()
        assertEquals(15, first.perPage)
        assertEquals(1, first.lastPage)
        assertEquals(4, first.data.size)
        assertEquals(1, db.table("users").paginate(2, 0).currentPage)
        assertThrows<IllegalArgumentException> { db.table("users").paginate(0) }
    }

    @Test
    fun writes() {
        assertTrue(db.table("tags").insert(mapOf("code" to "a", "label" to "A")))
        assertTrue(db.table("tags").insert(listOf(mapOf("code" to "b", "label" to "B"), mapOf("code" to "c"))))
        assertNull(db.table("tags").find("c", "code")!!["label"])
        assertTrue(db.table("tags").insert(emptyList()))
        assertEquals(3L, db.table("tags").count())

        assertEquals(5L, db.table("users").insertGetId(mapOf("name" to "New")))
        assertEquals(6L, db.table("users").insertGetId(emptyMap()))
        assertTrue(db.table("users").insert(listOf(emptyMap())))
        assertEquals(7L, db.table("users").count())

        assertEquals(0, db.table("users").update(emptyMap()))
        assertEquals(2, db.table("users").where("name", "Ada").update(mapOf("age" to 1, "email" to null)))
        assertEquals(listOf<Any?>(1, 1), db.table("users").where("name", "Ada").pluck("age"))
        assertEquals(1, db.table("posts").where("id", 1).increment("votes"))
        assertEquals(1, db.table("posts").where("id", 1).increment("votes", 5))
        assertEquals(16, db.table("posts").where("id", 1).value("votes"))
        assertEquals(3, db.table("posts").decrement("votes"))
        assertEquals(1, db.table("posts").where("id", 2).decrement("votes", 4))
        assertEquals(listOf<Any?>(15, 0, 6), db.table("posts").orderBy("id").pluck("votes"))

        assertEquals(1, db.table("posts").where("id", 3).delete())
        assertEquals(2, db.table("posts").delete())
        db.table("tags").truncate()
        assertEquals(0L, db.table("tags").count())
    }

    @Test
    fun copyIsIndependentAndScopesGroupWheres() {
        val base = db.table("users").where("name", "Ada")
        val copy = base.copy().where("age", 20)
        assertEquals(2L, base.count())
        assertEquals(1L, copy.count())

        val scoped = db.table("users").where("name", "Ada").orWhere("name", "Grace").groupWheres().whereNotNull("email")
        assertEquals("select * from \"users\" where (\"name\" = ? or \"name\" = ?) and \"email\" is not null", scoped.toSql())
        assertEquals(2L, scoped.count())
        assertEquals("select * from \"users\"", db.table("users").groupWheres().toSql())
    }

    @Test
    fun unquotedGrammar() {
        val plain = DatabaseConnection("plain", database.connection().dataSource, Grammar.NONE)
        val query = plain.table("users").where("users.name", "Ada").select("name")
        assertEquals("select name from users where users.name = ?", query.toSql())
        assertEquals(2, query.get().size)
    }
}
