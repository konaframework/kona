package org.konaframework.firefly.forms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.document.Document
import org.konaframework.cadence.relations.BelongsTo
import org.konaframework.cadence.relations.HasMany
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function

class RelationshipSelectTest {
    @Test
    fun cadenceRelationshipReceivesModelBuilder() {
        database()
        val authors = org.konaframework.firefly.tables.CadenceRecordStore(RelAuthor::class.java)
        val posts = org.konaframework.firefly.tables.CadenceRecordStore(RelPost::class.java)
        val profiles = org.konaframework.firefly.tables.CadenceRecordStore(RelProfile::class.java)
        val ada = authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Ada", "active" to true)))
        val grace = authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Grace", "active" to false)))
        authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Lin", "active" to true)))
        posts.save(posts.fill(posts.newRecord(), mapOf("author_id" to ada.getKey(), "title" to "Yes")))
        posts.save(posts.fill(posts.newRecord(), mapOf("author_id" to grace.getKey(), "title" to "Yes")))
        profiles.save(profiles.fill(profiles.newRecord(), mapOf("author_id" to ada.getKey(), "city" to "London")))
        profiles.save(profiles.fill(profiles.newRecord(), mapOf("author_id" to grace.getKey(), "city" to "Paris")))

        var seen: ModelBuilder<RelAuthor>? = null
        var sql = ""
        val select = Select.make("authorId").relationship("author", "name") { query ->
            @Suppress("UNCHECKED_CAST")
            val builder = query as ModelBuilder<RelAuthor>
            seen = builder
            builder.select("ff_rel_authors.*")
                .scope { it.where("ff_rel_authors.active", true) }
                .with("posts")
                .whereHas("posts") { nested -> nested.where("title", "Yes") }
                .join("ff_rel_profiles", "ff_rel_profiles.author_id", "=", "ff_rel_authors.id")
            sql = builder.toSql()
        }
        val schema = Schema.make().components(select).record(RelPost())
        val options = select.resolvedOptions(schema)
        assertTrue(seen is ModelBuilder<*>)
        assertTrue(sql.contains("join"))
        assertTrue(sql.contains("exists"))
        assertEquals(listOf("Ada"), options.map { it.label })
        assertTrue((seen!!.get().first()).relationLoaded("posts"))

        val labeled = Select.make("authorId").relationship("author", "name").getOptionLabelFromRecordUsing { record ->
            "Dr " + (record as RelAuthor).getAttribute("name")
        }
        assertEquals(
            "Dr Ada",
            labeled.resolvedOptions(Schema.make().components(labeled).record(RelPost())).first { it.label == "Dr Ada" }.label,
        )

        val plain = Select.make("authorId").relationship("author", "name")
        assertTrue(plain.resolvedOptions(Schema.make().components(plain).record(RelPost())).any { it.label == "Grace" })

        val sqlPaper = Select.make("authorId").relationship("author", "name")
        assertThrows(FireflyException::class.java) {
            sqlPaper.resolvedOptions(Schema.make().components(sqlPaper).record(SqlPaper()))
        }
        val sqlQuery = Select.make("authorId").relationship("author", "name")
        assertThrows(FireflyException::class.java) {
            sqlQuery.resolvedOptions(Schema.make().components(sqlQuery).record(SqlQueryHolder()))
        }
        val direct = Select.make("authorId").relationship("author", "name")
        assertTrue(direct.resolvedOptions(Schema.make().components(direct).record(DirectBuilder())).any { it.label == "Ada" })
    }

