package io.konaframework.cadence

/**
 * Identifier quoting rules for a connection. Values are never inlined into SQL;
 * only table / column names pass through [wrap].
 *
 * `users.id` → `"users"."id"`, `name as n` → `"name" as "n"`, `*` and `t.*` keep the star bare.
 *
 * Built-ins: [ANSI] (H2, PostgreSQL, SQLite), [MYSQL] (MySQL / MariaDB), [NONE].
 * Register additional JDBC URL prefixes with [registerUrlPrefix] for custom databases.
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

        private val urlPrefixes = linkedMapOf<String, Grammar>()

        /**
         * Registers a query [Grammar] for JDBC URLs starting with [jdbcPrefix]
         * (for example `"jdbc:oracle:"`). Longest matching prefix wins over built-ins.
         */
        @JvmStatic
        fun registerUrlPrefix(jdbcPrefix: String, grammar: Grammar) {
            require(jdbcPrefix.isNotBlank()) { "JDBC URL prefix must not be blank" }
            urlPrefixes[jdbcPrefix.lowercase()] = grammar
        }

        /** Clears all [registerUrlPrefix] registrations (tests / reboot). */
        @JvmStatic
        fun clearRegisteredUrlPrefixes() {
            urlPrefixes.clear()
        }

        @JvmStatic
        fun forUrl(url: String): Grammar {
            val lower = url.lowercase()
            var bestLen = -1
            var best: Grammar? = null
            for ((prefix, grammar) in urlPrefixes) {
                if (!lower.startsWith(prefix)) {
                    continue
                }
                if (prefix.length <= bestLen) {
                    continue
                }
                bestLen = prefix.length
                best = grammar
            }
            if (best != null) {
                return best
            }
            return if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) MYSQL else ANSI
        }
    }
}
