package org.konaframework.search

import org.konaframework.core.Config
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves search engines from config.
 *
 * - `search.default` — engine name (default `collection`)
 * - `search.engines.{name}.driver` — `collection`
 */
class SearchManager(
    private val config: Config,
) {
    private val engines = ConcurrentHashMap<String, Engine>()
    private val collection = CollectionEngine()

    fun engine(): Engine = engine(defaultEngine())

    fun engine(name: String): Engine = engines.getOrPut(name) { create(name) }

    fun defaultEngine(): String = config.getString("search.default", "collection")

    fun collection(): CollectionEngine = collection

    fun update(vararg models: Searchable) = engine().update(models.toList())

    fun delete(vararg models: Searchable) = engine().delete(models.toList())

    fun search(index: String, query: String): SearchResult = engine().search(index, query)

    private fun create(name: String): Engine {
        val driver = config.getString("search.engines.$name.driver", name)
        return when (driver.lowercase()) {
            "collection", "array", "memory" -> collection
            else -> throw IllegalArgumentException("Unknown search driver: $driver")
        }
    }
}
