package dev.kona.cadence.relations

import dev.kona.cadence.Database
import dev.kona.cadence.Cadence
import dev.kona.cadence.CadenceCompanion
import dev.kona.cadence.Grammar
import dev.kona.cadence.schema.Schema
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ThroughUser : Cadence<ThroughUser>() {
    override val table = "users"
    override val fillable = setOf("name")
    override val timestamps = false

    fun posts(): HasMany<ThroughPost> = hasMany(ThroughPost::class.java, "user_id", "id")

    fun comments(): HasManyThrough<ThroughComment, ThroughPost> =
        hasManyThrough(ThroughComment::class.java, ThroughPost::class.java, "user_id", "post_id", "id", "id")

    fun firstComment(): HasOneThrough<ThroughComment, ThroughPost> =
        hasOneThrough(ThroughComment::class.java, ThroughPost::class.java, "user_id", "post_id", "id", "id")

    fun commentsDefaultKeys(): HasManyThrough<ThroughComment, ThroughPost> =
        hasManyThrough(ThroughComment::class.java, ThroughPost::class.java)

    fun firstCommentDefaultKeys(): HasOneThrough<ThroughComment, ThroughPost> =
        hasOneThrough(ThroughComment::class.java, ThroughPost::class.java)

    companion object : CadenceCompanion<ThroughUser>(ThroughUser::class.java)
}

class ThroughPost : Cadence<ThroughPost>() {
    override val table = "posts"
    override val fillable = setOf("title", "user_id")
    override val timestamps = false

    fun comments(): HasMany<ThroughComment> = hasMany(ThroughComment::class.java, "post_id", "id")

    companion object : CadenceCompanion<ThroughPost>(ThroughPost::class.java)
}

class ThroughComment : Cadence<ThroughComment>() {
    override val table = "comments"
    override val fillable = setOf("body", "post_id")
    override val timestamps = false

    companion object : CadenceCompanion<ThroughComment>(ThroughComment::class.java)
}

class HasThroughTest {
    @BeforeEach
    fun setUp() {
        val h2 = JdbcDataSource()
        h2.setURL("jdbc:h2:mem:through${COUNTER.incrementAndGet()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
        Database().addConnection("default", h2, Grammar.ANSI).setAsGlobal()
        Database.global().withConnection { connection ->
            val schema = Schema(connection)
            schema.create("users") {
                id()
                string("name")
            }
            schema.create("posts") {
                id()
                foreignId("user_id")
                string("title")
            }
            schema.create("comments") {
                id()
                foreignId("post_id")
                string("body")
            }
        }
    }

    @AfterEach
    fun tearDown() = Database.clearGlobal()

    @Test
    fun hasManyThroughLazyAndEager() {
        val ada = ThroughUser.create(mapOf("name" to "Ada"))
        val post = ada.posts().create(mapOf("title" to "One"))
        post.comments().create(mapOf("body" to "c1"))
        post.comments().create(mapOf("body" to "c2"))
        ThroughUser.create(mapOf("name" to "Bob")).posts().create(mapOf("title" to "Other"))

        val comments = ada.comments().get()
        assertEquals(2, comments.size)
        assertEquals(setOf("c1", "c2"), comments.map { it.getAttribute("body") }.toSet())

        val users = ThroughUser.with("comments").get()
        val adaLoaded = users.first { it.getAttribute("name") == "Ada" }
        assertEquals(2, (adaLoaded.getRelation("comments") as List<*>).size)
        assertEquals(1, ThroughUser.whereHas("comments").count())
    }

    @Test
    fun hasOneThrough() {
        val ada = ThroughUser.create(mapOf("name" to "Ada"))
        val post = ada.posts().create(mapOf("title" to "One"))
        post.comments().create(mapOf("body" to "first"))
        post.comments().create(mapOf("body" to "second"))
        assertEquals("first", ada.firstComment().getResults()?.getAttribute("body"))
        assertEquals(2, ada.comments().getResults().size)

        val eager = ThroughUser.with("firstComment", "comments").get()
        val loaded = eager.first { it.getAttribute("name") == "Ada" }
        assertEquals("first", (loaded.getRelation("firstComment") as ThroughComment).getAttribute("body"))
        assertEquals(2, (loaded.getRelation("comments") as List<*>).size)
        assertEquals(2L, (ThroughUser.withCount("firstComment").where("name", "Ada").first()!!.getRawAttribute("firstComment_count") as Number).toLong())
        // Touch default-key overloads (FK names follow class names; schema uses shorter names).
        assertEquals("through_user_id", ada.commentsDefaultKeys().firstKey)
        assertEquals("through_user_id", ada.firstCommentDefaultKeys().firstKey)
        assertNotNull(ada.hasManyThrough(ThroughComment::class.java, ThroughPost::class.java, "user_id"))
        assertNotNull(ada.hasManyThrough(ThroughComment::class.java, ThroughPost::class.java, "user_id", "post_id"))
        assertNotNull(ada.hasManyThrough(ThroughComment::class.java, ThroughPost::class.java, "user_id", "post_id", "id"))
        assertNotNull(ada.hasOneThrough(ThroughComment::class.java, ThroughPost::class.java, "user_id"))
        assertNotNull(ada.hasOneThrough(ThroughComment::class.java, ThroughPost::class.java, "user_id", "post_id"))
        assertNotNull(ada.hasOneThrough(ThroughComment::class.java, ThroughPost::class.java, "user_id", "post_id", "id"))
        assertTrue(ThroughUser().comments().get().isEmpty())
        assertNull(ThroughUser().firstComment().getResults())
        ThroughUser.create(mapOf("name" to "Bob"))
        val bob = ThroughUser.with("firstComment").get().first { it.getAttribute("name") == "Bob" }
        assertNull(bob.getRelation("firstComment"))
    }

    companion object {
        private val COUNTER = AtomicInteger()
    }
}
