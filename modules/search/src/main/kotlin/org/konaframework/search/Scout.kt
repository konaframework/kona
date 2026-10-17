package org.konaframework.search

/** Static entry for search indexing / queries. */
object Scout {
    @Volatile
    private var manager: SearchManager? = null

    @JvmStatic
    fun setManager(manager: SearchManager) {
        this.manager = manager
    }

    @JvmStatic
    fun manager(): SearchManager =
        manager ?: throw IllegalStateException("Search manager not configured")

    @JvmStatic
    fun update(vararg models: Searchable) {
        manager().update(*models)
    }

    @JvmStatic
    fun remove(vararg models: Searchable) {
        manager().delete(*models)
    }

    @JvmStatic
    fun search(index: String, query: String): SearchResult = manager().search(index, query)
}
