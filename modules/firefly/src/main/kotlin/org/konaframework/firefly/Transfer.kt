package org.konaframework.firefly

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.support.FireflyException
import org.konaframework.queue.Bus
import org.konaframework.queue.Job
import org.konaframework.queue.ShouldQueue
import java.nio.file.Files
import java.nio.file.Path

/** Writes rows to a CSV file. Implements [ShouldQueue] so a [Bus] can run it on the sync driver. */
class ExportRows(val rows: List<Map<String, String>>, val file: String) : Job, ShouldQueue {
    override fun handle() {
        if (rows.isEmpty()) throw FireflyException("Export has no rows")
        val path = Path.of(file)
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        Files.writeString(path, encodeCsv(rows))
    }
}

/** Reads a CSV file into [rows]. */
class ImportRows(val file: String) : Job, ShouldQueue {
    var rows: List<Map<String, String>> = emptyList()

    override fun handle() {
        val path = Path.of(file)
        if (!Files.isRegularFile(path)) throw FireflyException("Import file is missing [$file]")
        rows = decodeCsv(Files.readString(path))
    }
}

object FireflyTransfer {
    @JvmStatic
    fun exportAction(): Action = Action.make("export").label("Export").modal().modalHeading("Export")

    @JvmStatic
    fun importAction(): Action = Action.make("import").label("Import").modal().modalHeading("Import")

    @JvmStatic
    fun export(rows: List<Map<String, String>>, file: Path, bus: Bus?): String {
        val job = ExportRows(rows, file.toString())
        if (bus == null) {
            job.handle()
            return "sync"
        }
        return bus.dispatch(job)
    }

    @JvmStatic
    fun import(file: Path, bus: Bus?): List<Map<String, String>> {
        val job = ImportRows(file.toString())
        if (bus == null) {
            job.handle()
            return job.rows
        }
        bus.dispatch(job)
        return job.rows
    }
}

internal fun encodeCsv(rows: List<Map<String, String>>): String {
    if (rows.isEmpty()) throw FireflyException("Export has no rows")
    val headers = rows.first().keys.toList()
    val lines = ArrayList<String>()
    lines.add(headers.joinToString(",") { csvCell(it) })
    for (row in rows) {
        lines.add(headers.joinToString(",") { csvCell(row[it] ?: "") })
    }
    return lines.joinToString("\n")
}

internal fun decodeCsv(text: String): List<Map<String, String>> {
    val lines = text.split('\n').filter { it.isNotEmpty() }
    if (lines.isEmpty()) throw FireflyException("Import file is empty")
    val headers = splitCsv(lines.first())
    val out = ArrayList<Map<String, String>>()
    for (line in lines.drop(1)) {
        val cells = splitCsv(line)
        val row = LinkedHashMap<String, String>()
        for (index in headers.indices) {
            row[headers[index]] = if (index < cells.size) cells[index] else ""
        }
        out.add(row)
    }
    return out
}

private fun csvCell(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    if (escaped.contains(",") || escaped.contains("\"")) return "\"$escaped\""
    return escaped
}

private fun splitCsv(line: String): List<String> {
    val cells = ArrayList<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val ch = line[index]
        if (ch == '"') {
            val next = if (index + 1 < line.length) line[index + 1] else ' '
            if (quoted && next == '"') {
                current.append('"')
                index += 1
            } else {
                quoted = !quoted
            }
        } else if (ch == ',' && !quoted) {
            cells.add(current.toString())
            current.clear()
        } else {
            current.append(ch)
        }
        index += 1
    }
    cells.add(current.toString())
    return cells
}
