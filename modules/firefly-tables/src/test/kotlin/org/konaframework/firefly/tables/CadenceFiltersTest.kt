package org.konaframework.firefly.tables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.SoftDeletes
import org.konaframework.cadence.relations.BelongsTo
import org.konaframework.cadence.relations.HasMany
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException

class CadenceFiltersTest {
    private lateinit var ada: FilterAuthor
    private lateinit var bea: FilterAuthor

    @BeforeEach
    fun seed() {
        val url = "jdbc:h2:mem:fff${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
        val connection = database.connection()
        connection.statement("create table ff_filter_authors (id bigint auto_increment primary key, name varchar(100))")
        connection.statement(
            "create table ff_filter_books (id bigint auto_increment primary key, author_id bigint, title varchar(100), " +
                "status varchar(20), price int, featured boolean, published_at date, deleted_at timestamp)",
        )
        connection.statement("create table ff_filter_statuses (code varchar(20) primary key, label varchar(50))")
        database.setAsGlobal()
        val statuses = CadenceRecordStore(FilterStatus::class.java)
        statuses.save(statuses.fill(statuses.newRecord(), mapOf("code" to "open", "label" to "Open")))
        val authors = CadenceRecordStore(FilterAuthor::class.java)
        ada = authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Ada")))
        bea = authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Bea")))
        authors.save(authors.fill(authors.newRecord(), mapOf("name" to "Cy")))
        val books = CadenceRecordStore(FilterBook::class.java)
        fun book(values: Map<String, Any?>): FilterBook = books.save(books.fill(books.newRecord(), values))
        book(mapOf("author_id" to ada.getKey(), "title" to "Engines", "status" to "open", "price" to 10, "featured" to true, "published_at" to "2024-01-15"))
        book(mapOf("author_id" to ada.getKey(), "title" to "Notes", "status" to "draft", "price" to 250, "featured" to false, "published_at" to "2024-03-02"))
        book(mapOf("author_id" to bea.getKey(), "title" to "Orbits", "status" to "open", "price" to 40, "featured" to false, "published_at" to null))
        book(mapOf("author_id" to null, "title" to "Orphan", "status" to "shut", "price" to 5, "featured" to true, "published_at" to "2023-07-09"))
        val trashed = book(mapOf("author_id" to bea.getKey(), "title" to "Lost", "status" to "open", "price" to 99, "featured" to true, "published_at" to "2024-01-20"))
        trashed.delete()
    }

    private fun titles(table: Table<*, *>): List<String> =
        table.load().records.map { (it as FilterBook).getAttribute("title").toString() }.sorted()

    private fun books(vararg filters: Filter): Table<FilterBook, ModelBuilder<FilterBook>> =
        Table.cadence(FilterBook::class.java).columns(TextColumn.make("title")).perPage(50).filters(*filters)

    @Test
    fun selectFiltersConstrainSql() {
        val table = books(
            SelectFilter.make("status").options(mapOf("open" to "Open", "draft" to "Draft", "shut" to "Shut")),
        )
        assertEquals(listOf("Engines", "Notes", "Orbits", "Orphan"), titles(table))
        table.filter("status", "open")
        assertEquals(listOf("Engines", "Orbits"), titles(table))

        val multi = books(SelectFilter.make("status").multiple().options(mapOf("open" to "Open", "draft" to "Draft")))
        assertTrue(multi.applyFilterQuery(mapOf("filters" to "1", "filters.status" to "draft,shut")))
        assertEquals(listOf("Notes", "Orphan"), titles(multi))
        assertEquals(mapOf("filters" to "1", "filters.status" to "draft,shut"), multi.filterQuery())

        val attributed = books(SelectFilter.make("state").attribute("status").options(mapOf("shut" to "Shut")))
        attributed.filter("state", "shut")
        assertEquals(listOf("Orphan"), titles(attributed))
    }

