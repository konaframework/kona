package org.konaframework.search

/** Search engine driver. */
interface Engine {
    fun update(models: List<Searchable>)
    fun delete(models: List<Searchable>)
    fun search(index: String, query: String): SearchResult
    fun flush(index: String)
}
