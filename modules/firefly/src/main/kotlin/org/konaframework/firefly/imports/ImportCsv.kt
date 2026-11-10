package org.konaframework.firefly.imports

object ImportCsv {
    @JvmStatic
    fun parse(raw: String): ImportSheet {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return ImportSheet(emptyList(), emptyList())
        val delimiter = delimiter(text)
        val records = readRecords(text, delimiter)
        if (records.isEmpty()) return ImportSheet(emptyList(), emptyList())
        val headers = uniqueHeadings(records.first().cells)
        val width = headers.size
        val rows = records.drop(1).map { record ->
            val cells = record.cells.map { it.trim() }
            val padded = ArrayList<String>(width)
            for (index in 0 until width) {
                padded += if (index < cells.size) cells[index] else ""
            }
            ImportSheetRow(record.line, padded)
        }
        return ImportSheet(headers, rows)
    }

    /** Field name to column index, for [fields] only. The first matching header wins. */
    @JvmStatic
    fun suggest(headers: List<String>, fields: List<ImportColumn>): Map<String, Int> {
        val aliasToKey = linkedMapOf<String, String>()
        for (field in fields) {
            for (alias in field.aliases + field.name + field.label) {
                val normal = normalize(alias)
                if (normal !in aliasToKey) aliasToKey[normal] = field.name
            }
        }
        val taken = mutableSetOf<String>()
        val out = linkedMapOf<String, Int>()
        headers.forEachIndexed { index, header ->
            val key = aliasToKey[normalize(header)] ?: return@forEachIndexed
            if (key in taken) return@forEachIndexed
            taken += key
            out[key] = index
        }
        return out
    }

    private fun delimiter(text: String): Char {
        val line = headerLine(text)
        val commas = line.count { it == ',' }
        val semis = line.count { it == ';' }
        val tabs = line.count { it == '\t' }
        return when {
            tabs > commas && tabs >= semis -> '\t'
            semis > commas -> ';'
            else -> ','
        }
    }

    private fun readRecords(text: String, delimiter: Char): List<ImportSheetRow> {
        val records = mutableListOf<ImportSheetRow>()
        val cell = StringBuilder()
        val cells = mutableListOf<String>()
        var line = 1
        var recordLine = 1
        var quoted = false
        var i = 0
        fun finishCell() {
            cells += cell.toString()
            cell.setLength(0)
        }
        fun finishRecord() {
            finishCell()
            if (cells.any { it.isNotBlank() }) records += ImportSheetRow(recordLine, cells.toList())
            cells.clear()
            recordLine = line
        }
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    if (ch == '\n') line++
                    cell.append(ch)
                }
            } else when (ch) {
                '"' -> if (cell.isEmpty()) quoted = true else cell.append(ch)
                delimiter -> finishCell()
                '\n' -> {
                    finishRecord()
                    line++
                    recordLine = line
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || cells.isNotEmpty()) finishRecord()
        return records
    }

    internal fun headerLine(text: String): String {
        for (candidate in text.lineSequence()) {
            if (candidate.isNotBlank()) return candidate
        }
        return ""
    }

    internal fun normalize(header: String): String =
        header.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")
}
