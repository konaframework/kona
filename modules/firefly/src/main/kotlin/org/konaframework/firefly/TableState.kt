package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Table
import org.konaframework.http.RequestSession

/**
 * Applies panel page state (search, sort, page, page size, columns, filters) to a table.
 * Resource list and manage pages call it for you; custom pages pass their `render(state)` map.
 */
object TableState {
    @JvmStatic
    fun apply(built: Table<*, *>, state: Map<String, Any?>, sessionKey: String) {
        val search = state["search"] as? String
        if (!search.isNullOrBlank()) built.search(search)
        val sort = state["sort"] as? String
        if (!sort.isNullOrBlank()) {
            val rawDirection = state["direction"]
            val descending = rawDirection != null && rawDirection.toString().equals("desc", ignoreCase = true)
            try {
                built.sort(sort, if (descending) "desc" else "asc")
            } catch (_: FireflyException) {
                // Unknown / non-sortable column — ignore.
            }
        }
        val pageRaw = state["page"] as? String
        val pageNumber = pageRaw?.toIntOrNull()
        if (pageNumber != null) {
            try {
                built.page(pageNumber)
            } catch (_: FireflyException) {
                // Invalid page — keep default.
            }
        }
        val perPageRaw = state["perPage"] as? String
        val perPage = perPageRaw?.toIntOrNull()
        if (perPage != null) {
            try {
                built.perPage(perPage)
            } catch (_: FireflyException) {
                // Invalid size — keep default.
            }
        }
        // Soft-nav column picker: `?columns=name,created_at` (#363).
        val columnsRaw = state["columns"] as? String
        if (columnsRaw != null) {
            built.applyToggleableVisibility(columnsRaw.split(','))
        }
        applyFilters(built, state, sessionKey)
    }

    /**
     * Query-string filter state, falling back to the session copy when the table
     * [persists filters][Table.persistFiltersInSession] and the request carries none.
     */
    private fun applyFilters(built: Table<*, *>, state: Map<String, Any?>, sessionKey: String) {
        @Suppress("UNCHECKED_CAST")
        val input = state["filterInput"] as? Map<String, Any?> ?: emptyMap()
        val applied = built.applyFilterQuery(input)
        val filters = built.filterSet()
        val session = state["filterSession"] as? RequestSession
        if (!filters.persistsInSession() || session == null) return
        val key = filters.sessionKey() ?: sessionKey
        if (applied) {
            session.put(key, LinkedHashMap(built.filterQuery()))
            return
        }
        @Suppress("UNCHECKED_CAST")
        val stored = session.get(key) as? Map<String, Any?>
        if (stored != null) built.applyFilterQuery(stored)
    }
}
