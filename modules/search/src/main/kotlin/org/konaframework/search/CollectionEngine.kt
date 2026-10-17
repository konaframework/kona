package org.konaframework.search

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory collection engine for tests and local search.
 */
class CollectionEngine : Engine {
    private val indexes = ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, Any?>>>()

    override fun update(models: List<Searchable>) {
        for (model in models) {
            val index = indexes.getOrPut(model.searchableAs()) { ConcurrentHashMap() }
            index[model.getScoutKey()] = model.toSearchableArray()
        }
    }

    override fun delete(models: List<Searchable>) {
        for (model in models) {
            val index = indexes[model.searchableAs()]
            if (index != null) {
                index.remove(model.getScoutKey())
            }
        }
    }

    override fun search(index: String, query: String): SearchResult {
        val docs = indexes[index]
        if (docs == null) {
            return SearchResult(emptyList())
        }
        val needle = query.lowercase()
        if (needle.isBlank()) {
            return SearchResult(docs.map { (k, v) -> Hit(k, v) })
        }
        val hits = mutableListOf<Hit>()
        for ((key, doc) in docs) {
            if (matches(doc, key, needle)) {
                hits += Hit(key, doc, score = 1.0)
            }
        }
        return SearchResult(hits)
    }

    override fun flush(index: String) {
        indexes.remove(index)
    }

    fun size(index: String): Int = indexes[index]?.size ?: 0

    private fun matches(doc: Map<String, Any?>, key: String, needle: String): Boolean {
        if (key.lowercase().contains(needle)) {
            return true
        }
        for (value in doc.values) {
            val text = if (value == null) "" else value.toString()
            if (text.lowercase().contains(needle)) {
                return true
            }
        }
        return false
    }
}