    @Test
    fun documentRelationshipUsesItsOwnQuery() {
        val paper = DocPaper()
        var seen: Any? = null
        val select = Select.make("authorId").relationship("author", "name") { query ->
            seen = query
            (query as OptionQuery).where("name", "Ada")
        }
        val options = select.resolvedOptions(Schema.make().components(select).record(paper))
        assertTrue(seen is OptionQuery)
        assertFalse(seen is ModelBuilder<*>)
        assertEquals(listOf("Ada"), options.map { it.label })

        val fromModel = Select.make("authorId").relationship("author", "name")
        val modelOptions = fromModel.resolvedOptions(Schema.make().components(fromModel).record(DocHolder()))
        assertEquals("Ada", modelOptions.single().label)

        assertThrows(FireflyException::class.java) {
            Select.make("authorId").relationship("missing", "name")
                .resolvedOptions(Schema.make().record(OptionShelf(emptyList())))
        }
        assertThrows(FireflyException::class.java) {
            Select.make("authorId").relationship("author", "name")
                .resolvedOptions(Schema.make().record(NullShelf()))
        }
        assertThrows(FireflyException::class.java) {
            Select.make("authorId").relationship("author", "name")
                .resolvedOptions(Schema.make().record(BareShelf()))
        }
        assertThrows(FireflyException::class.java) {
            Select.make("authorId").relationship("author", "name")
                .resolvedOptions(Schema.make().record(TextShelf()))
        }
        assertThrows(FireflyException::class.java) {
            Select.make("authorId").relationship("author", "name")
                .resolvedOptions(Schema.make().record(EmptyDocHolder()))
        }
        assertThrows(FireflyException::class.java) {
            Select.make("authorId").relationship("author", "name")
                .resolvedOptions(Schema.make().record(NullQueryHolder()))
        }

        val blank = Select.make("authorId").relationship("author", "name").getOptionLabelFromRecordUsing(NullLabel())
        assertEquals("", blank.resolvedOptions(Schema.make().components(blank).record(DocHolder())).single().label)

        assertEquals("", recordKey(null))
        assertEquals("", recordKey(mapOf("name" to "A")))
        assertEquals("7", recordKey(mapOf("id" to 7)))
        assertEquals("9", recordKey(WithId()))
        assertEquals("", recordKey(Named("4")))
        assertEquals("", recordKey(RelAuthor()))
        assertNull(recordAttribute(null, "name"))
        assertEquals("Ada", recordAttribute(mapOf("name" to "Ada"), "name"))
        assertEquals("Ada", recordAttribute(Person(), "name"))
        assertNull(recordAttribute(Person(), "missing"))
        assertNull(recordAttribute(RelAuthor(), "name"))
        assertFalse(isDocumentModel(Person::class.java))
        assertTrue(isDocumentModel(DocAuthor::class.java))

        val listed = Select.make("authorId").relationship("author", "name")
        assertEquals("Ada", listed.resolvedOptions(Schema.make().record(ListShelf())).single().label)
        val roomy = Select.make("authorId").relationship("author", "name").optionsLimit(10)
        assertEquals(2, roomy.resolvedOptions(Schema.make().record(paper)).size)
        assertThrows(FireflyException::class.java) {
            Select.make("authorId").relationship(NullText(), Function { "name" })
                .resolvedOptions(Schema.make().record(paper))
        }
        val blankTitle = Select.make("authorId").relationship(Function<Get, String> { "author" }, NullText())
        assertEquals("", blankTitle.resolvedOptions(Schema.make().record(DocHolder())).single().label)
    }

    @Test
    fun relationshipSearchLooksPastPreloadLimit() {
        database()
        val authors = org.konaframework.firefly.tables.CadenceRecordStore(RelAuthor::class.java)
        authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Ada", "active" to true)))
        authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Grace", "active" to true)))
        authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Lin", "active" to true)))
        authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Zoe", "active" to true)))
        val select = Select.make("authorId").relationship("author", "name").preloadLimit(2)
        val schema = Schema.make().components(select).record(RelPost())
        assertEquals(2, select.resolvedOptions(schema).size)
        val hits = select.search(schema, "Zoe")
        assertEquals(listOf("Zoe"), hits.map { it.label })
        assertTrue(select.search(schema, "a").any { it.label == "Ada" })
        assertTrue(select.search(schema, "a").any { it.label == "Grace" })
        // Empty needle still caps to the search/preload limit.
        assertEquals(2, select.search(schema, "").size)
        val dynamic = Select.make("authorId")
            .relationship(Function<Get, String> { "author" }, Function<Get, String> { "name" })
            .preloadLimit(1)
        assertEquals(1, dynamic.search(Schema.make().components(dynamic).record(RelPost()), "a").size)
        val cadenceModified = Select.make("authorId").relationship("author", "name") { query ->
            @Suppress("UNCHECKED_CAST")
            (query as ModelBuilder<RelAuthor>).where("active", true)
        }.preloadLimit(10)
        assertTrue(
            cadenceModified.search(Schema.make().components(cadenceModified).record(RelPost()), "Ada")
                .any { it.label == "Ada" },
        )
    }

