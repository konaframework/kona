package io.konaframework.search

/**
 * Types that can be indexed by the search engine (Scout-class).
 */
interface Searchable {
    fun searchableAs(): String
    fun getScoutKey(): String
    fun toSearchableArray(): Map<String, Any?>
}
