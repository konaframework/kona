package org.konaframework.firefly.infolists

import org.konaframework.cadence.Cadence

internal enum class RecordAggregate {
    COUNT,
    AVG,
    MIN,
    MAX,
    SUM,
}

internal class AggregateRequest(
    val kind: RecordAggregate,
    val relationship: String,
    val column: String,
)

private object SkipRelated

internal fun aggregateRecord(record: Any?, request: AggregateRequest, entryName: String): Any? = when (record) {
    null -> emptyAggregate(request.kind)
    is Cadence<*> -> cadenceAggregate(record, request, entryName)
    is Map<*, *> -> mapAggregate(record, request, entryName)
    else -> emptyAggregate(request.kind)
}

private fun mapAggregate(record: Map<*, *>, request: AggregateRequest, entryName: String): Any? {
    if (record.containsKey(request.relationship)) {
        val reduced = reduceRelated(record[request.relationship], request)
        if (reduced !== SkipRelated) return reduced
    }
    return storedAggregate(record, request, entryName)
}

private fun storedAggregate(record: Map<*, *>, request: AggregateRequest, entryName: String): Any? {
    val alias = defaultAlias(request)
    if (record.containsKey(alias)) return record[alias]
    if (entryName != alias && record.containsKey(entryName)) return record[entryName]
    return emptyAggregate(request.kind)
}

private fun cadenceAggregate(model: Cadence<*>, request: AggregateRequest, entryName: String): Any? {
    if (model.relationLoaded(request.relationship)) {
        val reduced = reduceRelated(model.getRelation(request.relationship), request)
        if (reduced !== SkipRelated) return reduced
    }
    val alias = defaultAlias(request)
    if (model.hasAttribute(alias)) return model.getRawAttribute(alias)
    if (entryName != alias && model.hasAttribute(entryName)) return model.getRawAttribute(entryName)
    if (!model.exists()) return emptyAggregate(request.kind)
    return loadOnto(model, request, entryName, alias)
}

private fun loadOnto(model: Cadence<*>, request: AggregateRequest, entryName: String, alias: String): Any? {
    val spec = if (request.kind == RecordAggregate.COUNT) {
        if (entryName == alias) request.relationship else "${request.relationship} as $entryName"
    } else if (entryName == alias) {
        "${request.relationship}:${request.column}"
    } else {
        "${request.relationship} as $entryName:${request.column}"
    }
    when (request.kind) {
        RecordAggregate.COUNT -> model.loadCount(spec)
        RecordAggregate.AVG -> model.loadAvg(spec)
        RecordAggregate.MIN -> model.loadMin(spec)
        RecordAggregate.MAX -> model.loadMax(spec)
        RecordAggregate.SUM -> model.loadSum(spec)
    }
    val key = if (entryName == alias) alias else entryName
    return model.getRawAttribute(key)
}

private fun reduceRelated(related: Any?, request: AggregateRequest): Any? {
    val rows = when (related) {
        null -> emptyList()
        is Collection<*> -> related.toList()
        is Array<*> -> related.asList()
        is Cadence<*> -> listOf(related)
        is Map<*, *> -> listOf(related)
        else -> return SkipRelated
    }
    return reduceRows(rows, request)
}

private fun reduceRows(rows: List<Any?>, request: AggregateRequest): Any? = when (request.kind) {
    RecordAggregate.COUNT -> rows.size
    RecordAggregate.SUM -> sumOf(rows, request.column)
    RecordAggregate.AVG -> averageOf(rows, request.column)
    RecordAggregate.MIN -> extreme(rows, request.column, lowest = true)
    RecordAggregate.MAX -> extreme(rows, request.column, lowest = false)
}

private fun sumOf(rows: List<Any?>, column: String): Number {
    var sum = 0.0
    var any = false
    for (row in rows) {
        val number = toNumber(columnValue(row, column)) ?: continue
        sum += number
        any = true
    }
    if (!any) return 0
    return compactNumber(sum)
}

private fun averageOf(rows: List<Any?>, column: String): Number? {
    var sum = 0.0
    var count = 0
    for (row in rows) {
        val number = toNumber(columnValue(row, column)) ?: continue
        sum += number
        count += 1
    }
    if (count == 0) return null
    return compactNumber(sum / count)
}

private fun extreme(rows: List<Any?>, column: String, lowest: Boolean): Number? {
    var best: Double? = null
    for (row in rows) {
        val number = toNumber(columnValue(row, column)) ?: continue
        val current = best
        if (current == null || (if (lowest) number < current else number > current)) best = number
    }
    val winner = best ?: return null
    return compactNumber(winner)
}

private fun columnValue(row: Any?, column: String): Any? {
    if (row == null) return null
    if (row is Map<*, *>) return if (row.containsKey(column)) row[column] else null
    return attributeOf(row, column)
}

private fun defaultAlias(request: AggregateRequest): String {
    val column = request.column
    return when (request.kind) {
        RecordAggregate.COUNT -> "${request.relationship}_count"
        RecordAggregate.AVG -> "${request.relationship}_avg_$column"
        RecordAggregate.MIN -> "${request.relationship}_min_$column"
        RecordAggregate.MAX -> "${request.relationship}_max_$column"
        RecordAggregate.SUM -> "${request.relationship}_sum_$column"
    }
}

private fun emptyAggregate(kind: RecordAggregate): Any? = when (kind) {
    RecordAggregate.COUNT, RecordAggregate.SUM -> 0
    RecordAggregate.AVG, RecordAggregate.MIN, RecordAggregate.MAX -> null
}

/** Whole numbers render as integers; fractional values stay decimals. */
internal fun compactNumber(value: Double): Number {
    val asInt = value.toInt()
    if (asInt.toDouble() == value) return asInt
    return value
}
