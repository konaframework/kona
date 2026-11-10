package org.konaframework.firefly.imports

/**
 * Turns a stored column map into rows the importer can read.
 * A required column left empty blocks the file. Optional columns that are missing stay off the row.
 */
object ImportMapping {
    @JvmStatic
    fun issues(
        columns: List<ImportColumn>,
        mapping: Map<String, Int?>,
        sheet: ImportSheet,
    ): List<ImportProblem>? = issues(columns, mapping, sheet, emptyList())

    @JvmStatic
    fun issues(
        columns: List<ImportColumn>,
        mapping: Map<String, Int?>,
        sheet: ImportSheet,
        extra: List<ImportProblem>,
    ): List<ImportProblem>? {
        if (sheet.headers.isEmpty()) {
            return listOf(ImportProblem(1, "The file needs a header row."))
        }
        val clean = clean(mapping, sheet.headers.size)
        val found = mutableListOf<ImportProblem>()
        if (sheet.rows.isEmpty()) found += ImportProblem(1, "The file has no data rows.")
        for (column in columns) {
            if (column.requiredMapping && clean[column.name] == null) {
                found += ImportProblem(1, "Match a column to ${column.label}.")
            }
        }
        found += extra
        val blocked = sheet.rows.isEmpty() ||
            columns.any { it.requiredMapping && clean[it.name] == null } ||
            extra.isNotEmpty()
        return if (blocked) found else null
    }

    @JvmStatic
    fun rows(columns: List<ImportColumn>, mapping: Map<String, Int?>, sheet: ImportSheet): List<ImportRow> {
        val clean = clean(mapping, sheet.headers.size)
        return sheet.rows.map { row ->
            val values = linkedMapOf<String, String>()
            for (column in columns) {
                val index = clean[column.name] ?: continue
                val cell = if (index < row.cells.size) row.cells[index] else ""
                values[column.name] = cell.trim()
            }
            ImportRow(row.line, values)
        }
    }

    @JvmStatic
    fun clean(mapping: Map<String, Int?>, width: Int): Map<String, Int?> =
        mapping.mapValues { (_, index) ->
            when {
                index == null -> null
                index in 0 until width -> index
                else -> null
            }
        }
}
