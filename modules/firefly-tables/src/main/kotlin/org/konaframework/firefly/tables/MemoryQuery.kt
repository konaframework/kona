package org.konaframework.firefly.tables

import org.konaframework.firefly.support.FireflyException

/**
 * In-memory query for a table that has no Cadence model.
 * This is not a SQL builder: there is no `toSql`, `join`, or `whereHas`.
 */
open class MemoryQuery internal constructor(source: List<Map<String, Any?>>) {
    private val rows = source.map { LinkedHashMap(it) }
    private val predicates = ArrayList<(Map<String, Any?>) -> Boolean>()
    private val sorts = ArrayList<Pair<String, String>>()
    private var trashedMode: String? = null

    fun where(field: String, value: Any?): MemoryQuery = where(field, "=", value)

    fun where(field: String, operator: String, value: Any?): MemoryQuery = apply {
        val op = operator.lowercase()
        predicates.add { row -> matches(row[field], op, value) }
    }

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
        predicates.add { row ->
            columns.any { column ->
                val value = row[column] ?: return@any false
                value.toString().lowercase().contains(needle)
            }
        }
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
        val filtered = rows.filter { row ->
            if (!trashAllows(row)) return@filter false
            predicates.all { it(row) }
        }
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

    private fun numbers(field: String): List<Double> =
        get().mapNotNull { row -> (row[field] as? Number)?.toDouble() }

    private fun trashAllows(row: Map<String, Any?>): Boolean = when (trashedMode) {
        "only" -> row["deleted_at"] != null
        "without" -> row["deleted_at"] == null
        else -> true
    }

    private fun compareRows(left: Map<String, Any?>, right: Map<String, Any?>): Int {
        for ((field, direction) in sorts) {
            val compared = compare(left[field], right[field])
            if (compared != 0) return if (direction == "desc") -compared else compared
        }
        return 0
    }

    private fun matches(actual: Any?, operator: String, expected: Any?): Boolean {
        if (operator == "null") return actual == null
        if (operator == "not-null") return actual != null
        if (operator == "like") return like(actual, expected, false)
        if (operator == "not like") return like(actual, expected, true)
        if (operator == "=" || operator == "==") return same(actual, expected)
        if (operator == "!=" || operator == "<>") return !same(actual, expected)
        val compared = compare(actual, expected)
        return when (operator) {
            ">" -> compared > 0
            ">=" -> compared >= 0
            "<" -> compared < 0
            "<=" -> compared <= 0
            else -> throw FireflyException("Unknown operator [$operator]")
        }
    }

    private fun like(actual: Any?, expected: Any?, negate: Boolean): Boolean {
        if (actual == null || expected == null) return negate
        val text = actual.toString().lowercase()
        val pattern = expected.toString().lowercase().replace("%", "")
        val found = text.contains(pattern)
        return if (negate) !found else found
    }

    private fun same(actual: Any?, expected: Any?): Boolean {
        if (actual == expected) return true
        if (actual == null || expected == null) return false
        if (actual is Number && expected is Number) return actual.toDouble() == expected.toDouble()
        return actual.toString() == expected.toString()
    }

    private fun compare(actual: Any?, expected: Any?): Int {
        if (actual == null && expected == null) return 0
        if (actual == null) return -1
        if (expected == null) return 1
        if (actual is Number && expected is Number) return actual.toDouble().compareTo(expected.toDouble())
        return actual.toString().compareTo(expected.toString())
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