    @Test
    fun relationshipSelectFilters() {
        val byAuthor = books(SelectFilter.make("author").relationship("author", "name").searchable().preload())
        byAuthor.filter("author", ada.getKey().toString())
        assertEquals(listOf("Engines", "Notes"), titles(byAuthor))
        assertEquals(mapOf(ada.getKey().toString() to "Ada", bea.getKey().toString() to "Bea"), byAuthor.filterOptions("author", "a").filterValues { it != "Cy" })
        assertEquals(listOf("Bea"), byAuthor.filterOptions("author", "Be").values.toList())
        assertEquals("Author: Ada", byAuthor.filterIndicators().single().label)
        val html = byAuthor.render()
        assertTrue(html.contains("data-ff-relationship=\"author\""), html)
        assertTrue(html.contains("name=\"filters.author\""))

        val many = books(SelectFilter.make("author").relationship("author", "name").multiple())
        many.filter("author", listOf(ada.getKey().toString(), bea.getKey().toString()))
        assertEquals(listOf("Engines", "Notes", "Orbits"), titles(many))
        assertEquals("Author: Ada, Bea", many.filterIndicators().single().label)

        val empty = books(SelectFilter.make("author").relationship("author", "name").hasEmptyOption().emptyRelationshipOptionLabel("No author"))
        assertEquals("No author", empty.filterOptions("author", "")[SelectFilter.EMPTY])
        empty.filter("author", SelectFilter.EMPTY)
        assertEquals(listOf("Orphan"), titles(empty))
        empty.filter("author", listOf(SelectFilter.EMPTY, bea.getKey().toString()))
        assertEquals(listOf("Orbits", "Orphan"), titles(empty))

        val scoped = books(
            SelectFilter.make("author").relationship("author", "name") { query ->
                @Suppress("UNCHECKED_CAST")
                (query as ModelBuilder<FilterAuthor>).where("name", "!=", "Ada")
            },
        )
        assertEquals(listOf("Bea", "Cy"), scoped.filterOptions("author", "").values.toList())

        val authors = Table.cadence(FilterAuthor::class.java).columns(TextColumn.make("name")).filters(
            SelectFilter.make("books").relationship("books", "title").multiple(),
        )
        val orbitsKey = CadenceRecordStore(FilterBook::class.java).let { store ->
            store.list(store.query().also { store.constrain(it, "title", "=", "Orbits") }).single().getKey().toString()
        }
        authors.filter("books", orbitsKey)
        assertEquals(listOf("Bea"), authors.load().records.map { it.getAttribute("name") })
        val noBooks = Table.cadence(FilterAuthor::class.java).columns(TextColumn.make("name")).filters(
            SelectFilter.make("books").relationship("books", "title").hasEmptyOption(),
        )
        noBooks.filter("books", SelectFilter.EMPTY)
        assertEquals(listOf("Cy"), noBooks.load().records.map { it.getAttribute("name") })
    }

    @Test
    fun ternaryAndTrashedFilters() {
        val featured = books(TernaryFilter.make("featured").trueLabel("Featured").falseLabel("Not featured"))
        featured.filter("featured", "true")
        assertEquals(listOf("Engines", "Orphan"), titles(featured))
        assertEquals("Featured: Featured", featured.filterIndicators().single().label)
        featured.filter("featured", "false")
        assertEquals(listOf("Notes", "Orbits"), titles(featured))

        val published = books(TernaryFilter.make("published").attribute("published_at").nullable())
        published.filter("published", "true")
        assertEquals(listOf("Engines", "Notes", "Orphan"), titles(published))
        published.filter("published", "false")
        assertEquals(listOf("Orbits"), titles(published))

        val custom = books(
            TernaryFilter.make("pricey").queries(
                { query, _ -> (query as ModelBuilder<*>).where("price", ">=", 100) },
                { query, _ -> (query as ModelBuilder<*>).where("price", "<", 100) },
                { query, _ -> (query as ModelBuilder<*>).where("status", "!=", "shut") },
            ),
        )
        assertEquals(listOf("Engines", "Notes", "Orbits"), titles(custom))
        custom.filter("pricey", "true")
        assertEquals(listOf("Notes"), titles(custom))

        val trash = books(TrashedFilter.make())
        assertEquals(4, titles(trash).size)
        trash.filter("trashed", "with")
        assertEquals(5, titles(trash).size)
        trash.filter("trashed", "only")
        assertEquals(listOf("Lost"), titles(trash))
        assertEquals("Deleted records: Only deleted records", trash.filterIndicators().single().label)
        trash.filter("trashed", "without")
        assertEquals(4, titles(trash).size)
        assertTrue(trash.filterIndicators().isEmpty())
    }

