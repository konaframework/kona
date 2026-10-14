package dev.kona.extract

/**
 * Mixin for [Extract] models whose rows are "trashed" by setting [deletedAtColumn] instead of being removed.
 *
 * - `delete()` sets the column to now; queries exclude trashed rows by default.
 * - `withTrashed()` / `onlyTrashed()` on the query widen / invert that scope.
 * - [forceDelete] removes the row; [restore] clears the column.
 *
 * ```kotlin
 * class Post : Extract<Post>(), SoftDeletes
 * ```
 * ```java
 * public class Post extends Extract<Post> implements SoftDeletes { }
 * ```
 */
interface SoftDeletes {
    val deletedAtColumn: String
        get() = "deleted_at"

    fun trashed(): Boolean = (this as Extract<*>).getRawAttribute(deletedAtColumn) != null

    fun forceDelete(): Boolean = (this as Extract<*>).performForceDelete()

    fun restore(): Boolean = (this as Extract<*>).performRestore(deletedAtColumn)
}
