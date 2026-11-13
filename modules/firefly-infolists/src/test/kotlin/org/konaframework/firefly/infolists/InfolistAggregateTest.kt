package org.konaframework.firefly.infolists

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceCompanion
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.firefly.support.FireflyException

class InfolistAuthor : Cadence<InfolistAuthor>() {
    override val table = "ff_inf_authors"
    override val timestamps = false
    override val fillable = setOf("name")

    fun books() = hasMany(InfolistBook::class.java, "author_id")

    companion object : CadenceCompanion<InfolistAuthor>(InfolistAuthor::class.java)
}

class InfolistBook : Cadence<InfolistBook>() {
    override val table = "ff_inf_books"
    override val timestamps = false
    override val fillable = setOf("author_id", "title", "votes")

    companion object : CadenceCompanion<InfolistBook>(InfolistBook::class.java)
}

class InfolistAggregateTest {
    @AfterEach
    fun clearDatabase() {
        Database.clearGlobal()
    }

    @Test
    fun mapRecordAggregatesRelatedRows() {
        val rows = listOf(
            mapOf("votes" to 4),
            mapOf("votes" to -1),
            mapOf("votes" to 9),
            null,
            mapOf("title" to "skip"),
            mapOf("votes" to "2"),
            mapOf("votes" to "nope"),
        )
        val html = render(
            mapOf("books" to rows),
            TextEntry.make("books_count").counts(" books "),
            TextEntry.make("books_avg_votes").avg("books", " votes ").numeric(1),
            TextEntry.make("books_min_votes").min("books", "votes"),
            TextEntry.make("books_max_votes").max("books", "votes"),
            TextEntry.make("books_sum_votes").sum("books", "votes").numeric(0),
        )
        assertTrue(html.contains("ff-value\">7<"), html)
        assertTrue(html.contains("ff-value\">3.5<"), html)
        assertTrue(html.contains("ff-value\">-1<"), html)
        assertTrue(html.contains("ff-value\">9<"), html)
        assertTrue(html.contains("ff-value\">14<"), html)
    }

    @Test
    fun mapShapesAndStoredAttributes() {
        val book = InfolistBook()
        book.setRawAttribute("votes", 4)
        val single = render(
            mapOf("books" to mapOf("votes" to 6)),
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("books_max_votes").max("books", "votes"),
        )
        assertTrue(single.contains("ff-value\">1<"), single)
        assertTrue(single.contains("ff-value\">6<"), single)

        val array = render(
            mapOf("books" to arrayOf(mapOf("votes" to 1), mapOf("votes" to 2))),
            TextEntry.make("books_avg_votes").avg("books", "votes").numeric(1),
        )
        assertTrue(array.contains("ff-value\">1.5<"), array)

        val model = render(
            mapOf("books" to book),
            TextEntry.make("books_sum_votes").sum("books", "votes"),
        )
        assertTrue(model.contains("ff-value\">4<"), model)

        val stored = render(
            mapOf("books_count" to 4, "books_avg_votes" to 1, "books_min_votes" to 1, "books_max_votes" to 1, "books_sum_votes" to 1),
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("books_avg_votes").avg("books", "votes"),
            TextEntry.make("books_min_votes").min("books", "votes"),
            TextEntry.make("books_max_votes").max("books", "votes"),
            TextEntry.make("books_sum_votes").sum("books", "votes"),
        )
        assertTrue(stored.contains("ff-value\">4<"), stored)

        val named = render(
            mapOf("total" to 9),
            TextEntry.make("total").counts("books"),
        )
        assertTrue(named.contains("ff-value\">9<"), named)

        val ignored = render(
            mapOf("books" to "nope", "books_count" to 6),
            TextEntry.make("books_count").counts("books"),
        )
        assertTrue(ignored.contains("ff-value\">6<"), ignored)

        val missing = render(
            mapOf("books" to "nope"),
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("books_sum_votes").sum("books", "votes"),
            TextEntry.make("books_avg_votes").avg("books", "votes").placeholder("none"),
        )
        assertTrue(missing.contains("ff-value\">0<"), missing)
        assertTrue(missing.contains("ff-placeholder\">none<"), missing)

        val emptyAvg = render(
            mapOf("books" to emptyList<Any>()),
            TextEntry.make("books_avg_votes").avg("books", "votes").placeholder("empty"),
            TextEntry.make("books_sum_votes").sum("books", "votes"),
            TextEntry.make("books_min_votes").min("books", "votes").placeholder("low"),
            TextEntry.make("books_max_votes").max("books", "votes").placeholder("high"),
        )
        assertTrue(emptyAvg.contains("ff-placeholder\">empty<"), emptyAvg)
        assertTrue(emptyAvg.contains("ff-placeholder\">low<"), emptyAvg)
        assertTrue(emptyAvg.contains("ff-placeholder\">high<"), emptyAvg)
        assertTrue(emptyAvg.contains("ff-value\">0<"), emptyAvg)

        val nonsense = render(
            mapOf("books" to listOf(mapOf("votes" to "nope"))),
            TextEntry.make("books_sum_votes").sum("books", "votes"),
            TextEntry.make("books_avg_votes").avg("books", "votes").placeholder("nan"),
        )
        assertTrue(nonsense.contains("ff-value\">0<"), nonsense)
        assertTrue(nonsense.contains("ff-placeholder\">nan<"), nonsense)
    }

