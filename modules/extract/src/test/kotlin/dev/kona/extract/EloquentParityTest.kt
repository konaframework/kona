package dev.kona.extract

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

    class MutatingUser : Extract<MutatingUser>() {
        override val table = "users"
        override val fillable = setOf("name", "email", "age")

        fun posts() = hasMany(Post::class.java, "user_id", "id")

        override fun getMutatedAttribute(key: String, value: Any?): Any? =
            if (key == "name" && value is String) value.removeSuffix("!").uppercase() else value

        override fun setMutatedAttribute(key: String, value: Any?): Any? =
            if (key == "name" && value is String) "$value!" else value

        companion object : ExtractCompanion<MutatingUser>(MutatingUser::class.java)
    }
}
