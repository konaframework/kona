package org.konaframework.firefly.tables

import org.konaframework.crema.CremaEngine
import org.konaframework.firefly.support.ClasspathViewFinder

/**
 * Bundled Crema engine for table chrome (Phase C).
 *
 * [Table.render] packs rows, cells, and query state. Markup for the toolbar,
 * scroll table, empty state, pagination, and cards lives under `views/firefly/tables/`.
 */
internal object TableCrema {
    const val WRAP: String = "firefly.tables.wrap"
    const val TOOLBAR: String = "firefly.tables.toolbar"
    const val FORM: String = "firefly.tables.toolbar-form"
    const val MOBILE_SORT: String = "firefly.tables.mobile-sort"
    const val COLUMN_TOGGLE: String = "firefly.tables.column-toggle"
    const val SELECTION: String = "firefly.tables.selection"
    const val SCROLL: String = "firefly.tables.scroll"
    const val EMPTY: String = "firefly.tables.empty"
    const val EMPTY_ROW: String = "firefly.tables.empty-row"
    const val EMPTY_CARD: String = "firefly.tables.empty-card"
    const val PAGINATION: String = "firefly.tables.pagination"
    const val CARDS: String = "firefly.tables.cards"
    const val CARD: String = "firefly.tables.card"

    private val engine: CremaEngine by lazy {
        CremaEngine(ClasspathViewFinder(TableCrema::class.java.classLoader))
    }

    fun render(name: String, data: Map<String, Any?>): String = engine.render(name, data)
}
