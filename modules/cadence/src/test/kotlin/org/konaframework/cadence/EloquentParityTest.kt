package org.konaframework.cadence

import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EloquentParityTest {
    private lateinit var database: Database
    private lateinit var db: DatabaseConnection

    @BeforeEach
    fun setUp() {
        database = TestDb.create()
        db = database.connection()
        db.statement("create unique index users_email_unique on users(email)")
    }

    @AfterEach
    fun tearDown() {
        database.close()
        Database.clearGlobal()
    }

    @Test
    fun queryBuilderBetweenColumnWhenJoinChunkUpsert() {
        db.table("users").insert(
            listOf(
                mapOf("name" to "Ada", "email" to "ada@x.io", "age" to 36),
                mapOf("name" to "Grace", "email" to "grace@x.io", "age" to 45),
                mapOf("name" to "Linus", "email" to "linus@x.io", "age" to 28),
            ),
        )
        db.table("posts").insert(
            listOf(
                mapOf("user_id" to 1, "title" to "A", "votes" to 10),
                mapOf("user_id" to 2, "title" to "B", "votes" to 3),
            ),
        )

        assertEquals(1L, db.table("users").where("age", 20).orWhereBetween("age", 30, 40).count())
        assertEquals(2L, db.table("users").whereNotBetween("age", 30, 40).count())
        assertEquals(3L, db.table("users").where("age", 99).orWhereNotBetween("age", 100, 200).count())
        assertEquals(
            "select * from \"users\" where \"age\" not between ? and ?",
            db.table("users").whereNotBetween("age", 1, 2).toSql(),
        )

        assertEquals(
            "select * from \"users\" where \"name\" = \"email\"",
            db.table("users").whereColumn("name", "email").toSql(),
        )
        assertEquals(0L, db.table("users").whereColumn("name", "email").count())
        assertEquals(
            2L,
            db.table("posts").join("users", "posts.user_id", "users.id")
                .whereColumn("posts.user_id", "=", "users.id")
                .orWhereColumn("posts.votes", ">", "users.age")
                .count(),
        )

        val whenApplied = db.table("users").`when`(true) { it.where("name", "Ada") }.`when`(false) { it.where("name", "Nope") }
        assertEquals(1L, whenApplied.count())
        assertEquals(3L, db.table("users").unless(true) { it.where("name", "Ada") }.count())
        assertEquals(1L, db.table("users").unless(false) { it.where("name", "Ada") }.count())

        assertTrue(db.table("users").hasSelectColumns().not())
        assertTrue(db.table("users").select("id").hasSelectColumns())

        val right = db.table("posts").rightJoin("users", "posts.user_id", "users.id").select("users.name", "posts.title")
        assertTrue(right.toSql().contains("right join"))
        assertEquals(3, right.get().size)
        assertEquals(3, db.table("posts").rightJoin("users", "posts.user_id", "=", "users.id").get().size)

        val pages = mutableListOf<List<String>>()
        db.table("users").orderBy("id").chunk(2) { rows ->
            pages += rows.map { it["name"] as String }
        }
        assertEquals(listOf(listOf("Ada", "Grace"), listOf("Linus")), pages)
        assertThrows<IllegalArgumentException> { db.table("users").chunk(0) { } }

        assertTrue(db.table("users").updateOrInsert(mapOf("email" to "ada@x.io"), mapOf("age" to 37)))
        assertEquals(37, db.table("users").where("email", "ada@x.io").value("age"))
        assertTrue(db.table("users").updateOrInsert(mapOf("email" to "new@x.io"), mapOf("name" to "New", "age" to 1)))
        assertEquals("New", db.table("users").where("email", "new@x.io").value("name"))
        assertTrue(db.table("users").updateOrInsert(mapOf("email" to "new@x.io")))
        assertTrue(db.table("users").updateOrInsert(mapOf("email" to "solo@x.io", "name" to "Solo", "age" to 3)))

        assertEquals(
            2,
            db.table("users").upsert(
                listOf(
                    mapOf("email" to "ada@x.io", "name" to "Ada2", "age" to 40),
                    mapOf("email" to "fresh@x.io", "name" to "Fresh", "age" to 22),
                ),
                listOf("email"),
            ),
        )
        assertEquals("Ada2", db.table("users").where("email", "ada@x.io").value("name"))
        assertEquals("Fresh", db.table("users").where("email", "fresh@x.io").value("name"))
        assertEquals(
            0,
            db.table("users").upsert(
                listOf(mapOf("email" to "ada@x.io", "name" to "Ignored")),
                listOf("email"),
                emptyList(),
            ),
        )
        assertEquals(0, db.table("users").upsert(emptyList(), listOf("email")))
        assertEquals(0, db.table("users").upsert(emptyList(), listOf("email"), listOf("name")))
        assertThrows<IllegalArgumentException> {
            db.table("users").upsert(listOf(mapOf("email" to "x")), emptyList())
        }

        val qbChunks = mutableListOf<Int>()
        while (db.table("users").count() % 2L != 0L) {
            db.table("users").insert(mapOf("name" to "P", "email" to "p${db.table("users").count()}@z.io", "age" to 1))
        }
        db.table("users").orderBy("id").chunk(2) { qbChunks += it.size }
        assertTrue(qbChunks.all { it == 2 })
        db.table("users").insert(mapOf("name" to "Q", "email" to "q@z.io", "age" to 2))
        val qbOdd = mutableListOf<Int>()
        db.table("users").orderBy("id").chunk(2) { qbOdd += it.size }
        assertTrue(qbOdd.last() < 2)

        assertEquals(
            1,
            db.table("users").insertOrIgnore(
                listOf(
                    mapOf("email" to "ada@x.io", "name" to "Dup"),
                    mapOf("email" to "ignore@x.io", "name" to "Ok", "age" to 9),
                ),
                listOf("email"),
            ),
        )
        assertEquals("Ada2", db.table("users").where("email", "ada@x.io").value("name"))
        assertEquals("Ok", db.table("users").where("email", "ignore@x.io").value("name"))
        assertEquals(0, db.table("users").insertOrIgnore(emptyList(), listOf("email")))
        assertThrows<IllegalArgumentException> {
            db.table("users").insertOrIgnore(listOf(mapOf("email" to "z")), emptyList())
        }
    }

    @Test
    fun modelBuilderFirstOrAndAggregatesAndChunk() {
        User.create(mapOf("name" to "Ada", "email" to "a@x", "age" to 36))
        User.create(mapOf("name" to "Grace", "email" to "g@x", "age" to 45))

        val neu = User.query().firstOrNew(mapOf("email" to "missing@x"))
        assertFalse(neu.exists())
        assertEquals("missing@x", neu["email"])
        assertEquals("Ada", User.firstOrNew(mapOf("email" to "a@x"))["name"])
        assertTrue(User.query().firstOrNew(mapOf("email" to "a@x")).exists())

        val created = User.firstOrCreate(mapOf("email" to "c@x"), mapOf("name" to "Created", "age" to 10))
        assertTrue(created.exists())
        assertEquals("Created", created["name"])
        assertEquals(created.getKey(), User.firstOrCreate(mapOf("email" to "c@x"), mapOf("name" to "Nope")).getKey())
        val viaDefault = User.firstOrCreate(mapOf("email" to "d@x", "name" to "DefaultOnly", "age" to 13))
        assertEquals("DefaultOnly", viaDefault["name"])

        val updated = User.updateOrCreate(mapOf("email" to "c@x"), mapOf("name" to "Updated", "age" to 11))
        assertEquals("Updated", updated["name"])
        val brand = User.updateOrCreate(mapOf("email" to "b@x"), mapOf("name" to "Brand", "age" to 12))
        assertEquals("Brand", brand["name"])
        val updateDefault = User.updateOrCreate(mapOf("email" to "e@x", "name" to "E", "age" to 14))
        assertEquals("E", updateDefault["name"])

        assertEquals(45, User.query().max("age"))
        assertEquals(11, User.query().min("age"))
        assertTrue((User.query().sum("age") as Number).toLong() >= 104L)
        assertTrue((User.query().avg("age") as Number).toDouble() > 20)

        assertEquals(User.query().count(), User.query().where("age", -1).orWhereNotBetween("age", 1000, 2000).count())
        assertTrue(User.query().whereNotBetween("age", 20, 40).count() >= 3L)
        assertEquals(1L, User.query().where("name", "Grace").orWhereBetween("age", 40, 50).where("name", "Grace").count())
        assertEquals(0L, User.query().whereColumn("name", "email").count())
        assertEquals(1L, User.query().whereColumn("name", "!=", "email").where("name", "Ada").count())
        assertEquals(1L, User.query().where("name", "Ada").orWhereColumn("name", "email").count())
        assertEquals(1L, User.query().where("name", "Ada").orWhereColumn("id", ">", "age").count())
        assertTrue(User.query().rightJoin("posts", "users.id", "=", "posts.user_id").toSql().contains("right join"))

        val scoped = User.query().scope { it.where("name", "Ada") }.`when`(true) { it.where("age", 36) }.unless(false) { it.whereNotNull("email") }
        assertEquals(1, scoped.get().size)
        assertEquals(User.count(), User.query().`when`(false) { it.where("name", "Nope") }.count())

        // Even total: final empty page trips rows.isEmpty()
        while (User.count() % 2L != 0L) {
            User.create(mapOf("name" to "Pad", "email" to "pad${User.count()}@x", "age" to 16))
        }
        val evenChunks = mutableListOf<Int>()
        User.query().orderBy("id").chunk(2) { evenChunks += it.size }
        assertTrue(evenChunks.isNotEmpty())
        assertTrue(evenChunks.all { it == 2 })

        // Odd total: final short page trips models.size < count
        User.create(mapOf("name" to "Odd", "email" to "odd@x", "age" to 15))
        val oddChunks = mutableListOf<Int>()
        User.query().orderBy("id").chunk(2) { oddChunks += it.size }
        assertTrue(oddChunks.last() < 2)
        assertThrows<IllegalArgumentException> { User.query().chunk(0) { } }

        val collection = User.query().orderBy("id").getCollection()
        assertTrue(collection.count() >= 4)
        assertEquals("Ada", collection.find(1)!!["name"])
        assertNull(collection.find(999))

        assertTrue(User.query().updateOrInsert(mapOf("email" to "a@x"), mapOf("age" to 99)))
        assertTrue(User.query().updateOrInsert(mapOf("email" to "only-attrs@x", "name" to "Only", "age" to 2)))
        assertEquals(1, User.query().upsert(listOf(mapOf("email" to "g@x", "name" to "G2", "age" to 46)), listOf("email")))
        assertEquals(
            0,
            User.query().upsert(
                listOf(mapOf("email" to "g@x", "name" to "G3", "age" to 47)),
                listOf("email"),
                emptyList(),
            ),
        )
        assertEquals(1, User.query().insertOrIgnore(listOf(mapOf("email" to "z@x", "name" to "Z", "age" to 1)), listOf("email")))
        assertEquals(0, User.query().insertOrIgnore(listOf(mapOf("email" to "z@x", "name" to "Z2")), listOf("email")))
    }

    @Test
    fun globalScopesApplyAndCanBeRemoved() {
        class AdultScope : Scope {
            override fun apply(builder: ModelBuilder<*>) {
                builder.where("age", ">=", 18)
            }
        }
        try {
            User.addGlobalScope("named") { it.where("name", "Ada") }
            User.addGlobalScope(AdultScope())
            assertTrue(User.hasGlobalScope("named"))
            assertNotNull(User.getGlobalScope("named"))
            assertTrue(User.getGlobalScopes().containsKey(AdultScope::class.java.name))

            db.table("users").insert(
                listOf(
                    mapOf("name" to "Ada", "email" to "a@g", "age" to 36),
                    mapOf("name" to "Grace", "email" to "g@g", "age" to 45),
                    mapOf("name" to "Kid", "email" to "k@g", "age" to 10),
                ),
            )
            assertEquals(1L, User.query().count())
            assertEquals(2L, User.withoutGlobalScope("named").count())
            assertEquals(1L, User.withoutGlobalScope(AdultScope::class.java.name).count())
            assertEquals(3L, User.withoutGlobalScopes().count())
            assertEquals(1L, User.withoutGlobalScopes(listOf(AdultScope::class.java.name)).count())
            assertEquals(1L, User.withoutGlobalScopesExcept(listOf("named")).count())
            assertEquals(2L, User.query().withoutGlobalScopes(listOf("named")).count())
            assertTrue(User.query().toSql().contains("age"))
            User.removeGlobalScope("named")
            assertFalse(User.hasGlobalScope("named"))
            assertEquals(2L, User.query().count())
        } finally {
            GlobalScopes.clear(User::class.java)
        }
        // registry miss paths
        assertFalse(GlobalScopes.has(Plain::class.java, "x"))
        assertNull(GlobalScopes.get(Plain::class.java, "x"))
        assertTrue(GlobalScopes.all(Plain::class.java).isEmpty())
        GlobalScopes.remove(Plain::class.java, "x")
        GlobalScopes.clear(Plain::class.java)
        assertNull(User.getGlobalScope("missing"))
        assertFalse(User.hasGlobalScope("missing"))
    }

    @Test
    fun whereDateHavingAndModelBuilderMirrors() {
        // Use modern civil dates — pre-1900 timestamps can shift under H2 cast(as time) TZ rules.
        val adaBorn = Timestamp.valueOf(LocalDateTime.of(1990, 12, 10, 9, 30, 0))
        val graceBorn = Timestamp.valueOf(LocalDateTime.of(2001, 12, 9, 14, 15, 0))
        db.table("users").insert(
            listOf(
                mapOf("name" to "Ada", "email" to "a@d", "age" to 36, "born_at" to adaBorn),
                mapOf("name" to "Grace", "email" to "g@d", "age" to 45, "born_at" to graceBorn),
            ),
        )
        db.table("posts").insert(
            listOf(
                mapOf("user_id" to 1, "title" to "A1", "votes" to 10),
                mapOf("user_id" to 1, "title" to "A2", "votes" to 5),
                mapOf("user_id" to 2, "title" to "G1", "votes" to 20),
            ),
        )

        assertTrue(db.table("users").whereDate("born_at", Date.valueOf(LocalDate.of(1990, 12, 10))).toSql().contains("cast("))
        assertEquals(1L, db.table("users").whereDate("born_at", Date.valueOf(LocalDate.of(1990, 12, 10))).count())
        assertEquals(1L, db.table("users").whereDate("born_at", ">", Date.valueOf(LocalDate.of(1995, 1, 1))).count())
        assertEquals(2L, db.table("users").where("name", "x").orWhereDate("born_at", Date.valueOf(LocalDate.of(1990, 12, 10))).orWhereDate("born_at", "=", Date.valueOf(LocalDate.of(2001, 12, 9))).count())

        assertEquals(1L, db.table("users").whereYear("born_at", 1990).count())
        assertEquals(1L, db.table("users").whereYear("born_at", ">=", 2000).count())
        assertEquals(2L, db.table("users").where("id", 0).orWhereYear("born_at", 1990).orWhereYear("born_at", "=", 2001).count())

        assertEquals(2L, db.table("users").whereMonth("born_at", 12).count())
        assertEquals(0L, db.table("users").whereMonth("born_at", "<", 6).count())
        assertEquals(2L, db.table("users").where("id", 0).orWhereMonth("born_at", 12).orWhereMonth("born_at", "=", 12).count())

        assertEquals(1L, db.table("users").whereDay("born_at", 10).count())
        assertEquals(1L, db.table("users").whereDay("born_at", "!=", 10).count())
        assertEquals(2L, db.table("users").where("id", 0).orWhereDay("born_at", 10).orWhereDay("born_at", "=", 9).count())

        assertEquals(1L, db.table("users").whereTime("born_at", Time.valueOf(LocalTime.of(9, 30, 0))).count())
        assertEquals(1L, db.table("users").whereTime("born_at", ">", Time.valueOf(LocalTime.of(12, 0, 0))).count())
        assertEquals(2L, db.table("users").where("id", 0).orWhereTime("born_at", Time.valueOf(LocalTime.of(9, 30, 0))).orWhereTime("born_at", "=", Time.valueOf(LocalTime.of(14, 15, 0))).count())

        val groupedSql = db.table("posts").select("user_id").selectRaw("sum(votes) as total").groupBy("user_id")
            .having("total", ">", 10).orHaving("total", "=", 15).havingRaw("sum(votes) >= ?", listOf(15))
            .orHavingRaw("sum(votes) = ?", listOf(15)).orderBy("user_id").toSql()
        assertTrue(groupedSql.contains("group by"))
        assertTrue(groupedSql.contains("having"))
        assertEquals(
            1,
            db.table("posts").select("user_id").selectRaw("sum(votes) as total").groupBy("user_id")
                .having("total", ">", 15).get().size,
        )
        assertTrue(db.table("posts").selectRaw("count(*) as c", emptyList()).toSql().contains("count(*)"))
        assertTrue(db.table("users").distinct().select("name").toSql().startsWith("select distinct"))
        // default @JvmOverloads bindings branches
        assertTrue(db.table("posts").groupBy("user_id").havingRaw("count(*) >= 1").orHavingRaw("count(*) >= 1").toSql().contains("having"))

        assertEquals(1L, User.query().whereDate("born_at", Date.valueOf(LocalDate.of(1990, 12, 10))).count())
        assertEquals(1L, User.query().whereYear("born_at", 2001).orWhereMonth("born_at", 12).where("name", "Grace").count())
        assertEquals(1, User.query().whereDay("born_at", 10).orWhereDay("born_at", 9).where("name", "Ada").get().size)
        assertEquals(1L, User.query().whereTime("born_at", Time.valueOf(LocalTime.of(14, 15, 0))).count())
        assertEquals(1L, User.query().orWhereDate("born_at", Date.valueOf(LocalDate.of(1990, 12, 10))).where("name", "Ada").count())
        assertEquals(1L, User.query().orWhereYear("born_at", 1990).where("name", "Ada").count())
        assertEquals(1L, User.query().orWhereMonth("born_at", 12).where("name", "Ada").count())
        assertEquals(1L, User.query().orWhereTime("born_at", Time.valueOf(LocalTime.of(9, 30))).where("name", "Ada").count())
        assertEquals(
            1L,
            User.query().where("name", "x").orWhereRaw("age = ?", listOf(36)).count(),
        )
        assertTrue(User.query().addSelect("name").selectRaw("age as years").selectRaw("name as label", emptyList()).distinct().toSql().contains("distinct"))
        assertTrue(
            User.query().groupBy("name").having("age", ">", 0).orHaving("age", "=", 36)
                .havingRaw("count(*) >= ?", listOf(1)).orHavingRaw("count(*) = ?", listOf(1)).toSql().contains("having"),
        )
        assertTrue(User.query().where("name", "x").orWhereRaw("age > 0").groupBy("name").havingRaw("count(*) >= 1").orHavingRaw("count(*) >= 1").toSql().contains("having"))
        assertEquals(1L, User.query().whereDate("born_at", "=", Date.valueOf(LocalDate.of(1990, 12, 10))).count())
        assertEquals(1L, User.query().orWhereDate("born_at", "=", Date.valueOf(LocalDate.of(2001, 12, 9))).where("name", "Grace").count())
        assertEquals(1L, User.query().whereDay("born_at", "=", 10).count())
        assertEquals(1L, User.query().orWhereDay("born_at", "=", 9).where("name", "Grace").count())
        assertEquals(2L, User.query().whereMonth("born_at", 12).count())
        assertEquals(2L, User.query().whereMonth("born_at", "=", 12).count())
        assertEquals(2L, User.query().orWhereMonth("born_at", "=", 12).count())
        assertEquals(1L, User.query().whereYear("born_at", "=", 1990).count())
        assertEquals(1L, User.query().orWhereYear("born_at", "=", 2001).where("name", "Grace").count())
        assertEquals(1L, User.query().whereTime("born_at", "=", Time.valueOf(LocalTime.of(9, 30))).count())
        assertEquals(1L, User.query().orWhereTime("born_at", "=", Time.valueOf(LocalTime.of(14, 15))).where("name", "Grace").count())
    }

    @Test
    fun locksTapCursorLazyFindOrNewIncrement() {
        db.table("users").insert(
            listOf(
                mapOf("name" to "Ada", "email" to "a@x", "age" to 36),
                mapOf("name" to "Grace", "email" to "g@x", "age" to 45),
                mapOf("name" to "Linus", "email" to "l@x", "age" to 28),
            ),
        )

        assertTrue(db.table("users").where("name", "Ada").lockForUpdate().toSql().endsWith(" for update"))
        assertTrue(db.table("users").sharedLock().toSql().endsWith(" for share"))
        assertFalse(db.table("users").lockForUpdate().withoutLock().toSql().contains(" for "))
        assertEquals(1L, db.table("users").lockForUpdate().where("name", "Ada").count())
        assertTrue(db.table("users").lockForUpdate().copy().toSql().contains(" for update"))

        val tapped = mutableListOf<String>()
        assertEquals(1L, db.table("users").tap { tapped += "qb" }.where("name", "Ada").count())
        assertEquals(listOf("qb"), tapped)

        assertEquals(
            listOf("Ada", "Grace", "Linus"),
            db.table("users").orderBy("id").cursor().map { it["name"] as String }.toList(),
        )
        assertEquals(
            listOf("Ada", "Grace", "Linus"),
            db.table("users").orderBy("id").lazy(2).map { it["name"] as String }.toList(),
        )
        assertEquals(3, db.table("users").orderBy("id").lazy().take(3).count())
        // Exact page size then empty page (covers the empty-batch break after full pages).
        assertEquals(3, db.table("users").orderBy("id").lazy(1).count())
        assertEquals(
            listOf("Ada", "Grace", "Linus"),
            db.table("users").lazyById(2).map { it["name"] as String }.toList(),
        )
        assertEquals(3, db.table("users").lazyById().take(3).count())
        assertEquals(3, db.table("users").lazyById(1).count())
        val byId = mutableListOf<List<String>>()
        db.table("users").chunkById(2) { rows -> byId += rows.map { it["name"] as String } }
        assertEquals(listOf(listOf("Ada", "Grace"), listOf("Linus")), byId)
        db.table("users").chunkById(2, "id") { rows -> byId += rows.map { it["name"] as String } }
        assertThrows<IllegalArgumentException> { db.table("users").lazy(0).toList() }
        assertThrows<IllegalArgumentException> { db.table("users").chunkById(0) { _ -> } }
        assertThrows<IllegalArgumentException> { db.table("users").lazyById(0).toList() }
        assertTrue(db.table("users").where("id", -1).lazy(2).toList().isEmpty())
        assertTrue(db.table("users").where("id", -1).lazyById(2).toList().isEmpty())
        val emptyChunks = mutableListOf<Int>()
        db.table("users").where("id", -1).chunkById(2) { emptyChunks += it.size }
        assertTrue(emptyChunks.isEmpty())
        assertThrows<CadenceException> {
            db.table("users").select("name").chunkById(1, "id") { }
        }
        assertThrows<CadenceException> {
            db.table("users").select("name").lazyById(1, "id").toList()
        }

        assertEquals(0L, User.query().where("name", "x").orWhereNull("email").count())
        assertEquals(3L, User.query().where("name", "x").orWhereNotNull("email").count())
        assertEquals(2L, User.query().where("name", "Ada").orWhereIn("name", listOf("Grace")).count())
        assertEquals(1L, User.query().where("name", "Ada").orWhereNotIn("name", listOf("Ada", "Grace", "Linus")).count())
        val modelTap = mutableListOf<String>()
        assertEquals(1, User.query().tap { modelTap += "mb" }.where("name", "Ada").get().size)
        assertEquals(listOf("mb"), modelTap)
        assertTrue(User.query().lockForUpdate().toSql().contains(" for update"))
        assertTrue(
            User.query().sharedLock().withoutLock().toSql().let {
                !it.contains(" for update") && !it.contains(" for share")
            },
        )

        assertEquals(
            listOf("Ada", "Grace", "Linus"),
            User.query().orderBy("id").cursor().map { it["name"] as String }.toList(),
        )
        assertEquals(
            listOf("Ada", "Grace", "Linus"),
            User.query().orderBy("id").lazy(2).map { it["name"] as String }.toList(),
        )
        assertEquals(3, User.query().orderBy("id").lazy().take(3).count())
        assertEquals(3, User.query().orderBy("id").lazy(1).count())
        val modelChunks = mutableListOf<Int>()
        User.query().chunkById(2) { modelChunks += it.size }
        assertEquals(listOf(2, 1), modelChunks)
        User.query().chunkById(2, "id") { modelChunks += it.size }
        assertEquals(
            listOf("Ada", "Grace", "Linus"),
            User.query().lazyById(2).map { it["name"] as String }.toList(),
        )
        assertEquals(3, User.query().lazyById().take(3).count())
        assertEquals(3, User.query().lazyById(1).count())
        assertEquals(3, User.query().lazyById(2, "id").take(3).count())
        assertEquals(3, User.query().cursor().take(3).count())
        assertThrows<IllegalArgumentException> { User.query().lazy(0).toList() }
        assertThrows<IllegalArgumentException> { User.query().lazyById(0).toList() }
        assertTrue(User.query().where("id", -1).lazy(2).toList().isEmpty())
        assertTrue(User.query().where("id", -1).lazyById(2).toList().isEmpty())
        assertThrows<CadenceException> {
            User.query().select("name").lazyById(1, "id").toList()
        }

        val existing = User.findOrNew(1)
        assertTrue(existing.exists())
        assertEquals("Ada", existing["name"])
        val neu = User.findOrNew(99)
        assertFalse(neu.exists())
        assertEquals(99L, (neu.getKey() as Number).toLong())
        assertFalse(User.query().findOrNew(null).exists())

        val ada = User.find(1)!!
        assertTrue(ada.isClean("age"))
        ada["age"] = 37
        assertFalse(ada.isClean("age"))
        ada.refresh()
        assertEquals(1, ada.increment("age"))
        assertEquals(37, ada["age"])
        assertEquals(37, User.find(1)!!["age"])
        assertEquals(1, ada.decrement("age", 2))
        assertEquals(35, ada["age"])
        assertEquals(0, User().increment("age"))
        assertEquals(0, User().decrement("age"))

        val longAge = User.create(mapOf("name" to "Long", "email" to "long@x", "age" to 1))
        longAge.setAttribute("age", 1L)
        assertEquals(1, longAge.increment("age", 2))
        assertEquals(3L, longAge.getRawAttribute("age"))
        val floatAge = User.create(mapOf("name" to "Float", "email" to "float@x", "age" to 1))
        floatAge.setAttribute("age", 1.5f)
        assertEquals(1, floatAge.increment("age", 0.5))
        assertEquals(2.0f, floatAge.getRawAttribute("age"))
        val doubleAge = User.create(mapOf("name" to "Double", "email" to "double@x", "age" to 1))
        doubleAge.setAttribute("age", 1.0)
        assertEquals(1, doubleAge.increment("age"))
        assertEquals(2.0, doubleAge.getRawAttribute("age"))
        val nullAge = User.create(mapOf("name" to "NullAge", "email" to "nullage@x"))
        nullAge.setAttribute("age", null)
        assertEquals(1, nullAge.increment("age", 5))
        assertEquals(5.0, nullAge.getRawAttribute("age"))

        val post = Post.create(mapOf("title" to "V", "votes" to 1))
        assertEquals(1, post.increment("votes", 2))
        assertEquals(3, post["votes"])
        val noTs = VoteRow.create(mapOf("title" to "NT", "votes" to 4))
        assertEquals(1, noTs.increment("votes"))
        assertEquals(5, noTs["votes"])
        assertEquals(1, noTs.decrement("votes", 2))
        assertEquals(3, noTs["votes"])
    }

    class VoteRow : Cadence<VoteRow>() {
        override val table = "posts"
        override val timestamps = false
        override val fillable = setOf("title", "votes", "user_id")

        companion object : CadenceCompanion<VoteRow>(VoteRow::class.java)
    }

    @Test
    fun modelTruncateEmptiesTable() {
        User.create(mapOf("name" to "Ada", "email" to "a@x", "age" to 36))
        User.create(mapOf("name" to "Grace", "email" to "g@x", "age" to 45))
        assertEquals(2L, User.count())

        User.truncate()
        assertEquals(0L, User.count())

        User.create(mapOf("name" to "Ada", "email" to "a@x", "age" to 36))
        assertEquals(1L, User.count())
        User.query().truncate()
        assertEquals(0L, User.count())
    }

    @Test
    fun modelObserverReceivesTypedLifecycleHooks() {
        ModelDispatcher.flush()
        val hits = mutableListOf<String>()
        class CountingObserver : ModelObserver<User>() {
            override fun retrieved(model: User) { hits += "retrieved" }
            override fun creating(model: User) { hits += "creating" }
            override fun created(model: User) { hits += "created" }
            override fun updating(model: User) { hits += "updating" }
            override fun updated(model: User) { hits += "updated" }
            override fun saving(model: User) { hits += "saving" }
            override fun saved(model: User) { hits += "saved" }
            override fun deleting(model: User) { hits += "deleting" }
            override fun deleted(model: User) { hits += "deleted" }
        }
        class SoftObserver : ModelObserver<Post>() {
            override fun trashed(model: Post) { hits += "trashed" }
            override fun restoring(model: Post) { hits += "restoring" }
            override fun restored(model: Post) { hits += "restored" }
            override fun forceDeleting(model: Post) { hits += "forceDeleting" }
            override fun forceDeleted(model: Post) { hits += "forceDeleted" }
        }
        try {
            User.observe(CountingObserver())
            Cadence.observe(Post::class.java, SoftObserver())
            val user = User.create(mapOf("name" to "Ada", "email" to "a@o", "age" to 36))
            User.find(user.getKey())
            user["age"] = 37
            user.save()
            user.delete()
            assertTrue(hits.containsAll(listOf("creating", "created", "saving", "saved", "retrieved", "updating", "updated", "deleting", "deleted")))

            hits.clear()
            val post = Post.create(mapOf("title" to "Obs"))
            post.delete()
            post.restore()
            post.forceDelete()
            assertTrue(hits.containsAll(listOf("trashed", "restoring", "restored", "forceDeleting", "forceDeleted")))

            // empty defaults remain callable
            val empty = ModelObserver<User>()
            empty.retrieved(user)
            empty.creating(user)
            empty.created(user)
            empty.updating(user)
            empty.updated(user)
            empty.saving(user)
            empty.saved(user)
            empty.deleting(user)
            empty.deleted(user)
            empty.forceDeleting(user)
            empty.forceDeleted(user)
            empty.restoring(user)
            empty.restored(user)
            empty.trashed(user)
        } finally {
            ModelDispatcher.flush()
        }
    }

    @Test
    fun extractReplicateIsTouchMutatorsAndQuiet() {
        ModelDispatcher.flush()
        val seen = mutableListOf<ModelEvent>()
        ModelEvent.entries.forEach { event ->
            ModelDispatcher.listen(event) { seen += event }
        }

        val user = MutatingUser.create(mapOf("name" to "ada", "email" to "m@x", "age" to 1))
        assertEquals("ADA", user["name"])
        assertEquals("ada!", user.getRawAttribute("name"))

        seen.clear()
        assertTrue(user.saveQuietly())
        assertTrue(seen.isEmpty())

        val clone = user.replicate(listOf("age"))
        assertFalse(clone.exists())
        assertNull(clone.getKey())
        assertEquals("ada!", clone.getRawAttribute("name"))
        assertFalse(clone.hasAttribute("age"))
        assertFalse(clone.hasAttribute("created_at"))
        assertNull(user.replicate().getKey())

        val post = Post.create(mapOf("title" to "Soft"))
        post.delete()
        val postClone = post.replicate()
        assertFalse(postClone.hasAttribute("deleted_at"))
        assertFalse(postClone.hasAttribute("id"))
        val tag = Tag().forceFill(mapOf("code" to "rep", "label" to "R")).also { it.save() }
        assertNull(tag.replicate().getKey())
        assertEquals("R", tag.replicate()["label"])

        assertTrue(user.`is`(MutatingUser.find(user.getKey())))
        assertTrue(user.isNot(null))
        assertTrue(user.isNot(MutatingUser()))
        assertFalse(MutatingUser().`is`(user))
        assertFalse(user.`is`(Post()))
        assertFalse(user.`is`(MutatingUser.create(mapOf("name" to "x", "email" to "o@x"))))

        assertTrue(user.touch())
        assertNotNull(user["updated_at"])
        assertFalse(User().touch())
        assertFalse(Tag().forceFill(mapOf("code" to "t", "label" to "T")).also { it.save() }.touch())

        seen.clear()
        assertTrue(user.deleteQuietly())
        assertTrue(seen.isEmpty())
        assertFalse(User().deleteQuietly())

        ModelDispatcher.withoutEvents {
            ModelDispatcher.withoutEvents { User.create(mapOf("name" to "quiet", "email" to "q@x")) }
        }
        assertTrue(seen.isEmpty())

        val gone = MutatingUser.create(mapOf("name" to "Gone", "email" to "gone2@x", "age" to 1))
        assertSame(gone, gone.loadCount())
        db.table("users").where("id", gone.getKey()).delete()
        assertSame(gone, gone.loadCount("posts as missing_count"))
        ModelDispatcher.flush()
    }

    class MutatingUser : Cadence<MutatingUser>() {
        override val table = "users"
        override val fillable = setOf("name", "email", "age")

        fun posts() = hasMany(Post::class.java, "user_id", "id")

        override fun getMutatedAttribute(key: String, value: Any?): Any? =
            if (key == "name" && value is String) value.removeSuffix("!").uppercase() else value

        override fun setMutatedAttribute(key: String, value: Any?): Any? =
            if (key == "name" && value is String) "$value!" else value

        companion object : CadenceCompanion<MutatingUser>(MutatingUser::class.java)
    }
}
