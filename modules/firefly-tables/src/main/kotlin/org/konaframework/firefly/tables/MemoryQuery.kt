package org.konaframework.firefly.tables

import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * In-memory query for a table that has no Cadence model.
 * This is not a SQL builder: there is no `toSql`, `join`, or `whereHas`.
 * Clauses follow SQL precedence: `and` binds tighter than `or`, and [whereNested] groups.
 */
open class MemoryQuery internal constructor(source: List<Map<String, Any?>>) {
    private val rows = source.map { LinkedHashMap(it) }
    private val clauses = ArrayList<Clause>()
    private val sorts = ArrayList<Pair<String, String>>()
    private var trashedMode: String? = null

    private class Clause(val or: Boolean, val test: (Map<String, Any?>) -> Boolean)

    fun where(field: String, value: Any?): MemoryQuery = where(field, "=", value)

    fun where(field: String, operator: String, value: Any?): MemoryQuery = add(false, field, operator, value)

    fun orWhere(field: String, value: Any?): MemoryQuery = orWhere(field, "=", value)

    fun orWhere(field: String, operator: String, value: Any?): MemoryQuery = add(true, field, operator, value)

    /** Row predicate for constraints the field operators cannot express. */
    fun whereRow(predicate: Predicate<Map<String, Any?>>): MemoryQuery = apply {
        clauses.add(Clause(false) { row -> predicate.test(row) })
    }

    fun orWhereRow(predicate: Predicate<Map<String, Any?>>): MemoryQuery = apply {
        clauses.add(Clause(true) { row -> predicate.test(row) })
    }

    fun whereNull(field: String): MemoryQuery = where(field, "null", null)

    fun whereNotNull(field: String): MemoryQuery = where(field, "not-null", null)

    fun whereIn(field: String, values: Collection<*>): MemoryQuery = where(field, "in", values)

    fun whereNotIn(field: String, values: Collection<*>): MemoryQuery = where(field, "not in", values)

    fun orWhereIn(field: String, values: Collection<*>): MemoryQuery = orWhere(field, "in", values)

    fun whereBetween(field: String, from: Any?, to: Any?): MemoryQuery = whereNested { group ->
        group.where(field, ">=", from).where(field, "<=", to)
    }

    /** Compare the `yyyy-mm-dd` part of a date / date-time value. */
    fun whereDate(field: String, operator: String, value: Any?): MemoryQuery = datePart(field, "date", operator, value)

    fun whereMonth(field: String, operator: String, value: Any?): MemoryQuery = datePart(field, "month", operator, value)

    fun whereYear(field: String, operator: String, value: Any?): MemoryQuery = datePart(field, "year", operator, value)

    fun whereDay(field: String, operator: String, value: Any?): MemoryQuery = datePart(field, "day", operator, value)

    /** Parenthesized `and (…)` group, so `orWhere` inside the group stays inside it. */
    fun whereNested(group: Consumer<MemoryQuery>): MemoryQuery = nested(false, group)

    fun orWhereNested(group: Consumer<MemoryQuery>): MemoryQuery = nested(true, group)

    fun orderBy(field: String, direction: String = "asc"): MemoryQuery = apply {
        val normalized = direction.lowercase()
        if (normalized != "asc" && normalized != "desc") {
            throw FireflyException("Sort direction must be asc or desc")
        }
        sorts.add(field to normalized)
    }

    fun search(term: String, columns: List<String>): MemoryQuery = apply {
        if (term.isEmpty() || columns.isEmpty()) return@apply
        val needle = term.lowercase()
        clauses.add(
            Clause(false) { row ->
                columns.any { column ->
                    val value = dig(row, column) ?: return@any false
                    value.toString().lowercase().contains(needle)
                }
            },
        )
    }

    fun trashed(mode: String): MemoryQuery = apply {
        when (mode) {
            "with" -> trashedMode = "with"
            "only" -> trashedMode = "only"
            "without", "" -> trashedMode = "without"
            else -> throw FireflyException("Unknown trashed mode [$mode]")
        }
    }