    @Test
    fun explicitStateWinsAndBlankNamesThrow() {
        val html = render(
            mapOf("books" to listOf(mapOf("votes" to 1))),
            TextEntry.make("books_count").counts("books").state("9"),
        )
        assertTrue(html.contains("ff-value\">9<"), html)

        val none = Infolist.make().components(
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("books_sum_votes").sum("books", "votes"),
            TextEntry.make("books_avg_votes").avg("books", "votes").placeholder("avg"),
            TextEntry.make("books_min_votes").min("books", "votes").placeholder("min"),
            TextEntry.make("books_max_votes").max("books", "votes").placeholder("max"),
            TextEntry.make("total").counts("books"),
        ).render()
        assertTrue(none.contains("ff-value\">0<"), none)
        assertTrue(none.contains("ff-placeholder\">avg<"), none)
        assertTrue(none.contains("ff-placeholder\">min<"), none)
        assertTrue(none.contains("ff-placeholder\">max<"), none)
        val text = Infolist.make().record("ada").components(
            TextEntry.make("books_sum_votes").sum("books", "votes"),
        ).render()
        assertTrue(text.contains("ff-value\">0<"), text)
        val absent = render(emptyMap<String, Any?>(), TextEntry.make("total").counts("books"))
        assertTrue(absent.contains("ff-value\">0<"), absent)
        val cleared = render(
            mapOf("books" to null),
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("books_avg_votes").avg("books", "votes").placeholder("gone"),
        )
        assertTrue(cleared.contains("ff-value\">0<"), cleared)
        assertTrue(cleared.contains("ff-placeholder\">gone<"), cleared)

        assertThrows(FireflyException::class.java) { TextEntry.make("books_count").counts(" ") }
        assertThrows(FireflyException::class.java) { TextEntry.make("books_avg_votes").avg(" ", "votes") }
        assertThrows(FireflyException::class.java) { TextEntry.make("books_avg_votes").avg("books", " ") }
        assertThrows(FireflyException::class.java) { TextEntry.make("books_min_votes").min("books", " ") }
        assertThrows(FireflyException::class.java) { TextEntry.make("books_max_votes").max("books", " ") }
        assertThrows(FireflyException::class.java) { TextEntry.make("books_sum_votes").sum("books", " ") }

        assertEquals(10, compactNumber(10.0))
        assertEquals(1.5, compactNumber(1.5))
    }

    @Test
    fun repeatableItemBeatsTheOuterRecord() {
        val outer = mapOf("books" to listOf(mapOf("votes" to 1)))
        ItemFrame.use(mapOf("books" to listOf(mapOf("votes" to 4), mapOf("votes" to 6)))) {
            val html = Infolist.make().record(outer).components(
                TextEntry.make("books_avg_votes").avg("books", "votes"),
            ).render()
            assertTrue(html.contains("ff-value\">5<"), html)
        }
    }