    @Test
    fun eachFilterQueryIsItsOwnGroup() {
        val table = books(
            Filter.make("cheapOrFeatured").queryUsing { query, _ ->
                (query as ModelBuilder<*>).where("price", "<", 20).orWhere("featured", true)
            }.default(),
            SelectFilter.make("status").options(mapOf("open" to "Open")).default("open"),
        )
        // (price < 20 or featured) and (status = open) — the orWhere never escapes its group.
        assertEquals(listOf("Engines"), titles(table))
    }

    @Test
    fun schemaFiltersReadFormData() {
        val published = Filter.make("published").schema(FilterProbe("from"), FilterProbe("until"))
            .queryUsing { query, data ->
                val builder = query as ModelBuilder<*>
                data.string("from")?.let { builder.whereDate("published_at", ">=", it) }
                data.string("until")?.let { builder.whereDate("published_at", "<=", it) }
            }
            .indicateUsing { data ->
                listOfNotNull(
                    data.string("from")?.let { FilterIndicator.make("From $it").removeField("from") },
                    data.string("until")?.let { FilterIndicator.make("Until $it").removeField("until") },
                )
            }
        val table = books(published)
        table.applyFilterQuery(mapOf("filters" to "1", "filters.published.from" to "2024-01-01", "filters.published.until" to "2024-02-01"))
        assertEquals(listOf("Engines"), titles(table))
        assertEquals(listOf("From 2024-01-01", "Until 2024-02-01"), table.filterIndicators().map { it.label })
        table.removeFilterFields("published", "until")
        assertEquals(listOf("Engines", "Notes"), titles(table))
        assertEquals(mapOf("from" to "2024-01-01"), table.filterData("published").toMap().filterValues { it != null })
        val html = table.render()
        assertTrue(html.contains("name=\"filters.published.from\" value=\"2024-01-01\""), html)
        assertTrue(html.contains("name=\"filters.published.until\" value=\"\""), html)
    }

    @Test
    fun baseQueryAndResolvingRecords() {
        val table = books(
            Filter.make("visible").baseQuery { query, _ -> (query as ModelBuilder<*>).where("status", "!=", "shut") },
            SelectFilter.make("status").options(mapOf("open" to "Open", "draft" to "Draft")).default("open").excludeWhenResolvingRecord(),
        )
        assertEquals(listOf("Engines", "Orbits"), titles(table))
        val store = CadenceRecordStore(FilterBook::class.java)
        fun key(title: String): String = store.list(store.query().also { store.constrain(it, "title", "=", title) }).single().getKey().toString()
        assertNotNull(table.resolveRecord(key("Notes")))
        assertNull(table.resolveRecord(key("Orphan")))
    }

