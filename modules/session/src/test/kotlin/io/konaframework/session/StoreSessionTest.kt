package io.konaframework.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoreSessionTest {
    @Test
    fun putGetForgetPullAndHas() {
        val store = ArraySessionStore()
        val session = StoreSession(SessionId.generate(), store)
        assertFalse(session.has("name"))
        session.put("name", "kona")
        assertTrue(session.has("name"))
        assertEquals("kona", session.get("name"))
        session.put("empty", null)
        assertEquals("fallback", session.pull("missing", "fallback"))
        assertEquals("fallback", session.pull("nope", "fallback"))
        assertNull(session.pull("empty", "x"))
        assertFalse(session.all().containsKey("empty"))
        assertEquals("kona", session.pull("name"))
        assertNull(session.get("name"))
        session.put("a", 1).forget("a")
        assertNull(session.get("a"))
        assertEquals(emptyMap<String, Any?>(), session.all().filterKeys { !it.startsWith("_flash") })
    }

    @Test
    fun flashAgesAcrossStarts() {
        val store = ArraySessionStore()
        val id = SessionId.generate()
        val first = StoreSession(id, store).start(exists = false)
        first.flash("status", "saved")
        first.flash("status", "saved-again")
        first.save(120)
        assertEquals("saved-again", first.get("status"))

        val second = StoreSession(id, store, LinkedHashMap(store.read(id))).start(exists = true)
        assertEquals("saved-again", second.get("status"))
        second.save(120)

        val third = StoreSession(id, store, LinkedHashMap(store.read(id))).start(exists = true)
        assertNull(third.get("status"))
    }

    @Test
    fun flashHandlesNonListAndImmutableFlashBags() {
        val store = ArraySessionStore()
        val attrs = linkedMapOf<String, Any?>(
            StoreSession.FLASH_NEW to "bad",
            StoreSession.FLASH_OLD to 42,
            "keep" to true,
        )
        val session = StoreSession(SessionId.generate(), store, attrs).start(exists = true)
        assertTrue(session.get("keep") as Boolean)
        session.flash("msg", "hi")
        assertEquals("hi", session.get("msg"))

        // Immutable List (not MutableList) branch in flash().
        session.put(StoreSession.FLASH_NEW, listOf("prior"))
        session.flash("other", 1)
        @Suppress("UNCHECKED_CAST")
        val neu = session.get(StoreSession.FLASH_NEW) as List<String>
        assertTrue(neu.contains("prior"))
        assertTrue(neu.contains("other"))

        session.ageFlashData()
        // "msg" was dropped from the flash bag when FLASH_NEW was overwritten, so it persists.
        assertEquals("hi", session.get("msg"))
        session.ageFlashData()
        assertEquals("hi", session.get("msg"))
        assertNull(session.get("other"))
    }

    @Test
    fun regenerateAndInvalidate() {
        val store = ArraySessionStore()
        val session = StoreSession(SessionId.generate(), store).start(false)
        session.put("user", 1)
        session.save(60)
        val oldId = session.id
        assertEquals(1, store.size())

        session.regenerate(destroy = false)
        assertTrue(session.id != oldId)
        assertFalse(session.exists)
        assertEquals(mapOf("user" to 1), store.read(oldId).filterKeys { !it.startsWith("_flash") })

        session.save(60)
        val midId = session.id
        session.regenerate(destroy = true)
        assertTrue(store.read(midId).isEmpty())
        assertEquals(0, store.read(midId).size)

        session.put("x", "y")
        val beforeInvalidate = session.id
        session.save(60)
        session.invalidate()
        assertTrue(session.all().isEmpty() || session.all().keys.all { it.startsWith("_flash") })
        assertTrue(session.id != beforeInvalidate)
        assertTrue(store.read(beforeInvalidate).isEmpty())
    }

    @Test
    fun startMarksExists() {
        val store = ArraySessionStore()
        val session = StoreSession(SessionId.generate(), store).start(exists = true)
        assertTrue(session.exists)
        session.save(1)
        assertTrue(session.exists)
    }

    @Test
    fun oldInputAndPreviousUrl() {
        val store = ArraySessionStore()
        val session = StoreSession(SessionId.generate(), store).start(false)
        assertNull(session.old("email"))
        assertEquals("fallback", session.old("email", "fallback"))
        assertTrue(session.oldInput().isEmpty())

        session.flashInput(mapOf("email" to "a@b.c", "name" to "Ada"))
        assertEquals("a@b.c", session.old("email"))
        assertEquals("Ada", session.old("name", "x"))
        assertEquals(mapOf("email" to "a@b.c", "name" to "Ada"), session.oldInput())
        assertNull(session.old("missing"))

        session.put(StoreSession.OLD_INPUT, mapOf("blank" to null, "ok" to "1", "num" to 7))
        assertEquals("fallback", session.old("blank", "fallback"))
        assertNull(session.old("blank", null))
        assertEquals("7", session.old("num"))
        assertEquals(mapOf("blank" to "", "ok" to "1", "num" to "7"), session.oldInput())

        assertNull(session.previousUrl())
        session.setPreviousUrl("/contact")
        assertEquals("/contact", session.previousUrl())
        session.flashInput(emptyMap())
        assertTrue(session.oldInput().isEmpty())

        session.put(StoreSession.OLD_INPUT, "not-a-map")
        assertEquals("d", session.old("x", "d"))
        assertTrue(session.oldInput().isEmpty())
    }
}