    @Test
    fun cadenceModelUsesLoadedRowsOrStoredAttributes() {
        val book = InfolistBook()
        book.setRawAttribute("votes", 4)
        val loaded = InfolistAuthor()
        loaded.setRelation("books", listOf(book, InfolistBook().apply { setRawAttribute("votes", 8) }))
        val loadedHtml = render(
            loaded,
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("books_sum_votes").sum("books", "votes"),
        )
        assertTrue(loadedHtml.contains("ff-value\">2<"), loadedHtml)
        assertTrue(loadedHtml.contains("ff-value\">12<"), loadedHtml)

        val skipped = InfolistAuthor()
        skipped.setRelation("books", "nope")
        skipped.setRawAttribute("books_count", 6)
        val skippedHtml = render(skipped, TextEntry.make("books_count").counts("books"))
        assertTrue(skippedHtml.contains("ff-value\">6<"), skippedHtml)

        val stored = InfolistAuthor()
        stored.setRawAttribute("books_count", 4)
        val storedHtml = render(stored, TextEntry.make("books_count").counts("books"))
        assertTrue(storedHtml.contains("ff-value\">4<"), storedHtml)

        val named = InfolistAuthor()
        named.setRawAttribute("total", 9)
        val namedHtml = render(named, TextEntry.make("total").counts("books"))
        assertTrue(namedHtml.contains("ff-value\">9<"), namedHtml)

        val fresh = render(InfolistAuthor(), TextEntry.make("books_count").counts("books"))
        assertTrue(fresh.contains("ff-value\">0<"), fresh)
        val freshAvg = render(
            InfolistAuthor(),
            TextEntry.make("books_avg_votes").avg("books", "votes").placeholder("none"),
        )
        assertTrue(freshAvg.contains("ff-placeholder\">none<"), freshAvg)
    }

    @Test
    fun savedCadenceModelLoadsAggregatesFromTheDatabase() {
        val url = "jdbc:h2:mem:ffinf${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
        database.connection().statement(
            "create table ff_inf_authors (id bigint auto_increment primary key, name varchar(100))",
        )
        database.connection().statement(
            "create table ff_inf_books (id bigint auto_increment primary key, author_id bigint, title varchar(100), votes int)",
        )
        database.setAsGlobal()

        val ada = InfolistAuthor.create(mapOf("name" to "Ada"))
        InfolistBook.create(mapOf("author_id" to ada.getKey(), "title" to "A", "votes" to 3))
        InfolistBook.create(mapOf("author_id" to ada.getKey(), "title" to "B", "votes" to 7))
        val aliased = render(InfolistAuthor.find(ada.getKey())!!, TextEntry.make("total").counts("books"))
        assertTrue(aliased.contains("ff-value\">2<"), aliased)
        val html = render(
            ada,
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("total").counts("books"),
            TextEntry.make("books_avg_votes").avg("books", "votes").numeric(0),
            TextEntry.make("books_min_votes").min("books", "votes").numeric(0),
            TextEntry.make("books_sum_votes").sum("books", "votes").numeric(0),
            TextEntry.make("peak").max("books", "votes").numeric(0),
        )
        assertTrue(html.contains("ff-value\">2<"), html)
        assertTrue(html.contains("ff-value\">5<"), html)
        assertTrue(html.contains("ff-value\">3<"), html)
        assertTrue(html.contains("ff-value\">10<"), html)
        assertTrue(html.contains("ff-value\">7<"), html)

        val empty = InfolistAuthor.create(mapOf("name" to "Bea"))
        val quiet = render(
            empty,
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("books_avg_votes").avg("books", "votes").placeholder("none"),
            TextEntry.make("books_sum_votes").sum("books", "votes").numeric(0),
        )
        assertTrue(quiet.contains("ff-value\">0<"), quiet)
        assertTrue(quiet.contains("ff-placeholder\">none<"), quiet)
    }
}

private fun render(record: Any?, vararg entries: TextEntry): String =
    Infolist.make().record(record).components(*entries).render()