    @Test
    fun cadenceStoreEdges() {
        val store = CadenceRecordStore(FilterBook::class.java)
        fun count(block: (ModelBuilder<FilterBook>) -> Unit): Int = store.list(store.query().also(block)).size
        assertEquals(1, count { store.constrainDate(it, "published_at", "day", "=", 15) })
        assertThrows(FireflyException::class.java) { store.constrainDate(store.query(), "published_at", "week", "=", 1) }
        assertEquals(2, count { store.constrain(it, "status", "in", "open") })
        assertEquals(0, count { store.constrain(it, "status", "in", null) })
        assertEquals(2, count { store.constrain(it, "status", "not in", "open") })
        assertEquals(2, count { store.constrainRelationship(it, "statusRow", listOf("open"), false) })
        val authors = CadenceRecordStore(FilterAuthor::class.java)
        authors.save(authors.fill(authors.newRecord(), mapOf("name" to null)))
        val options = authors.relationshipOptions("books", "title", null, null, null, null)
        assertEquals(listOf("Engines", "Notes", "Orbits", "Orphan"), options.values.toList())
        val books = Table.cadence(FilterAuthor::class.java).columns(TextColumn.make("name")).filters(SelectFilter.make("books").relationship("books", "title"))
        assertEquals(listOf("Notes"), books.filterOptions("books", "ot").values.toList())
        val untitled = store.relationshipOptions("author", "name", null, null, null, null)
        assertEquals(listOf("", "Ada", "Bea", "Cy"), untitled.values.toList())
    }

