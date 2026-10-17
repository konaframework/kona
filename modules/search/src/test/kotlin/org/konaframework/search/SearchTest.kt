package org.konaframework.search

import org.konaframework.core.Application
import org.konaframework.core.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

data class Post(
    val id: String,
    val title: String,
    val body: String,
) : Searchable {
    override fun searchableAs(): String = "posts"
    override fun getScoutKey(): String = id
    override fun toSearchableArray(): Map<String, Any?> = mapOf("title" to title, "body" to body, "nil" to null)
}

class SearchTest {
    @Test
    fun indexSearchDeleteAndProvider(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("search.default", "collection")
        app.config.set("search.engines.collection.driver", "collection")
        app.config.set("search.engines.array.driver", "array")
        app.config.set("search.engines.memory.driver", "memory")
        app.register(SearchServiceProvider()).boot()

        val manager = app.resolve(SearchManager::class.java)
        assertEquals("collection", manager.defaultEngine())
        assertTrue(manager.engine() is CollectionEngine)
        assertTrue(manager.engine("array") is CollectionEngine)
        assertTrue(manager.engine("memory") is CollectionEngine)

        val a = Post("1", "Kona Brew", "rich aroma")
        val b = Post("2", "Steam Train", "rails")
        Scout.update(a, b)
        assertEquals(2, manager.collection().size("posts"))

        val hits = Scout.search("posts", "kona")
        assertEquals(listOf("1"), hits.keys())
        assertFalse(hits.isEmpty())
        assertEquals(1.0, hits.hits[0].score)

        assertTrue(Scout.search("posts", "missing").isEmpty())
        assertEquals(2, Scout.search("posts", "").hits.size)
        assertEquals(0, Scout.search("missing-index", "x").hits.size)

        Scout.remove(a)
        assertEquals(1, manager.collection().size("posts"))
        // match by key only
        assertEquals(listOf("2"), Scout.search("posts", "2").keys())
        manager.engine().flush("posts")
        assertEquals(0, manager.collection().size("posts"))
        assertEquals(0, manager.collection().size("never-created"))
        // delete when index missing (null branch)
        manager.engine().delete(listOf(Post("99", "ghost", "x")))

        assertThrows(IllegalArgumentException::class.java) {
            app.config.set("search.engines.bad.driver", "meilisearch")
            manager.engine("bad")
        }
    }

    @Test
    fun managerRequired() {
        val field = Scout::class.java.getDeclaredField("manager")
        field.isAccessible = true
        field.set(null, null)
        assertThrows(IllegalStateException::class.java) { Scout.manager() }
        Scout.setManager(SearchManager(Config()))
        assertTrue(Scout.manager() != null)
    }
}
