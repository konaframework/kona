package dev.kona.extract.relations

import dev.kona.extract.Database
import dev.kona.extract.Extract
import dev.kona.extract.ExtractCompanion
import dev.kona.extract.Grammar
import dev.kona.extract.schema.Schema
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class MorphPost : Extract<MorphPost>() {
    override val table = "posts"
    override val fillable = setOf("title")
    override val timestamps = false

    fun comments(): MorphMany<MorphComment> = morphMany(MorphComment::class.java, "commentable")

    fun image(): MorphOne<MorphImage> = morphOne(MorphImage::class.java, "imageable")

    fun taggedComments(): MorphMany<MorphComment> =
        morphMany(MorphComment::class.java, "commentable", "commentable_type", "commentable_id", "id")

    fun cover(): MorphOne<MorphImage> =
        morphOne(MorphImage::class.java, "imageable", "imageable_type", "imageable_id", "id")

    companion object : ExtractCompanion<MorphPost>(MorphPost::class.java)
}

class MorphVideo : Extract<MorphVideo>() {
    override val table = "videos"
    override val fillable = setOf("title")
    override val timestamps = false

    fun comments(): MorphMany<MorphComment> = morphMany(MorphComment::class.java, "commentable")

    companion object : ExtractCompanion<MorphVideo>(MorphVideo::class.java)
}

class MorphComment : Extract<MorphComment>() {
    override val table = "comments"
    override val fillable = setOf("body", "commentable_type", "commentable_id")
    override val timestamps = false

    fun commentable(): MorphTo = morphTo("commentable")

    fun owner(): MorphTo = morphTo("commentable", "commentable_type", "commentable_id")

    companion object : ExtractCompanion<MorphComment>(MorphComment::class.java)
}

class MorphImage : Extract<MorphImage>() {
    override val table = "images"
    override val fillable = setOf("url", "imageable_type", "imageable_id")
    override val timestamps = false

    companion object : ExtractCompanion<MorphImage>(MorphImage::class.java)
}

class MorphRelationsTest {
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        Relation.morphMap(
            mapOf(
                "posts" to MorphPost::class.java,
                "videos" to MorphVideo::class.java,
            ),
        )
        val h2 = JdbcDataSource()
        h2.setURL("jdbc:h2:mem:morph${COUNTER.incrementAndGet()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
        database = Database().addConnection("default", h2, Grammar.ANSI).setAsGlobal()
        database.withConnection { connection ->
            val schema = Schema(connection)
            schema.create("posts") {
                id()
                string("title")
            }
            schema.create("videos") {
                id()
                string("title")
            }
            schema.create("comments") {
                id()
                string("body")
                nullableMorphs("commentable")
            }
            schema.create("images") {
                id()
                string("url")
                nullableMorphs("imageable")
            }
        }
    }

    @AfterEach
    fun tearDown() {
        Relation.clearMorphMap()
        Database.clearGlobal()
    }

    @Test
    fun morphManyCreateAndLazyLoad() {
        val post = MorphPost.create(mapOf("title" to "Hello"))
        post.comments().create(mapOf("body" to "Nice"))
        post.comments().create(mapOf("body" to "Again"))
        MorphVideo.create(mapOf("title" to "Clip")).comments().create(mapOf("body" to "Video note"))

        val loaded = post.comments().get()
        assertEquals(2, loaded.size)
        assertTrue(loaded.all { it.getAttribute("commentable_type") == "posts" })
        assertEquals(1, MorphVideo.query().first()!!.comments().count())
    }

    @Test
    fun morphOneAndMorphTo() {
        val post = MorphPost.create(mapOf("title" to "P"))
        post.image().create(mapOf("url" to "https://example.com/a.png"))
        assertEquals("https://example.com/a.png", post.image().getResults()?.getAttribute("url"))

        post.comments().create(mapOf("body" to "x"))
        val linked = MorphComment.query().where("body", "x").first()!!
        val owner = linked.commentable().getResults() as MorphPost
        assertEquals("P", owner.getAttribute("title"))
        assertEquals(post.getKey(), owner.getKey())

        linked.commentable().dissociate()
        linked.save()
        assertNull(linked.fresh()!!.commentable().getResults())
    }

    @Test
    fun morphEagerLoadAndWhereHas() {
        val post = MorphPost.create(mapOf("title" to "A"))
        val video = MorphVideo.create(mapOf("title" to "B"))
        post.comments().create(mapOf("body" to "p1"))
        video.comments().create(mapOf("body" to "v1"))

        val posts = MorphPost.with("comments").get()
        assertEquals(1, (posts[0].getRelation("comments") as List<*>).size)

        val comments = MorphComment.with("commentable").get()
        assertEquals(2, comments.size)
        assertTrue(comments.any { it.getRelation("commentable") is MorphPost })
        assertTrue(comments.any { it.getRelation("commentable") is MorphVideo })

        assertEquals(1, MorphPost.whereHas("comments").count())
        assertEquals(1L, (MorphPost.withCount("comments").first()!!.getRawAttribute("comments_count") as Number).toLong())
    }