    @Test
    fun queryBuilderConstraintsRunAsSql() {
        val builder = QueryBuilderFilter.make("query").constraints(
            TextConstraint.make("title"),
            NumberConstraint.make("price"),
            DateConstraint.make("published_at").label("Published"),
            BooleanConstraint.make("featured"),
            SelectConstraint.make("status").options(mapOf("open" to "Open", "draft" to "Draft", "shut" to "Shut")).multiple(),
            RelationshipConstraint.make("author").titleAttribute("name"),
        )
        val table = books(builder)
        fun run(rules: String): List<String> {
            table.applyFilterQuery(mapOf("filters" to "1", "filters.query" to rules))
            return titles(table)
        }
        assertEquals(listOf("Engines", "Orphan"), run("title:contains:n"))
        assertEquals(listOf("Notes", "Orbits", "Orphan"), run("title:!contains:in"))
        assertEquals(listOf("Orbits", "Orphan"), run("title:startsWith:Or"))
        assertEquals(listOf("Engines", "Notes"), run("title:!startsWith:Or"))
        assertEquals(listOf("Engines", "Notes"), run("title:endsWith:s;title:!startsWith:Or"))
        assertEquals(listOf("Engines", "Notes", "Orphan"), run("title:!endsWith:ts"))
        assertEquals(listOf("Notes"), run("title:equals:Notes"))
        assertEquals(listOf("Engines", "Orbits", "Orphan"), run("title:!equals:Notes"))
        assertEquals(4, run("title:isFilled:").size)
        assertEquals(emptyList<String>(), run("title:!isFilled:"))
        assertEquals(listOf("Notes", "Orbits"), run("price:isMin:40"))
        assertEquals(listOf("Engines", "Orphan"), run("price:!isMin:40"))
        assertEquals(listOf("Engines", "Orphan"), run("price:isMax:10"))
        assertEquals(listOf("Notes", "Orbits"), run("price:!isMax:10"))
        assertEquals(listOf("Orbits"), run("price:equals:40"))
        assertEquals(3, run("price:!equals:40").size)
        assertEquals(4, run("price:isFilled:").size)
        assertEquals(0, run("price:!isFilled:").size)
        assertEquals(listOf("Notes", "Orbits"), run("price:isMin:39.5"))
        assertEquals(listOf("Notes"), run("published_at:isAfter:2024-02-01"))
        assertEquals(listOf("Engines", "Orphan"), run("published_at:!isAfter:2024-02-01"))
        assertEquals(listOf("Orphan"), run("published_at:isBefore:2024-01-01"))
        assertEquals(listOf("Engines", "Notes"), run("published_at:!isBefore:2024-01-01"))
        assertEquals(listOf("Engines"), run("published_at:isDate:2024-01-15"))
        assertEquals(listOf("Notes", "Orphan"), run("published_at:!isDate:2024-01-15"))
        assertEquals(listOf("Notes"), run("published_at:isMonth:2024-03-01"))
        assertEquals(listOf("Orphan"), run("published_at:isMonth:7"))
        assertEquals(listOf("Engines", "Orphan"), run("published_at:!isMonth:3"))
        assertEquals(listOf("Orphan"), run("published_at:isYear:2023"))
        assertEquals(listOf("Engines", "Notes"), run("published_at:!isYear:2023-01-01"))
        assertEquals(listOf("Orbits"), run("published_at:!isFilled:"))
        assertEquals(3, run("published_at:isFilled:").size)
        assertEquals(listOf("Engines", "Orphan"), run("featured:isTrue:"))
        assertEquals(listOf("Notes", "Orbits"), run("featured:!isTrue:"))
        assertEquals(listOf("Engines", "Notes", "Orbits"), run("status:is:open,draft"))
        assertEquals(listOf("Orphan"), run("status:!is:open,draft"))
        assertEquals(4, run("status:isFilled:").size)
        assertEquals(0, run("status:!isFilled:").size)
        assertEquals(listOf("Engines", "Notes"), run("author:isRelatedTo:${ada.getKey()}"))
        assertEquals(listOf("Orbits", "Orphan"), run("author:!isRelatedTo:${ada.getKey()}"))
        assertEquals(listOf("Engines", "Notes", "Orbits"), run("author:has:"))
        assertEquals(listOf("Orphan"), run("author:!has:"))
        // (featured and price <= 10) or (status is draft)
        assertEquals(listOf("Engines", "Notes", "Orphan"), run("featured:isTrue:;price:isMax:10|status:is:draft"))
        assertEquals(
            listOf("Featured is true", "Price is at most 10", "Status is Draft"),
            table.filterIndicators().map { it.label },
        )
        run("author:isRelatedTo:${ada.getKey()},${bea.getKey()}")
        assertEquals("Author is Ada, Bea", table.filterIndicators().single().label)
        run("published_at:isAfter:2024-01-01")
        assertEquals("Published is after 2024-01-01", table.filterIndicators().single().label)
        // Unusable values drop the rule instead of failing the page.
        assertEquals(4, run("published_at:isYear:soon").size)
        assertTrue(table.filterIndicators().isEmpty())
        assertEquals(4, run("published_at:isMonth:13;published_at:isMonth:soon;published_at:isAfter:soon;price:isMin:lots").size)
        assertEquals(4, run("ghost:is:x;title:nope:x;title|:;title:contains:").size)
        val html = table.render()
        assertTrue(html.contains("data-ff-qb"), html)
        assertTrue(html.contains("data-ff-qb-template=\"author\""))
        assertTrue(html.contains(">Ada<"))
    }
}

class FilterAuthor : Cadence<FilterAuthor>() {
    override val table = "ff_filter_authors"
    override val timestamps = false
    override val fillable = setOf("name")

    fun books(): HasMany<FilterBook> = hasMany(FilterBook::class.java, "author_id", "id")
}

class FilterBook : Cadence<FilterBook>(), SoftDeletes {
    override val table = "ff_filter_books"
    override val timestamps = false
    override val fillable = setOf("author_id", "title", "status", "price", "featured", "published_at")

    fun author(): BelongsTo<FilterAuthor> = belongsTo(FilterAuthor::class.java, "author_id", "id")

    fun statusRow(): BelongsTo<FilterStatus> = belongsTo(FilterStatus::class.java, "status", "code")
}

class FilterStatus : Cadence<FilterStatus>() {
    override val table = "ff_filter_statuses"
    override val timestamps = false
    override val primaryKey = "code"
    override val incrementing = false
    override val fillable = setOf("code", "label")
}

/** Minimal schema input (forms depend on tables, so table tests stay below them). */
class FilterProbe(name: String) : Component(name) {
    override fun renderBody(schema: Schema): String {
        val path = absolutePath().orEmpty()
        return """<input name="$path" value="${schema.get(path) ?: ""}">"""
    }
}
