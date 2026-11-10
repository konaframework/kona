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
        val missing = columns.filter { it.requiredMapping && clean[it.name] == null }.map { it.label }
        if (missing.isNotEmpty()) found += ImportProblem(1, unmatchedColumnsMessage(missing))
        found += extra
        val blocked = sheet.rows.isEmpty() || missing.isNotEmpty() || extra.isNotEmpty()
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

    /** Names every required column the user left on Select a column. This is not the importer's help text. */
    @JvmStatic
    fun unmatchedColumnsMessage(labels: List<String>): String {
        val named = when (labels.size) {
            1 -> labels[0]
            2 -> "${labels[0]} and ${labels[1]}"
            else -> labels.dropLast(1).joinToString(", ") + ", and " + labels.last()
        }
        val verb = if (labels.size == 1) "has" else "have"
        return "$named $verb to be matched to a column."
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