    fun get(): List<Map<String, Any?>> {
        val filtered = rows.filter { row -> trashAllows(row) && accepts(row) }
        if (sorts.isEmpty()) return filtered
        return filtered.sortedWith { left, right -> compareRows(left, right) }
    }

    fun paginate(page: Int, perPage: Int): TablePage<Map<String, Any?>> {
        if (page < 1) throw FireflyException("Page must be at least 1")
        if (perPage < 1) throw FireflyException("Page size must be at least 1")
        val all = get()
        val from = (page - 1) * perPage
        val slice = if (from >= all.size) emptyList() else all.subList(from, minOf(from + perPage, all.size))
        return TablePage(slice, all.size.toLong(), perPage, page)
    }

    fun count(): Long = get().size.toLong()

    fun sum(field: String): Any? = numbers(field).sum()

    fun avg(field: String): Any? {
        val values = numbers(field)
        if (values.isEmpty()) return null
        return values.sum() / values.size
    }

    fun min(field: String): Any? = numbers(field).minOrNull()

    fun max(field: String): Any? = numbers(field).maxOrNull()

    private fun add(or: Boolean, field: String, operator: String, value: Any?): MemoryQuery = apply {
        val op = operator.lowercase()
        if (op !in OPERATORS) throw FireflyException("Unknown operator [$operator]")
        clauses.add(Clause(or) { row -> matches(dig(row, field), op, value) })
    }

    private fun nested(or: Boolean, group: Consumer<MemoryQuery>): MemoryQuery = apply {
        val inner = MemoryQuery(emptyList())
        group.accept(inner)
        if (inner.clauses.isEmpty()) return@apply
        clauses.add(Clause(or) { row -> inner.accepts(row) })
    }

    private fun datePart(field: String, part: String, operator: String, value: Any?): MemoryQuery = apply {
        val op = operator.lowercase()
        if (op !in COMPARISONS) throw FireflyException("Unknown operator [$operator]")
        val expected = partOf(value, part)
        clauses.add(
            Clause(false) { row ->
                val actual = partOf(dig(row, field), part)
                if (actual == null || expected == null) false else compareOp(actual.compareTo(expected), op)
            },
        )
    }

    /** SQL precedence: each `or` starts a new `and` run; any satisfied run accepts the row. */
    private fun accepts(row: Map<String, Any?>): Boolean {
        var accepted = false
        var run = true
        clauses.forEachIndexed { index, clause ->
            if (index > 0 && clause.or) {
                accepted = accepted || run
                run = true
            }
            run = run && clause.test(row)
        }
        return accepted || run
    }

    private fun numbers(field: String): List<Double> =
        get().mapNotNull { row -> (row[field] as? Number)?.toDouble() }

    private fun trashAllows(row: Map<String, Any?>): Boolean = when (trashedMode) {
        "only" -> row["deleted_at"] != null
        "without" -> row["deleted_at"] == null
        else -> true
    }

    private fun compareRows(left: Map<String, Any?>, right: Map<String, Any?>): Int {
        for ((field, direction) in sorts) {
            val compared = compare(dig(left, field), dig(right, field))
            if (compared != 0) return if (direction == "desc") -compared else compared
        }
        return 0
    }

