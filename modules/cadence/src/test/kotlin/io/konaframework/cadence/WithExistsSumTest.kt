package io.konaframework.cadence

import io.konaframework.cadence.relations.HasMany
import io.konaframework.cadence.schema.Schema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SumAuthor : Cadence<SumAuthor>() {
    override val table = "authors"
    override val timestamps = false
    override val fillable = setOf("name")

    fun books(): HasMany<SumBook> = hasMany(SumBook::class.java, "author_id", "id")

    companion object : CadenceCompanion<SumAuthor>(SumAuthor::class.java)
}

class SumBook : Cadence<SumBook>() {
    override val table = "books"
    override val timestamps = false
    override val fillable = setOf("title", "author_id", "votes")

    companion object : CadenceCompanion<SumBook>(SumBook::class.java)
}

class WithExistsSumTest {
    @BeforeEach
    fun setUp() {
        TestDb.create()
        Database.global().withConnection { connection ->
            val schema = Schema(connection)
            schema.create("authors") {
                id()
                string("name")
            }
            schema.create("books") {
                id()
                foreignId("author_id")
                string("title")
                integer("votes")
            }
        }
        val ada = SumAuthor.create(mapOf("name" to "Ada"))
        val bob = SumAuthor.create(mapOf("name" to "Bob"))
        SumBook.create(mapOf("title" to "A1", "author_id" to ada.getKey(), "votes" to 3))
        SumBook.create(mapOf("title" to "A2", "author_id" to ada.getKey(), "votes" to 7))
        SumBook.create(mapOf("title" to "B1", "author_id" to bob.getKey(), "votes" to 1))
    }

    @AfterEach
    fun tearDown() {
        Database.clearGlobal()
    }

    @Test
    fun withExistsAndWithSumOnQuery() {
        val rows = SumAuthor.withExists("books").withSum("books", "votes").orderBy("id").get()
        assertEquals(2, rows.size)
        assertEquals(1, (rows[0].getRawAttribute("books_exists") as Number).toInt())
        assertEquals(10L, (rows[0].getRawAttribute("books_sum_votes") as Number).toLong())
        assertEquals(1L, (rows[1].getRawAttribute("books_sum_votes") as Number).toLong())

        val aliased = SumAuthor.withExists("books as has_books")
            .withSum("books as total_votes:votes")
            .where("name", "Ada")
            .first()!!
        assertEquals(1, (aliased.getRawAttribute("has_books") as Number).toInt())
        assertEquals(10L, (aliased.getRawAttribute("total_votes") as Number).toLong())
    }

    @Test
    fun loadExistsAndLoadSum() {
        val ada = SumAuthor.where("name", "Ada").first()!!
        ada.loadExists("books")
        ada.loadSum("books:votes")
        assertEquals(1, (ada.getRawAttribute("books_exists") as Number).toInt())
        assertEquals(10L, (ada.getRawAttribute("books_sum_votes") as Number).toLong())

        val empty = SumAuthor.create(mapOf("name" to "Cid"))
        empty.loadExists("books")
        empty.loadSum("books as none:votes")
        assertEquals(0, (empty.getRawAttribute("books_exists") as Number).toInt())
        assertEquals(0L, (empty.getRawAttribute("none") as Number).toLong())
        assertTrue(empty.getRawAttribute("books_exists") as Number == 0)

        // Already-selected columns + missing row for load* elvis branches.
        assertEquals(
            1,
            (SumAuthor.query().select("authors.*").withExists("books").where("name", "Ada").first()!!
                .getRawAttribute("books_exists") as Number).toInt(),
        )
        val ghost = SumAuthor().newFromBuilder(mapOf("id" to 9999L, "name" to "Ghost"))
        assertSame(ghost, ghost.loadExists("books"))
        assertSame(ghost, ghost.loadSum("books:votes"))
        assertSame(ghost, ghost.loadCount("books"))
        assertSame(ada, ada.loadExists())
        ada.loadExists("books as has_books")
        assertEquals(1, (ada.getRawAttribute("has_books") as Number).toInt())
    }

    @Test
    fun sumSpecRequiresColumn() {
        assertThrows(IllegalArgumentException::class.java) { SumAuthor.withSum("books") }
        assertThrows(IllegalArgumentException::class.java) { SumAuthor.withSum("books:") }
        assertEquals(10L, (SumAuthor.withSum("books", "votes").where("name", "Ada").first()!!.getRawAttribute("books_sum_votes") as Number).toLong())
    }
}
