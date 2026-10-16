package dev.kona.cadence

import java.util.function.Function

/**
 * One page of results plus totals (length-aware paginator shape).
 * [toMap] renders `data`, `total`, `per_page`, `current_page`, `last_page` for JSON responses.
 */
class Page<T>(
    val data: List<T>,
    val total: Long,
    val perPage: Int,
    val currentPage: Int,
) {
    val lastPage: Int = maxOf(((total + perPage - 1) / perPage).toInt(), 1)

    fun hasMorePages(): Boolean = currentPage < lastPage

    fun isEmpty(): Boolean = data.isEmpty()

    fun <R> map(mapper: Function<T, R>): Page<R> = Page(data.map { mapper.apply(it) }, total, perPage, currentPage)

    /** Models in [data] are rendered through [Cadence.toMap]. */
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "data" to data.map { if (it is Cadence<*>) it.toMap() else it },
        "total" to total,
        "per_page" to perPage,
        "current_page" to currentPage,
        "last_page" to lastPage,
    )

    override fun toString(): String = "Page(currentPage=$currentPage, lastPage=$lastPage, perPage=$perPage, total=$total)"
}