    @Test
    fun relationshipSearchFallsBackToInMemoryFilter() {
        val paper = DocPaper()
        val select = Select.make("authorId").relationship("author", "name").optionsLimit(10)
        val schema = Schema.make().components(select).record(paper)
        // OptionQuery.where is 2-arg, so search filters labels in memory past any SQL push-down.
        assertEquals(listOf("Grace"), select.search(schema, "Grace").map { it.label })
        assertTrue(select.search(schema, "").any { it.label == "Ada" })
        val likeShelf = LikeShelf()
        val likeSelect = Select.make("authorId").relationship("author", "name")
        val likeSchema = Schema.make().components(likeSelect).record(likeShelf)
        assertEquals(listOf("Ada"), likeSelect.search(likeSchema, "Ada").map { it.label })
        assertTrue(likeSelect.search(likeSchema, "missing").isEmpty())
        // Uncapped RelationshipOptions.search + blank title attributes.
        val blankTitles = RelationshipOptions.search(
            ListShelf(),
            "author",
            "missing",
            null,
            "1",
            null,
        )
        assertEquals(1, blankTitles.size)
        val limited = RelationshipOptions.search(
            ListShelf(),
            "author",
            "name",
            null,
            "",
            1,
        )
        assertEquals(1, limited.size)
        val noOwner = Select.make("authorId").relationship("author", "name")
        assertTrue(noOwner.search(Schema.make().components(noOwner), "Ada").isEmpty())
        val modified = Select.make("authorId").relationship("author", "name") { query ->
            (query as OptionQuery).where("name", "Ada")
        }
        assertEquals(
            listOf("Ada"),
            modified.search(Schema.make().components(modified).record(DocPaper()), "").map { it.label },
        )
        // Document model path (DocHolder.author → DocAuthor.query → OptionQuery) with and without modify.
        val fromDoc = Select.make("authorId").relationship("author", "name")
        assertEquals(
            listOf("Ada"),
            fromDoc.search(Schema.make().components(fromDoc).record(DocHolder()), "Ada").map { it.label },
        )
        val fromDocModified = Select.make("authorId").relationship("author", "name") { query ->
            (query as OptionQuery).where("name", "Ada")
        }
        assertEquals(
            listOf("Ada"),
            fromDocModified.search(Schema.make().components(fromDocModified).record(DocHolder()), "").map { it.label },
        )
        val badWhere = Select.make("authorId").relationship("author", "name")
        assertEquals(
            listOf("Ada"),
            badWhere.search(Schema.make().components(badWhere).record(BadWhereShelf()), "Ada").map { it.label },
        )
        val uncappedRel = Select.make("authorId").relationship("author", "name").preload(false)
        assertEquals(
            listOf("Ada"),
            uncappedRel.search(Schema.make().components(uncappedRel).record(ListShelf()), "Ada").map { it.label },
        )
        val listModified = Select.make("authorId").relationship("author", "name") { query ->
            (query as ListQuery).touched = true
        }
        assertEquals(
            listOf("Ada"),
            listModified.search(Schema.make().components(listModified).record(ListShelf()), "Ada").map { it.label },
        )
    }

    @Test
    fun staticOptionSearchIgnoresPreloadCapWhenQuerying() {
        val many = (1..5).associate { it.toString() to "Item $it" }
        val capped = Select.make("kind").preload(true).preloadLimit(2).options(many)
        val schema = Schema.make().components(capped)
        assertEquals(2, capped.resolvedOptions(schema).size)
        assertEquals(listOf("Item 5"), capped.search(schema, "Item 5").map { it.label })
        assertEquals(2, capped.search(schema, "").size)
        val uncapped = Select.make("kind").preload(false).options(many)
        assertEquals(5, uncapped.search(Schema.make().components(uncapped), "").size)
    }
}

private class LikeQuery(private val rows: List<Map<String, Any?>>) {
    private var filtered: List<Map<String, Any?>> = rows

    fun where(column: String, operator: String, value: Any?): LikeQuery = apply {
        val needle = value.toString().trim('%').lowercase()
        filtered = rows.filter {
            operator.equals("like", ignoreCase = true) &&
                (it[column]?.toString().orEmpty().lowercase().contains(needle) ||
                    it["id"]?.toString().orEmpty().lowercase().contains(needle))
        }
    }

