package org.konaframework.cadence

import org.konaframework.cadence.relations.HasMany
import org.konaframework.cadence.schema.Schema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NestAuthor : Cadence<NestAuthor>() {
    override val table = "nest_authors"
    override val timestamps = false
    override val fillable = setOf("name", "active")

    fun books(): HasMany<NestBook> = hasMany(NestBook::class.java, "author_id", "id")

    companion object : CadenceCompanion<NestAuthor>(NestAuthor::class.java)
}

class NestBook : Cadence<NestBook>() {
    override val table = "nest_books"
    override val timestamps = false
    override val fillable = setOf("title", "author_id")

    companion object : CadenceCompanion<NestBook>(NestBook::class.java)
}

class WhereNestedTest {
    @BeforeEach
    fun setUp() {
        TestDb.create()
        Database.global().withConnection { connection ->
            val schema = Schema(connection)
            schema.create("nest_authors") {
                id()
                string("name")
                boolean("active")
            }
            schema.create("nest_books") {
                id()
                foreignId("author_id")
                string("title")
            }
        }
        val ada = NestAuthor.create(mapOf("name" to "Ada", "active" to true))
        NestAuthor.create(mapOf("name" to "Bob", "active" to false))
        NestAuthor.create(mapOf("name" to "Cy", "active" to false))
        NestBook.create(mapOf("title" to "Engines", "author_id" to ada.getKey()))
    }

    @AfterEach
    fun tearDown() {
        Database.clearGlobal()
    }

    @Test
    fun nestedGroupsKeepOrInsideParentheses() {
        val query = NestAuthor.query()
            .where("active", false)
            .whereNested { group -> group.where("name", "Bob").orWhere("name", "Ada") }
        assertTrue(query.toSql().contains("and (\"name\" = ? or \"name\" = ?)"), query.toSql())
        assertEquals(listOf("Bob"), query.get().map { it.getAttribute("name") })
    }

    @Test
    fun nestedGroupsSupportRelationConstraints() {
        val found = NestAuthor.query()
            .whereNested { group -> group.whereHas("books") { books -> books.where("title", "Engines") } }
            .get()
        assertEquals(listOf("Ada"), found.map { it.getAttribute("name") })
        val either = NestAuthor.query()
            .where("name", "Cy")
            .orWhereNested { group -> group.whereHas("books").where("active", true) }
            .orderBy("name")
            .get()
        assertEquals(listOf("Ada", "Cy"), either.map { it.getAttribute("name") })
    }

    @Test
    fun emptyGroupsAddNoClause() {
        val query = NestAuthor.query().whereNested { }.orWhereNested { }
        assertEquals(3, query.count())
    }
}