    /** Nested map dig for dotted paths (`project.name`) used by searchable/sortable relation columns. */
    private fun dig(row: Map<String, Any?>, path: String): Any? {
        if (!path.contains('.')) return row[path]
        var current: Any? = row
        for (segment in path.split('.')) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                else -> return null
            }
        }
        return current
    }

    /** SQL semantics: apart from `null` / `not-null`, a null column never matches (not even `!=`). */
    private fun matches(actual: Any?, operator: String, expected: Any?): Boolean = when {
        operator == "null" -> actual == null
        operator == "not-null" -> actual != null
        actual == null -> false
        else -> when (operator) {
            "like" -> like(actual, expected, false)
            "not like" -> like(actual, expected, true)
            "=", "==" -> same(actual, expected)
            "!=", "<>" -> !same(actual, expected)
            "in" -> within(actual, expected)
            "not in" -> !within(actual, expected)
            else -> compareOp(compareValues(actual, expected), operator)
        }
    }

    private fun compareOp(compared: Int, operator: String): Boolean = when (operator) {
        ">" -> compared > 0
        ">=" -> compared >= 0
        "<" -> compared < 0
        "<=" -> compared <= 0
        "=", "==" -> compared == 0
        else -> compared != 0
    }

    private fun within(actual: Any, expected: Any?): Boolean {
        val options = expected as? Collection<*> ?: return same(actual, expected)
        return options.any { same(actual, it) }
    }

    /**
     * Case-insensitive `like`: `ada%` starts with, `%ada` ends with, `%ada%` (or no `%`) contains.
     */
    private fun like(actual: Any, expected: Any?, negate: Boolean): Boolean {
        if (expected == null) return false
        val text = actual.toString().lowercase()
        val raw = expected.toString().lowercase()
        val starts = raw.startsWith("%")
        val ends = raw.endsWith("%") && raw.length > 1
        val needle = raw.removePrefix("%").removeSuffix("%")
        val found = when {
            starts == ends -> text.contains(needle)
            starts -> text.endsWith(needle)
            else -> text.startsWith(needle)
        }
        return if (negate) !found else found
    }

    private fun same(actual: Any, expected: Any?): Boolean {
        if (actual == expected) return true
        if (expected == null) return false
        if (actual is Number && expected is Number) return actual.toDouble() == expected.toDouble()
        val left = actual.toString()
        val right = expected.toString()
        if (left == right) return true
        if (actual is Boolean || expected is Boolean) return truthy(left) == truthy(right)
        return false
    }

    private fun truthy(text: String): Boolean? = when (text.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }

    private fun compare(actual: Any?, expected: Any?): Int {
        if (actual == null && expected == null) return 0
        if (actual == null) return -1
        if (expected == null) return 1
        if (actual is Number && expected is Number) return actual.toDouble().compareTo(expected.toDouble())
        return actual.toString().compareTo(expected.toString())
    }

    /** Operator comparison: numeric text (query-string filter input) compares as numbers. */
    private fun compareValues(actual: Any, expected: Any?): Int {
        if (expected == null) return compare(actual, null)
        val left = actual.toString().toDoubleOrNull()
        val right = expected.toString().toDoubleOrNull()
        if (left != null && right != null) return left.compareTo(right)
        return compare(actual, expected)
    }

    private fun partOf(value: Any?, part: String): String? {
        if (value == null) return null
        val text = value.toString()
        val match = ISO_DATE.find(text)
        if (match == null) {
            if (part == "date") return null
            val number = text.toIntOrNull() ?: return null
            return number.toString().padStart(4, '0')
        }
        val (year, month, day) = match.destructured
        return when (part) {
            "date" -> "$year-$month-$day"
            "month" -> month.padStart(4, '0')
            "day" -> day.padStart(4, '0')
            else -> year
        }
    }

    companion object {
        private val ISO_DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
        private val COMPARISONS = setOf("=", "==", "!=", "<>", ">", ">=", "<", "<=")
        private val OPERATORS = COMPARISONS + setOf("null", "not-null", "like", "not like", "in", "not in")
    }
}

open class TablePage<T>(
    val records: List<T>,
    val total: Long,
    val perPage: Int,
    val page: Int,
) {
    val lastPage: Int = maxOf(if (perPage == 0) 1 else ((total + perPage - 1) / perPage).toInt(), 1)

    fun isEmpty(): Boolean = records.isEmpty()

    fun hasMorePages(): Boolean = page < lastPage
}
