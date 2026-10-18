package org.konaframework.brew

/**
 * Fluent builders for Kona [org.konaframework.http.Validator] rule strings (`Rule::unique()->ignore()`).
 *
 * Use inside `rules()` / `validate([...])`:
 * ```
 * "email" to listOf("required", "email", Rules.unique("users", "email").ignore(id).toString())
 * ```
 *
 * Named [Rules] (plural) to avoid clashing with the `@Rule` annotation.
 */
object Rules {
    @JvmStatic
    @JvmOverloads
    fun unique(table: String, column: String? = null): UniqueRule = UniqueRule(table, column)

    @JvmStatic
    @JvmOverloads
    fun exists(table: String, column: String? = null): ExistsRule = ExistsRule(table, column)

    @JvmStatic
    fun inList(vararg values: Any?): String =
        "in:" + values.joinToString(",") { it?.toString().orEmpty() }

    @JvmStatic
    fun notIn(vararg values: Any?): String =
        "not_in:" + values.joinToString(",") { it?.toString().orEmpty() }
}

/** `unique:table,column,exceptId` builder. */
class UniqueRule internal constructor(
    private val table: String,
    private val column: String?,
    private var exceptId: String? = null,
) {
    fun ignore(id: Any?): UniqueRule {
        exceptId = id?.toString()
        return this
    }

    override fun toString(): String {
        val col = column ?: ""
        return when {
            exceptId != null -> "unique:$table,$col,$exceptId"
            column != null -> "unique:$table,$column"
            else -> "unique:$table"
        }
    }
}

/** `exists:table,column` builder. */
class ExistsRule internal constructor(
    private val table: String,
    private val column: String?,
) {
    override fun toString(): String =
        if (column != null) "exists:$table,$column" else "exists:$table"
}
