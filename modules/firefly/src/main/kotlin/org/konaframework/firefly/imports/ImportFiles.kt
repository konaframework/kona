package org.konaframework.firefly.imports

internal data class OpenedImport(val sheet: ImportSheet, val sheetName: String, val sheetCount: Int)

/** CSV, TXT, or an Excel workbook. An `.xls` / `.xlsx` name that is not a workbook is refused. */
internal fun openImport(bytes: ByteArray, extension: String): OpenedImport {
    if (Excel.looksLikeWorkbook(bytes)) {
        val workbook = Excel.read(bytes)
        return OpenedImport(workbook.toImportSheet(), workbook.name, workbook.sheetCount)
    }
    if (extension == "xls" || extension == "xlsx") {
        throw IllegalArgumentException("This spreadsheet could not be read.")
    }
    return OpenedImport(ImportCsv.parse(bytes.toString(Charsets.UTF_8)), "", 1)
}

internal fun importFileLabel(original: String, sheetName: String, sheetCount: Int): String {
    val name = original.ifBlank { "import" }
    return if (sheetCount > 1 && sheetName.isNotBlank()) "$name · $sheetName" else name
}

internal fun blankToNull(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    return if (trimmed.isEmpty()) null else trimmed
}

internal fun textOf(value: Any?): String? = if (value == null) null else value.toString()

internal fun textOrEmpty(value: Any?): String = textOf(value) ?: ""

/** A mapped column index, or null when the field was left blank or is not a number. */
internal fun columnIndex(raw: String?): Int? {
    val text = blankToNull(raw) ?: return null
    return text.toIntOrNull()
}

/** Uses [fallback] when a reader or writer threw without a message. */
internal fun stated(message: String?, fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

internal fun importTooLarge(maxBytes: Int): String {
    val mb = maxBytes / 1_000_000
    val label = if (maxBytes % 1_000_000 == 0 && mb > 0) "$mb MB" else "$maxBytes bytes"
    return "That file is larger than $label."
}