    fun get(): List<Map<String, Any?>> = filtered
}

private class LikeShelf {
    fun author(): LikeQuery =
        LikeQuery(
            listOf(
                mapOf("id" to "1", "name" to "Ada"),
                mapOf("id" to "2", "name" to "Grace"),
            ),
        )
}

/** Has a `where` method whose first parameter is not String — skips SQL push-down. */
private class BadWhereQuery(private val rows: List<Map<String, Any?>>) {
    fun where(column: Int, operator: String, value: Any?): BadWhereQuery = this

    fun get(): List<Map<String, Any?>> = rows
}

private class BadWhereShelf {
    fun author(): BadWhereQuery =
        BadWhereQuery(listOf(mapOf("id" to "1", "name" to "Ada"), mapOf("id" to "2", "name" to "Grace")))
}

private class WithId {
    fun id(): String = "9"
}

private class Named(val id: String)

private class Person {
    fun name(): String = "Ada"
}

private class NullShelf {
    fun author(): Any? = null
}

private class BareQuery

private class BareShelf {
    fun author(): BareQuery = BareQuery()
}

private class TextQuery {
    fun get(): String = "nope"
}

private class TextShelf {
    fun author(): TextQuery = TextQuery()
}

private class DocAuthor : Document<DocAuthor>() {
    fun query(): OptionQuery = OptionQuery(listOf(mapOf("id" to "1", "name" to "Ada")))
}

private class DocHolder {
    fun author(): DocAuthor = DocAuthor()
}

private class DocPaper : Document<DocPaper>() {
    fun author(): OptionQuery = OptionQuery(
        listOf(
            mapOf("id" to "1", "name" to "Ada"),
            mapOf("id" to "2", "name" to "Grace"),
        ),
    )
}

private class EmptyDoc : Document<EmptyDoc>()

private class EmptyDocHolder {
    fun author(): EmptyDoc = EmptyDoc()
}

private class NullQueryDoc : Document<NullQueryDoc>() {
    fun query(): Any? = null
}

private class NullQueryHolder : Document<NullQueryHolder>() {
    fun author(): NullQueryDoc = NullQueryDoc()
}

private class SqlPaper : Document<SqlPaper>() {
    fun author(): ModelBuilder<RelAuthor> = RelAuthor().newQuery()
}

private class SqlQueryDoc : Document<SqlQueryDoc>() {
    fun query(): ModelBuilder<RelAuthor> = RelAuthor().newQuery()
}

private class SqlQueryHolder {
    fun author(): SqlQueryDoc = SqlQueryDoc()
}

private class DirectBuilder {
    fun author(): ModelBuilder<RelAuthor> = RelAuthor().newQuery()
}

private class ListQuery(private val rows: List<Map<String, Any?>>) {
    var touched: Boolean = false

    fun list(): List<Map<String, Any?>> = rows
}

private class ListShelf {
    fun author(): ListQuery = ListQuery(listOf(mapOf("id" to "1", "name" to "Ada")))
}

private fun database(): Database {
    val url = "jdbc:h2:mem:ffrel${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
    val connection = database.connection()
    listOf(
        """
        create table ff_rel_authors (
            id bigint auto_increment primary key,
            name varchar(100), active boolean
        )
        """,
        """
        create table ff_rel_posts (
            id bigint auto_increment primary key,
            author_id bigint, title varchar(100)
        )
        """,
        """
        create table ff_rel_profiles (
            id bigint auto_increment primary key,
            author_id bigint, city varchar(100)
        )
        """,
    ).forEach { connection.statement(it.trim()) }
    return database.setAsGlobal()
}

class RelAuthor : Cadence<RelAuthor>() {
    override val table = "ff_rel_authors"
    override val timestamps = false
    override val fillable = setOf("name", "active")

    fun posts(): HasMany<RelPost> = hasMany(RelPost::class.java, "author_id", "id")
}

class RelPost : Cadence<RelPost>() {
    override val table = "ff_rel_posts"
    override val timestamps = false
    override val fillable = setOf("author_id", "title")

    fun author(): BelongsTo<RelAuthor> = belongsTo(RelAuthor::class.java, "author_id", "id")
}

class RelProfile : Cadence<RelProfile>() {
    override val table = "ff_rel_profiles"
    override val timestamps = false
    override val fillable = setOf("author_id", "city")
}
