package org.konaframework.cadence

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException

/** SQL dialect families with first-party query compilation in Cadence. */
enum class Dialect {
    /** Generic ANSI SQL (`limit` / `offset`, `for update` / `for share`). */
    STANDARD,
    H2,
    SQLITE,
    MYSQL,
    MARIADB,
    POSTGRES,
    SQLSERVER,
    ORACLE,
}

/** Row-lock mode requested by [QueryBuilder.lockForUpdate] / [QueryBuilder.sharedLock]. */
enum class LockMode {
    NONE,
    UPDATE,
    SHARED,
}

/**
 * Identifier quoting plus the dialect-sensitive fragments of query SQL for one connection.
 * Values are never inlined into SQL; only table / column names pass through [wrap].
 *
 * `users.id` → `"users"."id"`, `name as n` → `"name" as "n"`, `*` and `t.*` keep the star bare.
 *
 * Built-ins (picked by [forUrl]): [H2], [SQLITE], [POSTGRES], [ORACLE] (ANSI `"…"`),
 * [MYSQL] / [MARIADB] (backticks), [SQLSERVER] (`[…]`), [ANSI] (generic), [NONE] (unquoted).
 * Register additional JDBC URL prefixes with [registerUrlPrefix] for custom databases.
 */
open class Grammar @JvmOverloads constructor(
    private val open: String,
    private val close: String,
    val dialect: Dialect = Dialect.STANDARD,
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

    /** Same dialect with identifiers emitted as written (`quote_identifiers = false`). */
    fun unquoted(): Grammar = Grammar("", "", dialect)

    /**
     * `select [distinct] [top n] columns` — SQL Server uses `top` when a limit has no offset
     * (and no unions); every other engine pages in [compileOrderLimit].
     */
    open fun compileSelectHead(distinct: Boolean, columns: String, limit: Int?, offset: Int?, hasUnions: Boolean): String {
        val head = StringBuilder("select ")
        if (distinct) {
            head.append("distinct ")
        }
        if (usesTop(limit, offset, hasUnions)) {
            head.append("top ").append(limit).append(' ')
        }
        return head.append(columns).toString()
    }

    /** `order by …` plus the engine's paging clause. */
    open fun compileOrderLimit(orders: List<String>, limit: Int?, offset: Int?, hasUnions: Boolean): String {
        val sql = StringBuilder()
        val paged = limit != null || offset != null
        if (orders.isNotEmpty()) {
            sql.append(" order by ").append(orders.joinToString(", "))
        } else if (dialect == Dialect.SQLSERVER && paged && !usesTop(limit, offset, hasUnions)) {
            sql.append(" order by (select 0)")
        }
        when (dialect) {
            Dialect.SQLSERVER -> if (!usesTop(limit, offset, hasUnions) && paged) {
                sql.append(" offset ").append(offset ?: 0).append(" rows")
                limit?.let { sql.append(" fetch next ").append(it).append(" rows only") }
            }
            Dialect.ORACLE -> {
                offset?.let { sql.append(" offset ").append(it).append(" rows") }
                limit?.let { sql.append(if (offset == null) " fetch first " else " fetch next ").append(it).append(" rows only") }
            }
            else -> {
                if (limit != null) {
                    sql.append(" limit ").append(limit)
                } else if (offset != null) {
                    noLimitSentinel()?.let { sql.append(" limit ").append(it) }
                }
                offset?.let { sql.append(" offset ").append(it) }
            }
        }
        return sql.toString()
    }

    /** Table hint placed right after `from table` (SQL Server locking); empty elsewhere. */
    open fun compileTableLock(lock: LockMode): String {
        if (dialect != Dialect.SQLSERVER) {
            return ""
        }
        return when (lock) {
            LockMode.NONE -> ""
            LockMode.UPDATE -> " with(rowlock,updlock,holdlock)"
            LockMode.SHARED -> " with(rowlock,holdlock)"
        }
    }

    /**
     * Trailing lock clause. SQLite has no row locks and SQL Server locks through [compileTableLock];
     * shared locks are a no-op on H2 and Oracle, whose readers never block writers.
     */
    open fun compileLock(lock: LockMode): String = when {
        lock == LockMode.NONE || dialect == Dialect.SQLITE || dialect == Dialect.SQLSERVER -> ""
        lock == LockMode.UPDATE -> " for update"
        dialect == Dialect.MYSQL || dialect == Dialect.MARIADB -> " lock in share mode"
        dialect == Dialect.H2 || dialect == Dialect.ORACLE -> ""
        else -> " for share"
    }

    /** Expression extracting [part] (`date`, `time`, `day`, `month`, `year`) from the wrapped column. */
    open fun compileDatePart(part: String, wrapped: String): String = when (dialect) {
        Dialect.MYSQL, Dialect.MARIADB -> "$part($wrapped)"
        Dialect.SQLITE -> when (part) {
            "date", "time" -> "$part($wrapped)"
            else -> "cast(strftime('${SQLITE_PARTS.getValue(part)}', $wrapped) as integer)"
        }
        Dialect.SQLSERVER -> when (part) {
            "date", "time" -> "cast($wrapped as $part)"
            else -> "$part($wrapped)"
        }
        Dialect.ORACLE -> when (part) {
            "date" -> "trunc($wrapped)"
            "time" -> "to_char($wrapped, 'HH24:MI:SS')"
            else -> "extract($part from $wrapped)"
        }
        else -> when (part) {
            "date", "time" -> "cast($wrapped as $part)"
            else -> "extract($part from $wrapped)"
        }
    }

    /**
     * Normalizes a `where{Date,Time,Day,Month,Year}` value so it binds with the expression's type:
     * ISO strings become `LocalDate` / `LocalTime`, numeric strings become `Int`.
     */
    open fun datePartValue(part: String, value: Any?): Any? {
        val normalized = when {
            value !is String -> value
            part == "date" -> parseOrKeep(value) { LocalDate.parse(it) }
            part == "time" -> parseOrKeep(value) { LocalTime.parse(it) }
            else -> value.trim().toIntOrNull() ?: value
        }
        if (dialect == Dialect.ORACLE && normalized is LocalTime) {
            return TIME.format(normalized)
        }
        return normalized
    }

    /** Statements that empty [table] (wrapped) and reset its identity where the engine allows. */
    open fun compileTruncate(table: String): List<String> = when (dialect) {
        Dialect.SQLITE -> listOf("delete from $table")
        Dialect.POSTGRES -> listOf("truncate table $table restart identity cascade")
        else -> listOf("truncate table $table")
    }

    /**
     * SQLite keeps AUTOINCREMENT counters in `sqlite_sequence`; returns the reset statement
     * (bound with the raw table name) or null for engines that reset in [compileTruncate].
     */
    open fun compileSequenceReset(): String? =
        if (dialect == Dialect.SQLITE) "delete from sqlite_sequence where name = ?" else null

    /** Query returning a row when the [compileSequenceReset] target exists. */
    open fun compileSequenceTableExists(): String =
        "select 1 from sqlite_master where type = 'table' and name = 'sqlite_sequence'"

    /** Insert of a row that supplies no columns. */
    open fun compileInsertDefaults(table: String): String = when (dialect) {
        Dialect.MYSQL, Dialect.MARIADB -> "insert into $table () values ()"
        Dialect.ORACLE -> throw CadenceException("Oracle cannot insert a row into $table without at least one column value.")
        else -> "insert into $table default values"
    }

    /**
     * Column names to request from `prepareStatement(sql, String[])` for [key], or null to use
     * `RETURN_GENERATED_KEYS`. Oracle needs names — otherwise its driver returns the `ROWID`.
     */
    open fun generatedKeyColumns(key: String): Array<String>? =
        if (dialect == Dialect.ORACLE) arrayOf(key) else null

    /**
     * Converts a bound value to what the engine stores best. SQLite has no date-time type, so
     * temporal values bind as ISO text that its date functions and [CastType] can read back.
     */
    open fun bindValue(value: Any?): Any? {
        if (dialect != Dialect.SQLITE) {
            return value
        }
        return when (value) {
            is Instant -> DATE_TIME.format(LocalDateTime.ofInstant(value, ZoneId.systemDefault()))
            is Timestamp -> DATE_TIME.format(value.toLocalDateTime())
            is LocalDateTime -> DATE_TIME.format(value)
            is LocalDate -> value.toString()
            is LocalTime -> TIME.format(value)
            else -> value
        }
    }

    private fun usesTop(limit: Int?, offset: Int?, hasUnions: Boolean): Boolean =
        dialect == Dialect.SQLSERVER && limit != null && offset == null && !hasUnions

    /** MySQL / SQLite reject `offset` without `limit`; this is each engine's "no limit" value. */
    private fun noLimitSentinel(): String? = when (dialect) {
        Dialect.MYSQL, Dialect.MARIADB -> "18446744073709551615"
        Dialect.SQLITE -> "-1"
        else -> null
    }

    private fun wrapSegment(segment: String): String {
        if (segment == "*" || open.isEmpty()) {
            return segment
        }
        return open + segment.replace(close, close + close) + close
    }

    companion object {
        private val ALIAS = Regex("\\s+as\\s+", RegexOption.IGNORE_CASE)
        private val SQLITE_PARTS = mapOf("day" to "%d", "month" to "%m", "year" to "%Y")
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val DATE_TIME: DateTimeFormatter = DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter()

        private fun parseOrKeep(value: String, parse: (String) -> Any): Any = try {
            parse(value.trim())
        } catch (_: DateTimeParseException) {
            value
        }

        /** Generic ANSI double quotes with standard paging / locking. */
        @JvmField
        val ANSI = Grammar("\"", "\"")

        /** H2 (pair with `DATABASE_TO_LOWER=TRUE` so quoted and unquoted names agree). */
        @JvmField
        val H2 = Grammar("\"", "\"", Dialect.H2)

        @JvmField
        val SQLITE = Grammar("\"", "\"", Dialect.SQLITE)

        @JvmField
        val POSTGRES = Grammar("\"", "\"", Dialect.POSTGRES)

        /** Backticks. */
        @JvmField
        val MYSQL = Grammar("`", "`", Dialect.MYSQL)

        /** Backticks; shares MySQL compilation except where MariaDB differs. */
        @JvmField
        val MARIADB = Grammar("`", "`", Dialect.MARIADB)

        /** Square brackets, `top` / `offset … fetch`, table lock hints. */
        @JvmField
        val SQLSERVER = Grammar("[", "]", Dialect.SQLSERVER)

        /** ANSI double quotes, `offset … fetch` paging (Oracle 12c+). */
        @JvmField
        val ORACLE = Grammar("\"", "\"", Dialect.ORACLE)

        /** No quoting; identifiers are emitted as written. */
        @JvmField
        val NONE = Grammar("", "")

        private val urlPrefixes = linkedMapOf<String, Grammar>()

        private val builtIns = linkedMapOf(
            "jdbc:h2:" to H2,
            "jdbc:sqlite:" to SQLITE,
            "jdbc:postgresql:" to POSTGRES,
            "jdbc:mysql:" to MYSQL,
            "jdbc:mariadb:" to MARIADB,
            "jdbc:sqlserver:" to SQLSERVER,
            "jdbc:microsoft:sqlserver:" to SQLSERVER,
            "jdbc:jtds:sqlserver:" to SQLSERVER,
            "jdbc:oracle:" to ORACLE,
        )

        /**
         * Registers a query [Grammar] for JDBC URLs starting with [jdbcPrefix]
         * (for example `"jdbc:cockroach:"`). Longest matching prefix wins over built-ins.
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
            for ((prefix, grammar) in builtIns) {
                if (lower.startsWith(prefix)) {
                    return grammar
                }
            }
            return ANSI
        }

        /** Built-in grammar for a [Dialect]. */
        @JvmStatic
        fun of(dialect: Dialect): Grammar = GrammarLookup.of(dialect)

        /**
         * Detects the dialect from a JDBC `DatabaseMetaData.getDatabaseProductName()` value
         * (`H2`, `SQLite`, `PostgreSQL`, `MySQL`, `MariaDB`, `Microsoft SQL Server`, `Oracle`).
         */
        @JvmStatic
        fun dialectForProduct(product: String): Dialect {
            val lower = product.lowercase()
            return when {
                lower == "h2" -> Dialect.H2
                lower.contains("mariadb") -> Dialect.MARIADB
                lower.contains("mysql") -> Dialect.MYSQL
                lower.contains("sqlite") -> Dialect.SQLITE
                lower.contains("postgres") -> Dialect.POSTGRES
                lower.contains("sql server") -> Dialect.SQLSERVER
                lower.contains("oracle") -> Dialect.ORACLE
                else -> Dialect.STANDARD
            }
        }
    }
}
