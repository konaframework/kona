package org.konaframework.cadence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SerializationEventsTest {
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        database = TestDb.create()
        ModelDispatcher.flush()
    }

    @AfterEach
    fun tearDown() {
        ModelDispatcher.flush()
        database.close()
        Database.clearGlobal()
    }

    @Test
    fun hiddenVisibleAppendsAndJson() {
        val user = User()
        user.forceFill(mapOf("name" to "Ada", "email" to "a@b.c", "secret" to "hidden-value"))
        user.makeHidden("secret")
        user.makeHidden("secret") // second call covers existing hiddenOverrides bag
        val array = user.toArray()
        assertFalse(array.containsKey("secret"))
        assertEquals("Ada", array["name"])

        user.makeVisible("secret")
        user.makeVisible("secret") // second call covers existing visibleOverrides bag
        assertEquals("hidden-value", user.toArray()["secret"])

        user.setAppends("label")
        assertEquals("Ada <a@b.c>", user.toArray()["label"])

        val json = user.toJson()
        assertTrue(json.contains("Ada"))
        assertEquals(user.toArray(), user.toMap())

        // Hide a relation when visible list is empty again
        val withRel = User()
        withRel.forceFill(mapOf("name" to "R", "email" to "r@x"))
        withRel.setRelation("meta", "rel")
        withRel.makeHidden("meta")
        assertFalse(withRel.toArray().containsKey("meta"))
    }

    @Test
    fun visibleOnlySerialization() {
        val model = VisibleUser()
        model.forceFill(mapOf("name" to "X", "email" to "e@x"))
        assertEquals(setOf("name"), model.toArray().keys)
        assertNull(model.getAppendedAttribute("missing"))
        model.makeHidden("name")
        model.makeVisible("email")
        assertTrue(model.toArray().containsKey("email"))
        val again = User()
        again.makeHidden("email")
        again.makeVisible("email")
        assertFalse(again.effectiveHidden().contains("email"))
        // makeVisible with no prior makeHidden covers null hiddenOverrides branch
        val bare = User()
        bare.makeVisible("email")
        assertTrue(bare.effectiveVisible().contains("email"))
    }

    @Test
    fun lifecycleEventsOnSaveUpdateDelete() {
        val seen = mutableListOf<ModelEvent>()
        ModelEvent.entries.forEach { event ->
            ModelDispatcher.listen(event) { seen += event }
        }

        val user = User.create(mapOf("name" to "Ada", "email" to "a@b.c"))
        assertTrue(seen.contains(ModelEvent.CREATING))
        assertTrue(seen.contains(ModelEvent.CREATED))
        assertTrue(seen.contains(ModelEvent.SAVING))
        assertTrue(seen.contains(ModelEvent.SAVED))

        seen.clear()
        user["name"] = "Ada2"
        user.save()
        assertTrue(seen.contains(ModelEvent.UPDATING))
        assertTrue(seen.contains(ModelEvent.UPDATED))

        seen.clear()
        user.save()
        assertTrue(seen.contains(ModelEvent.SAVING))
        assertTrue(seen.contains(ModelEvent.SAVED))
        assertFalse(seen.contains(ModelEvent.UPDATING))

        seen.clear()
        val found = User.find(user.getKey()!!)
        assertTrue(found != null)
        assertTrue(seen.contains(ModelEvent.RETRIEVED))

        seen.clear()
        val post = SoftPost.create(mapOf("title" to "Hi"))
        seen.clear()
        post.delete()
        assertTrue(seen.contains(ModelEvent.DELETING))
        assertTrue(seen.contains(ModelEvent.TRASHED))
        assertTrue(seen.contains(ModelEvent.DELETED))

        seen.clear()
        post.restore()
        assertTrue(seen.contains(ModelEvent.RESTORING))
        assertTrue(seen.contains(ModelEvent.RESTORED))

        seen.clear()
        post.forceDelete()
        assertTrue(seen.contains(ModelEvent.FORCE_DELETING))
        assertTrue(seen.contains(ModelEvent.FORCE_DELETED))
    }

    @Test
    fun withoutEventsAndSaveQuietly() {
        val seen = mutableListOf<ModelEvent>()
        ModelEvent.entries.forEach { event ->
            ModelDispatcher.listen(event) { seen += event }
        }

        ModelDispatcher.withoutEvents(
            Runnable {
                User.create(mapOf("name" to "Silent", "email" to "s@x"))
            },
        )
        assertTrue(seen.isEmpty())

        val user = User.create(mapOf("name" to "Loud", "email" to "l@x"))
        seen.clear()
        user["name"] = "Louder"
        assertTrue(user.saveQuietly())
        assertTrue(seen.isEmpty())

        seen.clear()
        assertTrue(user.deleteQuietly())
        assertTrue(seen.isEmpty())
        assertNull(User.find(user.getKey()))
    }

    @Test
    fun extractCollectionHelpers() {
        val a = User.create(mapOf("name" to "A", "email" to "a@x"))
        val b = User.create(mapOf("name" to "B", "email" to "b@x"))
        val unfinished = User()
        unfinished.forceFill(mapOf("name" to "Z"))
        val col = CadenceCollection.of(listOf(a, b, unfinished))
        assertEquals(3, col.count())
        assertEquals(listOf(a, b, unfinished), col.all())
        assertEquals(listOf("A", "B", "Z"), col.pluck("name"))
        assertEquals(2, col.modelKeys().size)
        assertEquals(3, col.toArray().size)
        assertEquals(a.getKey(), col.toMapKeyedById()[a.getKey()]!!.getKey())
        assertEquals("A", col.first()!!["name"])
        assertEquals("Z", col.last()!!["name"])
        assertFalse(col.isEmpty())
        col.each { assertNotNull(it["name"]) }
        assertEquals(1, col.filter { it["name"] == "A" }.count())
        assertEquals(3, col.toList().size)
        assertEquals(2, CadenceCollection.make(a, b).count())
        assertEquals(a.getKey(), col.find(a.getKey())!!.getKey())
        assertNull(col.find(9999))
        col.load()
        col.loadMissing()
        CadenceCollection.of(emptyList<User>()).load()
        CadenceCollection.of(emptyList<User>()).loadMissing("x")
        val iterated = mutableListOf<User>()
        for (item in col) {
            iterated += item
        }
        assertEquals(3, iterated.size)
    }

    class User : Cadence<User>() {
        override val fillable = setOf("name", "email")
        override val hidden = setOf("secret")
        override val appends = setOf("label")

        override fun getAppendedAttribute(key: String): Any? =
            if (key == "label") "${getAttribute("name")} <${getAttribute("email")}>" else null

        companion object : CadenceCompanion<User>(User::class.java)
    }

    class VisibleUser : Cadence<VisibleUser>() {
        override val table = "users"
        override val fillable = setOf("name", "email")
        override val visible = setOf("name")
    }

    class SoftPost : Cadence<SoftPost>(), SoftDeletes {
        override val table = "posts"
        override val fillable = setOf("title")
        companion object : CadenceCompanion<SoftPost>(SoftPost::class.java)
    }
}
