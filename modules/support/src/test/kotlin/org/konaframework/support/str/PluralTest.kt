package org.konaframework.support.str

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PluralTest {
    private fun assertPlural(expected: String, word: String) = assertEquals(expected, Str.plural(word), word)

    @Test
    fun regularNouns() {
        assertPlural("users", "user")
        assertPlural("posts", "post")
        assertPlural("days", "day")
        assertPlural("keys", "key")
        assertPlural("ys", "y")
        assertPlural("files", "file")
    }

    @Test
    fun consonantYBecomesIes() {
        assertPlural("currencies", "currency")
        assertPlural("companies", "company")
        assertPlural("categories", "category")
        assertPlural("queries", "query")
        assertPlural("soliloquies", "soliloquy")
    }

    @Test
    fun sibilantsAndSuffixRules() {
        assertPlural("addresses", "address")
        assertPlural("boxes", "box")
        assertPlural("taxes", "tax")
        assertPlural("branches", "branch")
        assertPlural("wishes", "wish")
        assertPlural("quizzes", "quiz")
        assertPlural("statuses", "status")
        assertPlural("buses", "bus")
        assertPlural("aliases", "alias")
        assertPlural("indices", "index")
        assertPlural("matrices", "matrix")
        assertPlural("mice", "mouse")
        assertPlural("houses", "house")
        assertPlural("hives", "hive")
        assertPlural("lives", "life")
        assertPlural("wolves", "wolf")
        assertPlural("roofs", "roof")
        assertPlural("analyses", "analysis")
        assertPlural("axes", "axis")
        assertPlural("taxa", "taxon")
        assertPlural("women", "woman")
        assertPlural("potatoes", "potato")
        assertPlural("photos", "photo")
        assertPlural("cacti", "cactus")
    }

    @Test
    fun irregularUncountableAndAlreadyPlural() {
        assertPlural("people", "person")
        assertPlural("children", "child")
        assertPlural("oxen", "ox")
        assertPlural("humans", "human")
        assertPlural("gases", "gas")
        assertPlural("news", "news")
        assertPlural("equipment", "equipment")
        assertPlural("data", "data")
        assertPlural("people", "people")
        assertPlural("users", "users")
    }

    @Test
    fun onlyLastWordIsInflectedAndCaseIsKept() {
        assertPlural("blog_posts", "blog_post")
        assertPlural("order_people", "order_person")
        assertPlural("BlogPosts", "BlogPost")
        assertPlural("SalesPeople", "SalesPerson")
        assertPlural("line-items", "line-item")
        assertPlural("USERS", "USER")
        assertPlural("Ys", "Y")
        assertPlural("user2s", "user2")
        assertPlural("", "")
    }

    @Test
    fun countOfOneKeepsSingular() {
        assertEquals("user", Str.plural("user", 1))
        assertEquals("user", Str.plural("user", -1))
        assertEquals("users", Str.plural("user", 0))
        assertEquals("currencies", Str.of("currency").plural().toString())
        assertEquals("currency", Str.of("currency").plural(1).toString())
    }
}