    @Test
    fun morphToAssociate() {
        val post = MorphPost.create(mapOf("title" to "Z"))
        val comment = MorphComment.create(mapOf("body" to "orphan"))
        comment.commentable().associate(post)
        comment.save()
        assertEquals("posts", comment.fresh()!!.getAttribute("commentable_type"))
        assertEquals(post.getKey(), comment.fresh()!!.commentable().getResults()!!.getKey())
    }

    @Test
    fun morphEdgeCoverage() {
        val unsaved = MorphPost()
        assertTrue(unsaved.comments().get().isEmpty())
        assertNull(unsaved.image().getResults())

        val post = MorphPost.create(mapOf("title" to "E"))
        post.image().create(mapOf("url" to "u"))
        val withImage = MorphPost.with("image").get().first { it.getKey() == post.getKey() }
        assertEquals("u", (withImage.getRelation("image") as MorphImage).getAttribute("url"))
        assertEquals(1L, (MorphPost.withCount("image").where("id", post.getKey()).first()!!.getRawAttribute("image_count") as Number).toLong())
        assertEquals(1, (MorphPost.withExists("image").where("id", post.getKey()).first()!!.getRawAttribute("image_exists") as Number).toInt())

        assertNotNull(post.cover().getResults())
        assertTrue(post.taggedComments().getResults().isEmpty())

        Relation.clearMorphMap()
        val fqcn = MorphPost::class.java.name
        val comment = MorphComment.create(
            mapOf("body" to "fq", "commentable_type" to fqcn, "commentable_id" to post.getKey()),
        )
        assertEquals(post.getKey(), comment.commentable().getResults()!!.getKey())
        assertNotNull(comment.owner().getResults())
        assertNull(MorphMap.resolveOrNull("missing.Type"))
        assertTrue(comment.commentable().getEager().isEmpty())
        assertNull(MorphComment().commentable().getResults())
        // MorphTo.get() always returns empty (eager path is custom); still exercises addConstraints.
        assertTrue(MorphComment().commentable().get().isEmpty())
        assertTrue(comment.commentable().get().isEmpty())
        val typeOnly = MorphComment().newFromBuilder(mapOf("commentable_type" to fqcn))
        val idOnly = MorphComment().newFromBuilder(mapOf("commentable_id" to post.getKey()))
        assertTrue(typeOnly.commentable().get().isEmpty())
        assertTrue(idOnly.commentable().get().isEmpty())
        assertNull(typeOnly.commentable().getResults())
        assertNull(idOnly.commentable().getResults())
        val morphTo = comment.commentable()
        morphTo.addEagerConstraints(listOf(comment))
        morphTo.match(listOf(comment), emptyList(), "commentable")
        // Intermediate Java/@JvmOverloads arities
        assertNotNull(post.morphMany(MorphComment::class.java, "commentable", "commentable_type"))
        assertNotNull(post.morphMany(MorphComment::class.java, "commentable", "commentable_type", "commentable_id"))
        assertNotNull(post.morphOne(MorphImage::class.java, "imageable", "imageable_type"))
        assertNotNull(post.morphOne(MorphImage::class.java, "imageable", "imageable_type", "imageable_id"))
        assertNotNull(comment.morphTo("commentable", "commentable_type"))
        assertEquals("_morph_to", MorphToRelated().table)
        assertFalse(MorphToRelated().timestamps)

        // Eager-load parents with no morph rows / unknown type / null type.
        MorphPost.create(mapOf("title" to "lonely"))
        val lonely = MorphPost.with("comments", "image").get().first { it.getAttribute("title") == "lonely" }
        assertTrue((lonely.getRelation("comments") as List<*>).isEmpty())
        assertNull(lonely.getRelation("image"))
        val unknown = MorphComment.create(
            mapOf("body" to "u", "commentable_type" to "no.Such", "commentable_id" to 1L),
        )
        assertNull(unknown.commentable().getResults())
        val nullType = MorphComment.create(mapOf("body" to "n"))
        MorphComment.with("commentable").get()
        assertNull(nullType.fresh()!!.getRelation("commentable"))
        // Empty id set for a unique type group (no other rows share this morph type).
        val videoFqcn = MorphVideo::class.java.name
        MorphComment.create(mapOf("body" to "noid", "commentable_type" to videoFqcn))
        val noIdLoaded = MorphComment.with("commentable").get().first { it.getAttribute("body") == "noid" }
        assertNull(noIdLoaded.getRelation("commentable"))
        assertNull(MorphComment().newFromBuilder(mapOf("commentable_id" to 1L)).commentable().getResults())
    }

    companion object {
        private val COUNTER = AtomicInteger()
    }
}
