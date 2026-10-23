package org.konaframework.wire.features

import org.konaframework.wire.Component
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import kotlin.math.ceil
import kotlin.math.max

/**
 * `WithPagination` — page state, URL sync via query string, and simple link HTML.
 * Themes: `tailwind` (default) / `bootstrap` via [paginationTheme].
 */
interface WithPagination {
    fun getPage(): Int
    fun setPage(page: Int)
    fun previousPage()
    fun nextPage()
    fun gotoPage(page: Int)
    fun resetPage()
    fun paginationView(): String? = null
    fun paginationTheme(): String = "tailwind"
}

/** Default [WithPagination] backed by a `page` (or custom [pageName]) property. */
abstract class Paginates : Component(), WithPagination {
    open var paginatorPage: Int = 1
    open val pageName: String get() = "page"
    open val paginatorScrollTo: Boolean = true
    open val withoutUrlPagination: Boolean = false

    override fun getPage(): Int = paginatorPage

    override fun setPage(page: Int) {
        paginatorPage = max(1, page)
    }

    override fun previousPage() {
        setPage(paginatorPage - 1)
    }

    override fun nextPage() {
        setPage(paginatorPage + 1)
    }

    override fun gotoPage(page: Int) {
        setPage(page)
    }

    override fun resetPage() {
        setPage(1)
    }

    /** Paginate an in-memory list (simple LengthAware shape). */
    fun paginate(items: List<*>, perPage: Int): LengthAwarePaginator {
        val result = LengthAwarePaginator.of(items, perPage, paginatorPage, pageName, paginationTheme(), paginationView())
        paginatorPage = result.currentPage
        return result
    }

    /** Simple paginator without total count. */
    fun simplePaginate(items: List<*>, perPage: Int): SimplePaginator =
        SimplePaginator.of(items, perPage, paginatorPage, pageName, paginationTheme())
}

class LengthAwarePaginator(
    val data: List<*>,
    val total: Int,
    val perPage: Int,
    val currentPage: Int,
    val pageName: String = "page",
    val theme: String = "tailwind",
    val view: String? = null,
) {
    val lastPage: Int = max(1, ceil(total.toDouble() / perPage).toInt())
    fun links(): String = PaginationViews.links(theme, pageName, currentPage, lastPage, view)
    fun items(): List<*> = data

    companion object {
        fun of(
            items: List<*>,
            perPage: Int,
            page: Int,
            pageName: String,
            theme: String,
            view: String?,
        ): LengthAwarePaginator {
            val total = items.size
            val last = max(1, ceil(total.toDouble() / perPage).toInt())
            val current = page.coerceIn(1, last)
            val slice = items.drop((current - 1) * perPage).take(perPage)
            return LengthAwarePaginator(slice, total, perPage, current, pageName, theme, view)
        }
    }
}

class SimplePaginator(
    val data: List<*>,
    val perPage: Int,
    val currentPage: Int,
    val hasMorePages: Boolean,
    val pageName: String = "page",
    val theme: String = "tailwind",
) {
    fun links(): String = PaginationViews.simple(theme, pageName, currentPage, hasMorePages)
    fun items(): List<*> = data

    companion object {
        fun of(items: List<*>, perPage: Int, page: Int, pageName: String, theme: String): SimplePaginator {
            val slice = items.drop((page - 1) * perPage).take(perPage + 1)
            val hasMore = slice.size > perPage
            return SimplePaginator(slice.take(perPage), perPage, page, hasMore, pageName, theme)
        }
    }
}

object PaginationViews {
    fun links(theme: String, pageName: String, current: Int, last: Int, custom: String?): String {
        if (custom != null) return custom
        return when (theme) {
            "bootstrap" -> bootstrap(pageName, current, last)
            else -> tailwind(pageName, current, last)
        }
    }

    fun simple(theme: String, pageName: String, current: Int, hasMore: Boolean): String {
        val prev = if (current > 1)
            """<button wire:click="previousPage" wire:key="prev">Prev</button>""" else ""
        val next = if (hasMore)
            """<button wire:click="nextPage" wire:key="next">Next</button>""" else ""
        return """<nav data-theme="$theme" data-page="$pageName">$prev$next</nav>"""
    }

    private fun tailwind(pageName: String, current: Int, last: Int): String {
        val buttons = (1..last).joinToString("") { p ->
            val active = if (p == current) " font-bold" else ""
            """<button class="px-2$active" wire:click="gotoPage($p)" wire:key="p$p">$p</button>"""
        }
        return """<nav data-theme="tailwind" data-page="$pageName">$buttons</nav>"""
    }

    private fun bootstrap(pageName: String, current: Int, last: Int): String {
        val items = (1..last).joinToString("") { p ->
            val active = if (p == current) " active" else ""
            """<li class="page-item$active"><button class="page-link" wire:click="gotoPage($p)">$p</button></li>"""
        }
        return """<nav data-theme="bootstrap" data-page="$pageName"><ul class="pagination">$items</ul></nav>"""
    }
}

/** Keeps paginator page in sync with `@Url` / query string when [Paginates.withoutUrlPagination] is false. */
class SupportPagination : ComponentHook() {
    override fun mount(params: Map<String, Any?>, key: String?) {
        val paginator = component as? Paginates ?: return
        if (paginator.withoutUrlPagination) return
        val raw = params[paginator.pageName] ?: return
        val page = raw.toString().toIntOrNull() ?: return
        paginator.setPage(page)
    }

    override fun dehydrate(context: ComponentContext) {
        val paginator = component as? Paginates ?: return
        if (paginator.withoutUrlPagination) return
        @Suppress("UNCHECKED_CAST")
        val url = (component.state().store["url"] as? MutableMap<String, Any?>)
            ?: linkedMapOf<String, Any?>().also { component.state().store["url"] = it }
        url[paginator.pageName] = linkedMapOf("value" to paginator.paginatorPage, "keep" to false)
        context.addEffect("url", url)
    }
}
