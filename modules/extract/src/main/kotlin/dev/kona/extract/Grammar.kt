package dev.kona.extract

/**
 * Identifier quoting rules for a connection. Values are never inlined into SQL;
 * only table / column names pass through [wrap].
 *
 * `users.id` → `"users"."id"`, `name as n` → `"name" as "n"`, `*` and `t.*` keep the star bare.
 */
class Grammar(
    private val open: String,
    private val close: String,
) {
    fun wrap(value: String): String {
        val trimmed = value.trim()
        val alias = ALIAS.find(trimmed)
        if (alias != null) {
            return wrap(trimmed.substring(0, alias.range.first)) + " as " + wrapSegment(trimmed.substring(alias.range.last + 1))
        }
        return trimmed.split('.').joinToString(".") { wrapSegment(it) }
    }

    fun columnize(columns: Collection<String>): String = columns.joinToString(", ") { wrap(it) }

    private fun wrapSegment(segment: String): String {
        if (segment == "*" || open.isEmpty()) {
            return segment
        }
        return open + segment.replace(close, close + close) + close
    }

    companion object {
        private val ALIAS = Regex("\\s+as\\s+", RegexOption.IGNORE_CASE)

        /** ANSI double quotes (H2 with `DATABASE_TO_LOWER=TRUE`, PostgreSQL, SQLite). */
        @JvmField
        val ANSI = Grammar("\"", "\"")

        /** Backticks (MySQL / MariaDB). */
        @JvmField
        val MYSQL = Grammar("`", "`")

        /** No quoting; identifiers are emitted as written. */
        @JvmField
        val NONE = Grammar("", "")

        @JvmStatic
        fun forUrl(url: String): Grammar =
            if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) MYSQL else ANSI
    }
}
