package io.konaframework.cadence.factory

import io.konaframework.core.Container
import io.konaframework.cadence.Database
import io.konaframework.cadence.relations.Author
import io.konaframework.cadence.relations.Book
import io.konaframework.cadence.relations.RelationDb
import io.konaframework.cadence.seeding.Seeder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.function.Supplier

class AuthorFactory : Factory<Author>(Author::class.java) {
    override fun definition(): Map<String, Any?> = mapOf("name" to "Author $index")
}

class BookFactory : Factory<Book>(Book::class.java) {
    override fun definition(): Map<String, Any?> = mapOf(
        "title" to Supplier { "Book $index" },
        "author_id" to AuthorFactory(),
    )
}

open class Greeting {
    open val text: String = "hi"
}

class AuthorSeeder : Seeder() {
    override fun run() {
        AuthorFactory().count(2).createMany()
    }
}

class BookSeeder(private val greeting: Greeting) : Seeder() {
    override fun run() {
        BookFactory().create(mapOf("title" to greeting.text))
    }
}

class DatabaseSeeder : Seeder() {
    override fun run() {
        call(AuthorSeeder::class.java)
        call(NestedSeeder())
    }
}

class NestedSeeder : Seeder() {
    override fun run() {
        call(BookSeeder::class.java)
    }
}

class FactoryTest {
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        database = RelationDb.create()
    }

    @AfterEach
    fun tearDown() {
        database.close()
        Database.clearGlobal()
    }

    @Test
    fun makeCreateCountStatesAndSequences() {
        val made = AuthorFactory().make()
        assertFalse(made.exists())
        assertEquals("Author 0", made["name"])
        assertEquals(mapOf("name" to "Author 0"), AuthorFactory().raw())
        assertEquals(mapOf("name" to "X"), AuthorFactory().raw(mapOf("name" to "X")))

        val factory = AuthorFactory().count(3)
        val many = factory.makeMany()
        assertEquals(listOf("Author 0", "Author 1", "Author 2"), many.map { it["name"] })
        assertEquals(0L, Author.count())

        val created = AuthorFactory().count(2).state(mapOf("name" to "Same")).createMany()
        assertTrue(created.all { it.exists() && it["name"] == "Same" })
        assertEquals(2L, Author.count())

        val seq = AuthorFactory().count(3).sequence(mapOf("name" to "A"), mapOf("name" to "B")).makeMany(mapOf())
        assertEquals(listOf("A", "B", "A"), seq.map { it["name"] })

        val upper = AuthorFactory().state { it + ("name" to it["name"].toString().uppercase()) }.make(mapOf())
        assertEquals("AUTHOR 0", upper["name"])
        assertEquals(emptyList<Author>(), AuthorFactory().count(0).createMany())
        assertThrows(IllegalArgumentException::class.java) { AuthorFactory().count(-1) }

        val made2 = mutableListOf<String>()
        val created2 = mutableListOf<Any?>()
        val one = AuthorFactory()
            .afterMaking { made2 += it["name"].toString() }
            .afterCreating { created2 += it.getKey() }
            .create(mapOf("name" to "Cb"))
        assertEquals(listOf("Cb"), made2)
        assertEquals(listOf(one.getKey()), created2)
        assertSame(Author::class.java, AuthorFactory().modelClass)
    }

    @Test
    fun nestedFactoriesAndSuppliers() {
        val book = BookFactory().create()
        assertEquals("Book 0", book["title"])
        val author = book.author().first()!!
        assertEquals("Author 0", author["name"])
        assertEquals(1L, Author.count())

        val lambda = Factory.of(Author::class.java) { mapOf("name" to "Lambda") }
        assertEquals("Lambda", lambda.create()["name"])
    }

    @Test
    fun seedersCallOthers() {
        val root = DatabaseSeeder()
        root.run()
        assertEquals(listOf("AuthorSeeder", "NestedSeeder", "BookSeeder"), root.called)
        assertEquals(3L, Author.count())
        assertEquals("hi", Book.first()!!["title"])

        val container = Container()
        container.instance(Greeting::class.java, object : Greeting() {
            override val text = "from container"
        })
        val viaContainer = Seeder.instantiate(DatabaseSeeder::class.java, container)
        assertSame(container, viaContainer.container)
        viaContainer.run()
        assertEquals(1L, Book.query().where("title", "from container").count())

        val fresh = Seeder.instantiate(AuthorSeeder::class.java, null)
        assertTrue(fresh.called.isEmpty())
        assertTrue(fresh.container != null)
    }
}
