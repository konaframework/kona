package org.konaframework.wire

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.Database
import org.konaframework.cadence.Grammar
import org.konaframework.cadence.schema.Schema
import org.konaframework.wire.hydration.ModelCollectionSynth
import org.konaframework.wire.hydration.ModelSynth
import org.konaframework.wire.hydration.SynthContext

class ModelSnapshotTest {
    class MemoUser : Cadence<MemoUser>() {
        override val timestamps = false
        fun getEmail(): String? = getAttribute("email") as String?
    }

    class NeedsArg(val label: String) : Cadence<NeedsArg>() {
        override val timestamps = false
    }

    class PlainNote

    class StoredUser : Cadence<StoredUser>() {
        override val table = "wire_users"
        override val timestamps = false
        fun getEmail(): String? = getAttribute("email") as String?
    }

    class Host : Component() {
        var user: MemoUser? = MemoUser().apply {
            setAttribute("email", "a@b.c")
            setAttribute("name", "Ada")
        }
        override fun template() = "<p>{{ user?.getEmail() }}|{{ user?.getAttribute('name') }}</p>"
    }

    class EmptyHost : Component() {
        var user: MemoUser? = null
        override fun template() = "<p>{{ user?.getEmail() }}|{{ user?.getAttribute('name') }}</p>"
    }

    @BeforeEach
    fun resetState() {
        Database.clearGlobal()
        Wire.reset()
    }

    @AfterEach
    fun clearState() {
        Database.clearGlobal()
        Wire.reset()
    }

    @Test
    fun templateKeepsModelMethodsAcrossARoundTrip() {
        val manager = brew()
        Wire.setManager(manager)
        val mounted = Wire.test(Host::class.java)
        assertTrue(mounted.html().contains("a@b.c|Ada"), mounted.html())
        val again = mounted.refresh()
        assertTrue(again.html().contains("a@b.c|Ada"), again.html())
        val empty = Wire.test(EmptyHost::class.java).refresh()
        assertTrue(empty.html().contains("|</p>"), empty.html())
    }

    @Test
    fun restoresAttributesWhenNoDatabaseIsConfigured() {
        val ctx = SynthContext(brew().synths, "user")
        val user = MemoUser().apply {
            setAttribute("email", "a@b.c")
            setAttribute("name", "Ada")
        }
        val (data, meta) = ModelSynth.dehydrate(user, ctx)
        assertNull(data)
        assertEquals("a@b.c", (meta["attributes"] as Map<*, *>)["email"])
        val restored = ModelSynth.hydrate(null, meta, ctx, null) as MemoUser
        assertEquals("a@b.c", restored.getEmail())
        assertEquals("Ada", restored.getAttribute("name"))

        val withKey = meta.toMutableMap()
        withKey["key"] = 7
        val keyed = ModelSynth.hydrate(null, withKey, ctx, null) as MemoUser
        assertEquals("a@b.c", keyed.getEmail())
        assertEquals(7, keyed.getKey())

        val present = withKey.toMutableMap()
        present["attributes"] = mapOf("id" to 4, "email" to "a@b.c")
        val kept = ModelSynth.hydrate(null, present, ctx, null) as MemoUser
        assertEquals(4, kept.getKey())

        val skipped = linkedMapOf<String, Any?>("class" to MemoUser::class.java.name, "attributes" to mapOf(1 to "nope", "email" to "a@b.c"))
        val partial = ModelSynth.hydrate(null, skipped, ctx, null) as MemoUser
        assertEquals("a@b.c", partial.getEmail())

        assertNull(ModelSynth.hydrate(null, mapOf("class" to MemoUser::class.java.name, "attributes" to "bad"), ctx, null))
        assertNull(ModelSynth.hydrate(null, mapOf("class" to PlainNote::class.java.name, "key" to 1), ctx, null))
        assertThrows(CorruptComponentPayloadException::class.java) {
            ModelSynth.hydrate(null, mapOf("class" to NeedsArg::class.java.name, "attributes" to mapOf("label" to "x")), ctx, null)
        }

        val (list, _) = ModelCollectionSynth.dehydrate(listOf(user), ctx)
        val models = ModelCollectionSynth.hydrate(list, emptyMap(), ctx, null) as List<*>
        assertEquals("a@b.c", (models.single() as MemoUser).getEmail())
    }

    @Test
    fun reloadsTheRowWhenADatabaseIsConfigured() {
        val h2 = JdbcDataSource()
        h2.setURL("jdbc:h2:mem:wiremodel${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
        val database = Database().addConnection("default", h2, Grammar.ANSI).setAsGlobal()
        try {
            database.withConnection { connection ->
                Schema(connection).create("wire_users") {
                    id()
                    string("email")
                }
            }
            database.table("wire_users").insert(mapOf("email" to "fresh@b.c"))
            val ctx = SynthContext(brew().synths, "user")
            val meta = mapOf(
                "class" to StoredUser::class.java.name,
                "key" to 1,
                "attributes" to mapOf("email" to "stale@b.c"),
            )
            val loaded = ModelSynth.hydrate(null, meta, ctx, null) as StoredUser
            assertEquals("fresh@b.c", loaded.getEmail())
            assertNull(ModelSynth.hydrate(null, mapOf("class" to StoredUser::class.java.name, "key" to 99), ctx, null))
        } finally {
            database.close()
            Database.clearGlobal()
        }
    }
}
