package org.konaframework.firefly.imports

import org.apache.poi.EncryptedDocumentException
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

/** One worksheet, with values as Excel displays them and row numbers from the sheet. */
data class ExcelSheet(
    val name: String,
    val headers: List<String>,
    val rows: List<ExcelRow>,
    val sheetCount: Int,
) {
    fun toImportSheet(): ImportSheet =
        ImportSheet(headers, rows.map { ImportSheetRow(it.number, it.cells) })
}

data class ExcelRow(val number: Int, val cells: List<String>)

/**
 * Reads `.xls` and `.xlsx`: identify the workbook from its bytes, take the first sheet that has
 * rows, calculate formulas, and format each cell as Excel shows it. Password-protected files are refused.
 */
object Excel {
    @JvmStatic
    fun looksLikeWorkbook(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val ole = bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        val zip = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        return ole || zip
    }

    @JvmStatic
    fun read(bytes: ByteArray): ExcelSheet {
        if (bytes.isEmpty()) throw IllegalArgumentException("The file is empty.")
        val workbook = try {
            WorkbookFactory.create(ByteArrayInputStream(bytes))
        } catch (ex: EncryptedDocumentException) {
            throw IllegalArgumentException("This spreadsheet is password protected.")
        } catch (ex: Exception) {
            throw IllegalArgumentException("This spreadsheet could not be read.")
        }
        workbook.use { return readWorkbook(it) }
    }

    private fun readWorkbook(workbook: org.apache.poi.ss.usermodel.Workbook): ExcelSheet {
        val count = workbook.numberOfSheets
        val sheet = (0 until count)
            .map { workbook.getSheetAt(it) }
            .firstOrNull { it.physicalNumberOfRows > 0 }
            ?: return ExcelSheet("", emptyList(), emptyList(), count)
        val formatter = DataFormatter()
        val evaluator = workbook.creationHelper.createFormulaEvaluator()
        val records = mutableListOf<ExcelRow>()
        for (row in sheet) {
            val last = row.lastCellNum.toInt()
            if (last <= 0) continue
            val cells = (0 until last).map { index ->
                val cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return@map ""
                formatter.formatCellValue(cell, evaluator).trim()
            }
            if (cells.all { it.isEmpty() }) continue
            records += ExcelRow(row.rowNum + 1, cells)
        }
        if (records.isEmpty()) return ExcelSheet(sheet.sheetName, emptyList(), emptyList(), count)
        var width = 0
        for (record in records) {
            if (record.cells.size > width) width = record.cells.size
        }
        val headers = uniqueHeadings(pad(records.first().cells, width))
        val rows = records.drop(1).map { ExcelRow(it.number, pad(it.cells, width)) }
        return ExcelSheet(sheet.sheetName, headers, rows, count)
    }

    private fun pad(cells: List<String>, width: Int): List<String> {
        val out = ArrayList<String>(width)
        for (index in 0 until width) {
            out += if (index < cells.size) cells[index] else ""
        }
        return out
    }
}
