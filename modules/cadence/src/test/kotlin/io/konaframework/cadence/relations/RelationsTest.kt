package io.konaframework.cadence.relations

import io.konaframework.cadence.Database
import io.konaframework.cadence.CadenceCollection
import io.konaframework.cadence.CadenceException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class RelationsTest {
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

    private fun author(name: String) = Author.newInstance().forceFill(mapOf("name" to name)).also { it.save() }

    @Test
    fun hasManyAndBelongsToLazy() {
        val ada = author("Ada")
        val bob = author("Bob")
        val first = ada.books().create(mapOf("title" to "Engines"))
        ada.books().save(Book().forceFill(mapOf("title" to "Notes")))
        ada.books().createMany(listOf(mapOf("title" to "Letters")))
        ada.books().saveMany(listOf(Book().forceFill(mapOf("title" to "Poems"))))

        assertEquals(listOf("Engines", "Notes", "Letters", "Poems"), ada.books().get().map { it["title"] })
        assertEquals(1, ada.books().where("title", "Notes").getResults().size)
        assertEquals(1, ada.books().where("id", ">", first.getKey()).where("title", "like", "L%").get().size)
        assertEquals(emptyList<Book>(), bob.books().get())
        assertEquals(emptyList<Book>(), Author().books().get())
        assertEquals(4L, ada.books().count())
        assertTrue(ada.books().exists())
        assertFalse(bob.books().exists())
        assertEquals(0L, Author().books().count())
        assertNull(Author().books().first())
        assertEquals("Engines", ada.books().first()!!["title"])
        assertEquals(listOf("Ada"), first.author().get().map { it["name"] })

        assertEquals("Ada", first.author().first()!!["name"])
        assertNull(Book().author().getResults())
        val made = ada.books().make(mapOf("title" to "Draft"))
        assertFalse(made.exists())
        assertEquals(ada.getKey(), made["author_id"])

        first.delete()
        assertEquals(3, ada.books().get().size)
        assertThrows(IllegalStateException::class.java) { Author().books().create(mapOf("title" to "x")) }
    }

    @Test
    fun hasManyFirstOrCreateAndUpdateOrCreate() {
        val ada = author("Ada")
        val bob = author("Bob")
        bob.books().create(mapOf("title" to "BobOnly"))

        val draft = ada.books().firstOrNew(mapOf("title" to "Draft"))
        assertFalse(draft.exists())
        assertEquals(ada.getKey(), draft["author_id"])
        assertEquals("Draft", draft["title"])

        val created = ada.books().firstOrCreate(mapOf("title" to "Engines"), mapOf("title" to "Engines"))
        assertTrue(created.exists())
        assertEquals(ada.getKey(), created["author_id"])
        assertEquals("Engines", created["title"])
        assertEquals(created.getKey(), ada.books().firstOrCreate(mapOf("title" to "Engines")).getKey())
        assertEquals(1L, ada.books().where("title", "Engines").count())
        assertEquals(0L, bob.books().where("title", "Engines").count())

        val oneArg = ada.books().firstOrCreate(mapOf("title" to "Letters"))
        assertTrue(oneArg.exists())
        assertEquals(ada.getKey(), oneArg["author_id"])

        val updated = ada.books().updateOrCreate(mapOf("title" to "Engines"), mapOf("title" to "Engines v2"))
        assertEquals(created.getKey(), updated.getKey())
        assertEquals("Engines v2", updated["title"])

        val brand = ada.books().updateOrCreate(mapOf("title" to "BrandNew"))
        assertTrue(brand.exists())
        assertEquals(ada.getKey(), brand["author_id"])
        assertEquals("BrandNew", brand["title"])

        val existingNew = ada.books().firstOrNew(mapOf("title" to "BrandNew"))
        assertTrue(existingNew.exists())
        assertEquals(brand.getKey(), existingNew.getKey())

        assertThrows(IllegalStateException::class.java) {
            Author().books().firstOrCreate(mapOf("title" to "x"))
        }
        assertThrows(IllegalStateException::class.java) {
            Author().books().updateOrCreate(mapOf("title" to "x"))
        }
        assertThrows(IllegalStateException::class.java) {
            Author().books().firstOrNew(mapOf("title" to "x"))
        }

        val profile = ada.profile().firstOrCreate(mapOf("bio" to "Hi"))
        assertEquals("Hi", profile["bio"])
        assertEquals(ada.getKey(), profile["author_id"])
        assertEquals(profile.getKey(), ada.profile().firstOrCreate(mapOf("bio" to "Hi"), mapOf("bio" to "Nope")).getKey())
        assertEquals(
            profile.getKey(),
            ada.profile().updateOrCreate(mapOf("bio" to "Hi"), mapOf("bio" to "Hello")).getKey(),
        )
        assertEquals("Hello", ada.profile().first()!!["bio"])
    }

    @Test
    fun hasOneAndAssociate() {
        val ada = author("Ada")
        assertNull(ada.profile().first())
        assertNull(Author().profile().getResults())
        val profile = ada.profile().create(mapOf("bio" to "Mathematician"))
        assertEquals("Mathematician", ada.profile().first()!!["bio"])
        assertEquals("Ada", profile.author().getResults()!!["name"])

        val bob = author("Bob")
        val relation = profile.author()
        assertSame(profile, relation.associate(bob))
        assertSame(bob, profile.getRelation("author"))
        profile.save()
        assertEquals("Bob", Profile.find(profile.getKey())!!.author().first()!!["name"])
        relation.dissociate()
        assertNull(profile["author_id"])
        assertNull(profile.getRelation("author"))
        assertTrue(profile.relationLoaded("author"))
        assertEquals("author_id", relation.foreignKey)
        assertEquals("id", relation.ownerKey)
        assertEquals("author", relation.relationName)
    }

    @Test
    fun eagerLoadingUsesOneQueryPerRelation() {
        val authors = (1..5).map { author("A$it") }
        authors.forEachIndexed { i, a ->
            repeat(i) { n -> a.books().create(mapOf("title" to "B$i-$n")) }
            if (i % 2 == 0) a.profile().create(mapOf("bio" to "bio$i"))
        }
        Book.all().forEach { book ->
            book.getKey()!!.let { id -> repeat(2) { Chapter().forceFill(mapOf("book_id" to id, "position" to 2 - it, "name" to "c$it")).save() } }
        }

        RelationDb.statements.set(0)
        val loaded = Author.with("books", "profile").orderBy("id").get()
        assertEquals(3, RelationDb.statements.get())
        assertEquals(listOf(0, 1, 2, 3, 4), loaded.map { it.getRelationValue<List<Book>>("books")!!.size })
        assertEquals(listOf(true, false, true, false, true), loaded.map { it.getRelation("profile") != null })

        RelationDb.statements.set(0)
        val nested = Author.query().with("books.chapters", "books.author").get()
        assertEquals(4, RelationDb.statements.get())
        val chapters = nested.last().getRelation("books") as List<*>
        val firstBook = chapters.first() as Book
        assertEquals(listOf(1, 2), (firstBook.getRelation("chapters") as List<*>).map { (it as Chapter)["position"] })
        assertEquals("A5", (firstBook.getRelation("author") as Author)["name"])

        RelationDb.statements.set(0)
        val books = Book.query().with("author").get()
        assertEquals(2, RelationDb.statements.get())
        assertTrue(books.all { (it.getRelation("author") as Author).getKey() == it["author_id"] })

        val orphan = Book().forceFill(mapOf("title" to "Orphan")).also { it.save() }
        assertNull(Book.query().with("author").where("id", orphan.getKey()).first()!!.getRelation("author"))
        assertNull(Book.query().with("author").where("id", -1).first())
        assertEquals(emptyList<Book>(), Book.query().with("author").where("id", -1).get())

        val page = Author.query().with("books").orderBy("id").paginate(2, 2)
        assertEquals(listOf(2, 3), page.data.map { (it.getRelation("books") as List<*>).size })
        assertEquals(listOf("books"), Author.query().with("books").getEagerLoads().keys.toList())
        assertEquals(mapOf<String, RelationConstraint?>(), Author.query().getEagerLoads())
        assertTrue(Author.query().eagerLoadRelations(emptyList()).isEmpty())
    }

    @Test
    fun constrainedEagerLoadsAndLoadOnInstances() {
        val ada = author("Ada")
        listOf("Zeta", "Alpha", "Mid").forEach { ada.books().create(mapOf("title" to it)) }

        val withConstraint = Author.query()
            .with("books", RelationConstraint { it.where("title", "!=", "Mid").orderBy("title") })
            .first()!!
        assertEquals(listOf("Alpha", "Zeta"), (withConstraint.getRelation("books") as List<*>).map { (it as Book)["title"] })

        val nestedConstraint = Author.query()
            .with("books.chapters", RelationConstraint { it.where("position", 99) })
            .with("books", null)
            .first()!!
        assertEquals(3, (nestedConstraint.getRelation("books") as List<*>).size)

        val fresh = Author.find(ada.getKey())!!
        assertFalse(fresh.relationLoaded("books"))
        assertEquals(3, fresh.getRelationValue<List<Book>>("books")!!.size)
        RelationDb.statements.set(0)
        fresh.getRelationValue<List<Book>>("books")
        assertEquals(0, RelationDb.statements.get())

        fresh.setRelation("books", emptyList<Book>())
        fresh.loadMissing("books", "profile")
        assertEquals(0, (fresh.getRelation("books") as List<*>).size)
        assertTrue(fresh.relationLoaded("profile"))
        fresh.load("books.chapters")
        assertEquals(3, (fresh.getRelation("books") as List<*>).size)
        fresh.loadMissing("books.author")

        val list = Author.all()
        assertSame(list, EagerLoader.load(list, "books"))
        assertTrue(EagerLoader.load(emptyList<Author>(), "books").isEmpty())

        val rel = ada.books().with("chapters").constrain { it.orderByDesc("title") }
        assertEquals("Zeta", (rel.getResults().first())["title"])
        assertNotNull(rel.getResults().first().getRelation("chapters"))
        assertSame(ada, rel.parent)
        assertEquals("books", rel.related.table)
        assertEquals("author_id", (rel as HasMany<Book>).foreignKey)
        assertEquals("id", rel.localKey)
        assertEquals(mapOf("id" to ada.getKey(), "name" to "Ada"), ada.toMap().filterKeys { it == "id" || it == "name" })
    }

    @Test
    fun undefinedRelationsFail() {
        val ada = author("Ada")
        val missing = assertThrows(CadenceException::class.java) { ada.load("nope") }
        assertTrue(missing.message!!.contains("undefined relationship [nope]"))
        assertThrows(CadenceException::class.java) { ada.load("notARelation") }
        assertThrows(IllegalStateException::class.java) { ada.load("broken") }
        assertThrows(CadenceException::class.java) { EagerLoader.relationOf(ada, "toString") }
    }

    @Test
    fun belongsToManyPivotOperations() {
        val ada = author("Ada")
        val bob = author("Bob")
        val (sci, art, math) = listOf("Science", "Art", "Math").map { name ->
            Genre().forceFill(mapOf("name" to name)).also { it.save() }
        }

        val genres = ada.genres()
        assertEquals("author_genre", genres.pivotTable)
        assertEquals("author_id", genres.foreignPivotKey)
        assertEquals("genre_id", genres.relatedPivotKey)
        assertEquals("id", genres.parentKey)
        assertEquals("id", genres.relatedKey)

        genres.attach(sci)
        genres.attach(listOf(art.getKey()), mapOf("weight" to 5))
        val loaded = ada.genres().orderBy("name").get()
        assertEquals(listOf("Art", "Science"), loaded.map { it["name"] })
        @Suppress("UNCHECKED_CAST")
        val pivot = loaded.first().getRelation(BelongsToMany.PIVOT) as Map<String, Any?>
        assertEquals(5, pivot["weight"])
        assertEquals(ada.getKey(), pivot["author_id"])
        assertNotNull(pivot["created_at"])
        assertFalse(loaded.first().hasAttribute("pivot_weight"))

        val before = Instant.now().minusSeconds(1)
        assertEquals(1, ada.genres().updateExistingPivot(art, mapOf("weight" to 9)))
        assertEquals(9, (ada.genres().where("name", "Art").get().single().getRelation("pivot") as Map<*, *>)["weight"])
        assertTrue(before.isBefore(Instant.now()))

        assertEquals(SyncResult(listOf(math.getKey()), listOf(sci.getKey())), ada.genres().sync(listOf(art, math, math.getKey())))
        assertEquals(SyncResult(emptyList(), emptyList()), ada.genres().sync(listOf(art.getKey(), math.getKey())))
        assertEquals(listOf("Art", "Math"), ada.genres().orderBy("name").get().map { it["name"] })

        val carl = author("Carl")
        assertEquals(emptyList<Any?>(), (Author.query().with("genres").where("id", carl.getKey()).first()!!.getRelation("genres")))
        carl.delete()
        bob.genres().attach(listOf(sci, art))
        RelationDb.statements.set(0)
        val all = Author.query().with("genres").orderBy("id").get()
        assertEquals(2, RelationDb.statements.get())
        assertEquals(listOf(2, 2), all.map { (it.getRelation("genres") as List<*>).size })
        val withoutPivotAuthors = Genre.query().with("authors").where("name", "Art").first()!!
        assertEquals(2, (withoutPivotAuthors.getRelation("authors") as List<*>).size)
        assertEquals(listOf("Ada", "Bob"), art.authors().orderBy("name").get().map { it["name"] })
        assertEquals(1, math.authors().updateExistingPivot(ada, mapOf("weight" to 1)))

        assertEquals(1, ada.genres().detach(math))
        assertEquals(0, ada.genres().detach(emptyList<Any>()))
        assertEquals(1, ada.genres().detach(listOf(art.getKey())))
        assertEquals(2, bob.genres().detach())
        assertEquals(emptyList<Genre>(), Author().genres().getResults())
        assertThrows(IllegalStateException::class.java) { Author().genres().attach(sci) }
        assertEquals(emptyList<Genre>(), ada.genres().get())
    }

    @Test
    fun helpers() {
        assertEquals(5L, Relation.normalizeKey(5))
        assertEquals("k", Relation.normalizeKey("k"))
        assertNull(Relation.normalizeKey(null))
        assertEquals("blog_post", Relation.snake("BlogPost"))
        assertEquals("author_id", Author().defaultForeignKey())
        val custom = Author().hasMany(Book::class.java, "writer_id", "uuid")
        assertEquals("writer_id", custom.foreignKey)
        assertEquals("uuid", custom.localKey)
        val customOne = Author().hasOne(Profile::class.java, "owner_id")
        assertEquals("owner_id", customOne.foreignKey)
        assertEquals("code", Author().hasOne(Profile::class.java, null, "code").localKey)
        val chapterBook = Chapter().book()
        assertEquals(listOf("book_id", "id", "book"), listOf(chapterBook.foreignKey, chapterBook.ownerKey, chapterBook.relationName))
        val many = Genre().belongsToMany(Author::class.java, "links", "g", "a", "gid", "aid")
        assertEquals(listOf("links", "g", "a", "gid", "aid"), listOf(many.pivotTable, many.foreignPivotKey, many.relatedPivotKey, many.parentKey, many.relatedKey))
    }

    @Test
    fun whereHasWithCountAndPivotHelpers() {
        val ada = author("Ada")
        val bob = author("Bob")
        ada.books().create(mapOf("title" to "Engines"))
        ada.books().create(mapOf("title" to "Notes"))
        val trashed = ada.books().create(mapOf("title" to "Gone"))
        trashed.delete()
        bob.books().create(mapOf("title" to "Only"))
        ada.profile().create(mapOf("bio" to "Hi"))

        val sci = Genre().forceFill(mapOf("name" to "Science")).also { it.save() }
        val art = Genre().forceFill(mapOf("name" to "Art")).also { it.save() }
        ada.genres().attach(sci)
        ada.genres().attach(listOf(art.getKey()), mapOf("weight" to 3))
        bob.genres().attach(sci)

        assertEquals(listOf("Ada", "Bob"), Author.has("books").orderBy("name").get().map { it["name"] })
        assertEquals(listOf("Bob"), Author.doesntHave("profile").orderBy("name").get().map { it["name"] })
        assertEquals(
            listOf("Ada"),
            Author.whereHas("books") { it.where("title", "Notes") }.get().map { it["name"] },
        )
        assertEquals(
            listOf("Bob"),
            Author.whereDoesntHave("books") { it.where("title", "Notes") }.orderBy("name").get().map { it["name"] },
        )
        assertEquals(
            2,
            Author.query().where("name", "Nobody").orWhereHas("genres").get().size,
        )
        assertEquals(
            listOf("Ada", "Bob"),
            Author.query().where("name", "Ada").orWhereDoesntHave("profile").orderBy("name").get().map { it["name"] },
        )
        assertEquals(
            listOf("Ada"),
            Author.whereHas("genres") { it.where("name", "Art") }.get().map { it["name"] },
        )
        assertEquals(
            listOf("Ada"),
            Author.whereHas("profile").get().map { it["name"] },
        )
        assertEquals(
            "Hi",
            Profile.whereHas("author") { it.where("name", "Ada") }.first()!!["bio"],
        )

        val counted = Author.withCount("books", "genres", "books as published_books_count").orderBy("name").get()
        assertEquals(listOf(2L, 1L), counted.map { (it["books_count"] as Number).toLong() })
        assertEquals(listOf(2L, 1L), counted.map { (it["genres_count"] as Number).toLong() })
        assertEquals(2L, (counted.first()["published_books_count"] as Number).toLong())

        ada.loadCount("books as loaded_books_count")
        assertEquals(2L, (ada["loaded_books_count"] as Number).toLong())
        Author().loadCount("books")
        ada.loadCount()

        assertEquals(
            SyncResult(emptyList(), emptyList()),
            ada.genres().syncWithoutDetaching(listOf(sci, art)),
        )
        val math = Genre().forceFill(mapOf("name" to "Math")).also { it.save() }
        assertEquals(SyncResult(listOf(math.getKey()), emptyList()), ada.genres().syncWithoutDetaching(listOf(math)))
        assertEquals(
            SyncResult(emptyList(), listOf(math.getKey())),
            ada.genres().toggle(listOf(math)),
        )
        assertEquals(
            SyncResult(listOf(math.getKey()), emptyList()),
            ada.genres().toggle(listOf(math)),
        )
        assertEquals(listOf("Art"), ada.genres().wherePivot("weight", 3).get().map { it["name"] })
        assertEquals(listOf("Art"), ada.genres().wherePivot("weight", ">=", 3).get().map { it["name"] })
        assertEquals(
            listOf("Art", "Science"),
            ada.genres().wherePivotIn("genre_id", listOf(sci.getKey(), art.getKey())).orderBy("name").get().map { it["name"] },
        )

        val col = Author.query().with("books").orderBy("id").getCollection()
        assertEquals(ada.getKey(), col.find(ada.getKey())!!.getKey())
        col.loadMissing("genres")
        assertTrue(col.first()!!.relationLoaded("genres"))
        col.loadMissing("genres")
        CadenceCollection.of(emptyList<Author>()).loadMissing("books")

        assertEquals(listOf("Ada"), Author.orWhereHas("profile").where("name", "Ada").get().map { it["name"] })
        assertEquals(listOf("Bob"), Author.whereDoesntHave("profile").get().map { it["name"] })
        assertEquals(
            listOf("Ada", "Bob"),
            Author.orWhereDoesntHave("profile").where("name", "Missing").orWhere("name", "Ada").orWhere("name", "Bob")
                .orderBy("name").get().map { it["name"] },
        )
        assertEquals(
            2L,
            (Author.query().select("authors.id", "authors.name").withCount("books").where("id", ada.getKey()).first()!!["books_count"] as Number).toLong(),
        )
        ada.loadCount("books")
        assertEquals(2L, (ada["books_count"] as Number).toLong())

        assertThrows(CadenceException::class.java) { Author.whereHas("dummy").get() }
        assertThrows(CadenceException::class.java) { Author.withCount("dummy").get() }
    }
}
