package org.konaframework.firefly.imports

/**
 * A spreadsheet saved as headers and rows with line numbers.
 * Session stores this map (strings, lists, numbers), not the sheet object.
 */
data class ImportSheet(val headers: List<String>, val rows: List<ImportSheetRow>) {
    fun toSession(): Map<String, Any?> = mapOf(
        "headers" to headers,
        "rows" to rows.map { mapOf("line" to it.line, "cells" to it.cells) },
    )

    companion object {
        fun fromSession(raw: Any?): ImportSheet? {
            if (raw == null) return null
            return when (raw) {
                is String -> ImportCsv.parse(raw).takeIf { it.headers.isNotEmpty() }
                is Map<*, *> -> {
                    val headers = (raw["headers"] as? List<*>)?.map { it?.toString().orEmpty() } ?: return null
                    if (headers.isEmpty()) return null
                    val rows = (raw["rows"] as? List<*>)?.mapNotNull { item ->
                        val map = item as? Map<*, *> ?: return@mapNotNull null
                        val line = (map["line"] as? Number)?.toInt() ?: return@mapNotNull null
                        val cells = (map["cells"] as? List<*>)?.map { it?.toString().orEmpty() } ?: return@mapNotNull null
                        ImportSheetRow(line, cells)
                    } ?: return null
                    ImportSheet(headers, rows)
                }
                else -> null
            }
        }
    }
}

data class ImportSheetRow(val line: Int, val cells: List<String>)

internal fun uniqueHeadings(cells: List<String>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return cells.mapIndexed { index, raw ->
        val base = raw.trim().ifBlank { "Column ${index + 1}" }
        val count = (seen[base.lowercase()] ?: 0) + 1
        seen[base.lowercase()] = count
        if (count == 1) base else "$base ($count)"
    }
}
