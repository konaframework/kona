package org.konaframework.cadence

import java.util.Base64
import java.util.function.Function

/**
 * Simple paginator (no total count). Fetches `perPage + 1` rows to determine [hasMorePages].
 */
class SimplePage<T>(
    val data: List<T>,
    val perPage: Int,
    val currentPage: Int,
    private val hasMore: Boolean,
) {
    fun hasMorePages(): Boolean = hasMore

    fun isEmpty(): Boolean = data.isEmpty()

    fun <R> map(mapper: Function<T, R>): SimplePage<R> =
        SimplePage(data.map { mapper.apply(it) }, perPage, currentPage, hasMore)

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "data" to data.map { if (it is Cadence<*>) it.toMap() else it },
        "per_page" to perPage,
        "current_page" to currentPage,
        "has_more" to hasMore,
    )
}

/**
 * Cursor paginator. [nextCursor] / [prevCursor] are opaque Base64 tokens over the sort key.
 */
class CursorPage<T>(
    val data: List<T>,
    val perPage: Int,
    val nextCursor: String?,
    val prevCursor: String?,
    val path: String = "",
) {
    fun hasMorePages(): Boolean = nextCursor != null

    fun isEmpty(): Boolean = data.isEmpty()

    fun <R> map(mapper: Function<T, R>): CursorPage<R> =
        CursorPage(data.map { mapper.apply(it) }, perPage, nextCursor, prevCursor, path)

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "data" to data.map { if (it is Cadence<*>) it.toMap() else it },
        "per_page" to perPage,
        "next_cursor" to nextCursor,
        "prev_cursor" to prevCursor,
        "path" to path,
    )

    companion object {
        @JvmStatic
        fun encode(value: Any?): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toString().toByteArray())

        @JvmStatic
        fun decode(cursor: String): String =
            String(Base64.getUrlDecoder().decode(cursor))
    }
}
