package io.konaframework.cadence

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
import java.time.Instant
import java.time.LocalDateTime

class CadenceModelTest {
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        database = TestDb.create()
    }

    @AfterEach
    fun tearDown() {
        database.close()
        Database.clearGlobal()
    }

    @Test
    fun conventionsAndMetadata() {
        assertEquals("users", User().table)
        assertEquals("id", User().primaryKey)
        assertTrue(User().incrementing)
        assertTrue(User().timestamps)
        assertEquals(15, User().perPage)
        assertNull(User().connectionName)
        assertEquals(setOf("*"), Locked().guarded)
        assertTrue(Locked().fillable.isEmpty())
        assertTrue(Locked().casts.isEmpty())
        assertEquals("deleted_at", Post().deletedAtColumn)
        assertEquals("archives", Archive().table)

        assertEquals("blog_posts", Cadence.tableNameFor("BlogPost"))
        assertEquals("categories", Cadence.tableNameFor("Category"))
        assertEquals("days", Cadence.tableNameFor("Day"))
        assertEquals("ys", Cadence.tableNameFor("Y"))
        assertEquals("boxes", Cadence.tableNameFor("Box"))
        assertEquals("addresses", Cadence.tableNameFor("Address"))
        assertEquals("branches", Cadence.tableNameFor("Branch"))
        assertEquals("user2_files", Cadence.tableNameFor("User2File"))

        assertEquals(
            mapOf("created_at" to CastType.INSTANT, "updated_at" to CastType.INSTANT, "deleted_at" to CastType.INSTANT),
            Post().effectiveCasts(),
        )
        assertEquals(mapOf("removed_at" to CastType.INSTANT), Archive().effectiveCasts())
        assertTrue(Tag().effectiveCasts().isEmpty())

        val error = assertThrows<CadenceException> { Cadence.of(NoDefaultConstructor::class.java).newInstance() }
        assertTrue(error.message!!.contains("no-arg constructor"))
    }

    @Test
    fun attributesCastsAndDirtyTracking() {
        val user = User()
        assertFalse(user.exists())
        assertFalse(user.hasAttribute("name"))
        user["name"] = "Ada"
        user.setAttribute("age", "36").setAttribute("admin", 1)
        user["settings"] = mapOf("theme" to "dark")
        user["born_at"] = "1815-12-10 00:00:00"
        assertEquals("Ada", user["name"])
        assertEquals(36, user["age"])
        assertEquals(true, user.getAttribute("admin"))
        assertEquals("{\"theme\":\"dark\"}", user.getRawAttribute("settings"))
        assertEquals(mapOf("theme" to "dark"), user["settings"])
        assertEquals(LocalDateTime.of(1815, 12, 10, 0, 0), user["born_at"])
        val name: String? = user.attribute("name")
        assertEquals("Ada", name)
        assertTrue(user.hasAttribute("name"))
        assertEquals("Ada", user.getAttributes()["name"])

        assertTrue(user.isDirty())
        assertTrue(user.isDirty("name"))
        assertFalse(user.isClean())
        user.syncOriginal()
        assertTrue(user.isClean())
        assertEquals("Ada", user.getOriginal("name"))
        user["name"] = "Ada L."
        assertEquals(mapOf("name" to "Ada L."), user.getDirty())
        assertFalse(user.isDirty("age"))
        assertEquals("Ada", user.getOriginal()["name"])
        assertTrue(user.toString().startsWith("User{"))
    }

    @Test
    fun massAssignment() {
        val user = User().fill(mapOf("name" to "Ada", "id" to 99, "unknown" to 1))
        assertEquals("Ada", user["name"])
        assertFalse(user.hasAttribute("id"))
        assertTrue(user.isFillable("name"))
        assertFalse(user.isFillable("id"))
        assertTrue(user.isGuarded("id"))
        assertFalse(user.isTotallyGuarded())

        val locked = Locked()
        assertTrue(locked.isTotallyGuarded())
        val error = assertThrows<MassAssignmentException> { locked.fill(mapOf("name" to "x")) }
        assertEquals("name", error.key)
        assertTrue(error.message!!.contains("Locked"))
        locked.forceFill(mapOf("name" to "x", "id" to 3))
        assertEquals(3, locked.getKey())

        val archive = Archive().fill(mapOf("id" to 5, "name" to "n"))
        assertFalse(archive.hasAttribute("id"))
        assertEquals("n", archive["name"])
        assertTrue(archive.isFillable("name"))
        assertFalse(archive.isTotallyGuarded())

        val post = Post().fill(mapOf("id" to 1, "title" to "t"))
        assertEquals(1, post.getKey())
        assertFalse(post.isGuarded("anything"))
    }

    @Test
    fun createFindSaveDelete() {
        val ada = User.create(mapOf("name" to "Ada", "age" to 36, "admin" to true, "settings" to mapOf("a" to 1)))
        assertTrue(ada.exists())
        assertTrue(ada.wasRecentlyCreated())
        assertEquals(1L, ada.getKey())
        assertTrue(ada.isClean())
        val created = ada["created_at"] as Instant
        assertEquals(created, ada["updated_at"])

        val found = User.find(1)!!
        assertNotSame(ada, found)
        assertTrue(found.exists())
        assertFalse(found.wasRecentlyCreated())
        assertEquals("Ada", found["name"])
        assertEquals(36, found["age"])
        assertEquals(true, found["admin"])
        assertEquals(mapOf("a" to 1), found["settings"])
        assertEquals(created, found["created_at"])
        assertNull(User.find(42))
        val missing = assertThrows<ModelNotFoundException> { User.findOrFail(42) }
        assertEquals(listOf<Any?>(42), missing.ids)
        assertEquals(User::class.java, missing.model)
        assertEquals("No query results for model [io.konaframework.cadence.User] 42", missing.message)
        assertSame(found.getKey(), User.findOrFail(1).getKey())

        val bornAt = Instant.parse("2000-01-01T00:00:00Z")
        val grace = User().fill(mapOf("name" to "Grace"))
        grace["created_at"] = bornAt
        assertTrue(grace.save())
        assertEquals(bornAt, User.find(grace.getKey())!!["created_at"])

        // Updates only send dirty columns and touch updated_at.
        assertTrue(found.save())
        found["age"] = 37
        assertTrue(found.save())
        assertFalse(found.wasRecentlyCreated())
        assertEquals(37, User.find(1)!!["age"])
        assertTrue(found.update(mapOf("name" to "Ada L.")))
        assertEquals("Ada L.", ada.fresh()!!["name"])
        assertEquals(36, ada["age"])
        assertSame(ada, ada.refresh())
        assertEquals(37, ada["age"])
        assertTrue(ada.isClean())

        val explicit = User.find(2)!!
        explicit["updated_at"] = bornAt
        explicit["name"] = "G"
        explicit.save()
        assertEquals(bornAt, User.find(2)!!["updated_at"])

        assertEquals(2, User.all().size)
        assertEquals(2L, User.count())
        assertEquals("Ada L.", User.first()!!["name"])
        assertEquals(listOf<Any?>(1L, 2L), User.findMany(listOf(1, 2, 3)).map { it.getKey() })
        assertEquals(1, User.where("name", "G").get().size)
        assertEquals(1, User.where("age", ">", 30).get().size)
        assertEquals(2, User.whereIn("id", listOf(1, 2)).get().size)

        assertTrue(ada.delete())
        assertFalse(ada.exists())
        assertFalse(ada.delete())
        assertNull(ada.fresh())
        assertSame(ada, ada.refresh())
        assertNull(User.find(1))

        val stale = User.find(2)!!
        User.query().where("id", 2).delete()
        assertThrows<ModelNotFoundException> { stale.refresh() }

        assertFalse(User().delete())
        assertNull(User().fresh())
    }

    @Test
    fun keysWithoutIncrementingOrGeneratedValues() {
        val tag = Tag.create(mapOf("code" to "kt", "label" to "Kotlin"))
        assertEquals("kt", tag.getKey())
        assertEquals("Kotlin", Tag.find("kt")!!["label"])
        assertFalse(tag.hasAttribute("created_at"))
        tag["label"] = "K"
        tag.save()
        assertEquals("K", Tag.findOrFail("kt")["label"])
        assertEquals(1, Tag.destroy("kt", "nope"))
        assertEquals(0, Tag.destroy(listOf("kt")))

        val plain = Plain().fill(mapOf("name" to "p"))
        assertTrue(plain.save())
        assertNull(plain.getKey())
        val error = assertThrows<CadenceException> { plain.delete() }
        assertTrue(error.message!!.contains("has no [id] value"))
    }

    @Test
    fun namedConnectionAndBuilderFactories() {
        val second = TestDb.url()
        database.addConnection("second", ConnectionConfig(second))
        database.connection("second").statement("create table tags (code varchar(20) primary key, label varchar(50))")
        val secondary = Cadence.of(Secondary::class.java)
        val tag = secondary.newInstance()
        tag.forceFill(mapOf("code" to "s"))
        tag.save()
        assertSame(database.connection("second"), tag.connection())
        assertEquals(1L, secondary.count())
        assertEquals(0L, Tag.count())

        val hydrated = User().newFromBuilder(mapOf("id" to 7L, "name" to "Row"))
        assertTrue(hydrated.exists())
        assertTrue(hydrated.isClean())
        assertEquals("Row", hydrated["name"])
        assertEquals("N", User().newInstance(mapOf("name" to "N"))["name"])
        assertEquals("M", User.newInstance(mapOf("name" to "M"))["name"])
        assertSame(User::class.java, User.modelClass)
        assertEquals(listOf(hydrated.getKey()), User.query().hydrate(listOf(mapOf("id" to 7L))).map { it.getKey() })
    }

    @Test
    fun relationStorageAndSerialization() {
        val user = User().forceFill(mapOf("id" to 1, "age" to "3"))
        val post = Post().forceFill(mapOf("title" to "t"))
        assertFalse(user.relationLoaded("posts"))
        user.setRelation("posts", listOf(post, "raw")).setRelation("latest", post).setRelation("count", 2)
        assertTrue(user.relationLoaded("posts"))
        assertSame(post, user.getRelation("latest"))
        assertNull(user.getRelation("none"))
        assertEquals(setOf("posts", "latest", "count"), user.getRelations().keys)
        assertEquals(
            mapOf(
                "id" to 1,
                "age" to 3,
                "posts" to listOf(mapOf("title" to "t"), "raw"),
                "latest" to mapOf("title" to "t"),
                "count" to 2,
            ),
            user.toMap(),
        )
    }

    @Test
    fun modelBuilderConstraintsAndReads() {
        listOf("Ada" to 36, "Grace" to 45, "Linus" to 28, "Alan" to 41).forEach { (name, age) ->
            User.create(mapOf("name" to name, "age" to age, "email" to if (age > 40) null else "$name@x.io"))
        }
        Post.create(mapOf("user_id" to 1, "title" to "Engines", "votes" to 3))

        val builder = User.query()
            .select("users.*")
            .where("age", ">", 20)
            .where { it.where("name", "Ada").orWhere("name", "Grace").orWhere("name", "Alan") }
            .orWhere("name", "Linus")
            .orderBy("id")
        assertEquals(listOf<Any?>("Ada", "Grace", "Linus", "Alan"), builder.pluck("name"))
        assertSame(builder.getQuery(), builder.getQuery())
        assertEquals(
            listOf<Any?>("Ada"),
            User.query().where("name", "Ada").orWhere("name", "=", "Nobody").orWhere { it.where("id", 0) }.get().map { it["name"] },
        )
        assertEquals(2, User.query().whereNull("email").get().size)
        assertEquals(2, User.query().whereNotNull("email").get().size)
        assertEquals(1, User.query().whereNotIn("name", listOf("Ada", "Grace", "Linus")).get().size)
        assertEquals(2, User.query().whereBetween("age", 30, 41).get().size)
        assertEquals(1, User.query().whereRaw("age > 44").get().size)
        assertEquals(1, User.query().whereRaw("age > ?", listOf(44)).get().size)
        assertEquals(
            listOf<Any?>("Engines"),
            User.query().join("posts", "users.id", "=", "posts.user_id").select("posts.title").pluck("title"),
        )
        assertEquals(4, User.query().leftJoin("posts", "users.id", "=", "posts.user_id").get().size)
        assertEquals(listOf<Any?>(45, 41, 36, 28), User.query().orderByDesc("age").pluck("age"))
        assertEquals(listOf<Any?>(28, 36), User.query().orderBy("age", "asc").limit(2).pluck("age"))
        assertEquals(listOf<Any?>(41), User.query().orderBy("id").offset(3).pluck("age"))
        assertEquals(listOf<Any?>(45), User.query().orderBy("id").skip(1).take(1).pluck("age"))
        assertEquals(listOf<Any?>(28), User.query().orderBy("id").forPage(2, 2).limit(1).pluck("age"))
        assertEquals("Alan", User.query().latest().orderByDesc("id").first()!!["name"])
        assertEquals("Ada", User.query().oldest().orderBy("id").first()!!["name"])
        assertEquals("Linus", User.query().latest("age").oldest("id").orderBy("age").get().last()["name"])

        assertEquals(36, User.query().where("name", "Ada").value("age"))
        assertTrue(User.query().where("name", "Ada").exists())
        assertTrue(User.query().where("name", "Nobody").doesntExist())
        assertEquals("Grace", User.query().where("name", "Grace").firstOrFail()["name"])
        val none = assertThrows<ModelNotFoundException> { User.query().where("name", "Nobody").firstOrFail() }
        assertEquals("No query results for model [io.konaframework.cadence.User]", none.message)
        assertEquals("select \"users\".* from \"users\" where \"name\" = ?", User.query().select("users.*").where("name", "x").toSql())
        assertEquals(listOf<Any?>("x"), User.query().where("name", "x").getBindings())

        assertEquals(1, User.query().where("name", "Ada").update(mapOf("age" to 50)))
        assertEquals(50, User.find(1)!!["age"])
        assertNotNull(User.find(1)!!["updated_at"])
        val fixed = Instant.parse("2001-01-01T00:00:00Z")
        User.query().where("name", "Ada").update(mapOf("updated_at" to fixed))
        assertEquals(fixed, User.find(1)!!["updated_at"])
        Tag.create(mapOf("code" to "a"))
        assertEquals(1, Tag.query().update(mapOf("label" to "A")))
        assertEquals(0, User.query().restore())
        assertEquals(1, User.query().where("name", "Linus").delete())
        assertEquals(1, User.query().where("name", "Alan").forceDelete())
        assertEquals(2L, User.count())
    }

    @Test
    fun pagination() {
        (1..5).forEach { Post.create(mapOf("title" to "p$it")) }
        val page = Post.paginate()
        assertEquals(2, page.perPage)
        assertEquals(5L, page.total)
        assertEquals(3, page.lastPage)
        assertEquals(listOf<Any?>("p1", "p2"), page.data.map { it["title"] })
        assertTrue(page.data.all { it.exists() })

        val last = Post.paginate(2, 3)
        assertEquals(listOf<Any?>("p5"), last.data.map { it["title"] })
        assertFalse(last.hasMorePages())
        assertEquals(listOf<Any?>("p4"), Post.query().orderByDesc("id").paginate(1, 2).data.map { it["title"] })
        assertEquals(5, Post.query().paginate().let { it.total.toInt() })
        assertEquals(1, User.paginate().lastPage)
        assertEquals("p1", (Post.paginate(1).toMap()["data"] as List<*>).map { (it as Map<*, *>)["title"] }.single())
    }

    @Test
    fun softDeletes() {
        val first = Post.create(mapOf("title" to "first"))
        val second = Post.create(mapOf("title" to "second"))
        Post.create(mapOf("title" to "third"))
        assertFalse(first.trashed())

        assertTrue(first.delete())
        assertTrue(first.exists())
        assertTrue(first.trashed())
        assertTrue(first.isClean())
        assertNotNull(first["deleted_at"] as Instant)
        assertEquals(first["deleted_at"], first["updated_at"])
        assertNull(Post.find(first.getKey()))
        assertEquals(2L, Post.count())
        assertEquals(3, Post.withTrashed().get().size)
        assertEquals(listOf<Any?>("first"), Post.onlyTrashed().pluck("title"))
        assertEquals(listOf<Any?>("first"), first.fresh()!!.let { listOf(it["title"]) })
        assertTrue(first.fresh()!!.trashed())

        val scoped = Post.query().where("title", "first").orWhere("title", "second")
        assertEquals(
            "select * from \"posts\" where (\"title\" = ? or \"title\" = ?) and \"posts\".\"deleted_at\" is null",
            scoped.toSql(),
        )
        assertEquals(listOf<Any?>("second"), scoped.pluck("title"))
        assertEquals("select * from \"posts\" where \"posts\".\"deleted_at\" is not null", Post.onlyTrashed().toSql())
        assertEquals("select * from \"posts\"", Post.withTrashed().toSql())

        assertTrue(first.restore())
        assertFalse(first.trashed())
        assertEquals(3L, Post.count())

        assertEquals(2, Post.query().where("title", "!=", "third").delete())
        assertEquals(1L, Post.count())
        assertEquals(2, Post.onlyTrashed().count())
        assertEquals(1, Post.query().where("title", "first").restore())
        assertEquals(2L, Post.count())
        assertEquals(1, Post.withTrashed().where("title", "second").forceDelete())
        assertEquals(2, Post.withTrashed().count())

        assertTrue(second.forceDelete())
        assertFalse(second.exists())
        assertFalse(second.forceDelete())
        assertEquals(1, Post.destroy(first.getKey()!!))
        assertTrue(Post.withTrashed().find(first.getKey())!!.trashed())

        val archive = Archive.create(mapOf("name" to "old"))
        assertTrue(archive.delete())
        assertTrue(archive.trashed())
        assertFalse(archive.hasAttribute("updated_at"))
        assertEquals(0L, Archive.count())
        assertEquals(1, Archive.onlyTrashed().restore())
        assertEquals(1L, Archive.count())
        assertTrue(Archive.first()!!.forceDelete())
        assertEquals(0, Archive.withTrashed().count())
    }
}
