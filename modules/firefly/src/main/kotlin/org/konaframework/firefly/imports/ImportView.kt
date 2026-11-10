package org.konaframework.firefly.imports

import org.konaframework.firefly.escapeHtml

internal fun importMarkup(
    importers: List<Importer>,
    importer: Importer?,
    sheet: ImportSheet?,
    fileName: String,
    csrf: String,
    error: String,
    done: String,
    storedMap: Map<*, *>?,
    report: Map<*, *>?,
    pagePath: String,
): String {
    val notice = when {
        error.isNotBlank() -> """<p class="ni-error">${escapeHtml(error)}</p>"""
        done.isNotBlank() -> """<p class="ni-done">${escapeHtml(done)}</p>"""
        else -> ""
    }
    val body = when {
        importer == null -> directory(importers, pagePath)
        sheet == null || sheet.headers.isEmpty() -> upload(importer, csrf, pagePath)
        else -> match(importer, sheet, fileName, csrf, storedMap, report, pagePath)
    }
    return notice + body
}

private fun directory(importers: List<Importer>, pagePath: String): String {
    val links = importers.joinToString("") { importer ->
        val href = "$pagePath?importer=${ImportLinks.encode(importer.key)}"
        """<li><a href="$href">${escapeHtml(importer.title)}</a></li>"""
    }
    return """
        <div class="ni">
          <p>Import belongs to one list. Open it from the list, or choose one here. The matcher shows only that list's fields.</p>
          <ul>$links</ul>
        </div>
        ${style()}
    """.trimIndent()
}

private fun upload(importer: Importer, csrf: String, pagePath: String): String = """
    <div class="ni">
      <p>${escapeHtml(importer.uploadText)}</p>
      <p>Excel files are read as workbooks: the first sheet that has rows is used, formulas are calculated, and each cell is taken as Excel displays it.</p>
      <p><a href="${escapeHtml(pagePath)}/template?importer=${ImportLinks.encode(importer.key)}">Download a sample file</a></p>
      <form method="post" action="${escapeHtml(pagePath)}" enctype="multipart/form-data" class="ni-upload">
        $csrf
        <input type="hidden" name="importer" value="${escapeHtml(importer.key)}" />
        <label>File
          <input class="ff-input" type="file" name="file" accept=".csv,.txt,.xls,.xlsx,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" required />
        </label>
        <button type="submit" class="ff-action-submit">Upload and match fields</button>
      </form>
    </div>
    ${style()}
""".trimIndent()

private fun match(
    importer: Importer,
    sheet: ImportSheet,
    fileName: String,
    csrf: String,
    storedMap: Map<*, *>?,
    report: Map<*, *>?,
    pagePath: String,
): String {
    val suggested = ImportCsv.suggest(sheet.headers, importer.columns)
    val options = sheet.headers.mapIndexed { index, header ->
        val sample = firstSample(sheet, index)
        val label = if (sample == null) header else "$header — ${sample.take(40)}"
        index to label
    }
    val fields = importer.columns.joinToString("") { field ->
        val selected = if (storedMap == null || !storedMap.containsKey(field.name)) {
            suggested[field.name]
        } else {
            val raw = storedMap[field.name]
            if (raw == null) null else raw.toString().toIntOrNull()
        }
        val placeholder = if (field.requiredMapping) "Select a column" else "Don't import"
        val choices = buildString {
            val placeholderSelected = if (selected == null) " selected" else ""
            append("""<option value=""$placeholderSelected>${escapeHtml(placeholder)}</option>""")
            options.forEach { (index, label) ->
                val on = if (selected == index) " selected" else ""
                append("""<option value="$index"$on>${escapeHtml(label)}</option>""")
            }
        }
        val mark = if (field.requiredMapping) """ <span class="ni-req">*</span>""" else ""
        val required = if (field.requiredMapping) " required" else ""
        """
            <label>${escapeHtml(field.label)}$mark</label>
            <select class="ff-input" name="map_${escapeHtml(field.name)}"$required>$choices</select>
        """.trimIndent()
    }
    val previewHead = sheet.headers.joinToString("") { """<th>${escapeHtml(it)}</th>""" }
    val previewBody = sheet.rows.take(8).joinToString("") { row ->
        "<tr>" + row.cells.joinToString("") { """<td>${escapeHtml(it)}</td>""" } + "</tr>"
    }
    return """
        <div class="ni">
          <p><strong>${escapeHtml(fileName)}</strong>. ${escapeHtml(importer.matchText)}</p>
          <p>Test checks every row and writes nothing. Import writes the file only when every row is valid.</p>
          <form method="post" action="${escapeHtml(pagePath)}/cancel">$csrf<input type="hidden" name="importer" value="${escapeHtml(importer.key)}" /><button type="submit" class="ff-action-cancel">Choose a different file</button></form>
          <form method="post" action="${escapeHtml(pagePath)}/run" class="ni-match">
            $csrf
            <input type="hidden" name="importer" value="${escapeHtml(importer.key)}" />
            <div class="ni-map">$fields</div>
            ${reportHtml(report)}
            <div class="ni-actions">
              <button type="submit" name="commit" value="0" class="ff-action-cancel">Test</button>
              <button type="submit" name="commit" value="1" class="ff-action-submit">Import</button>
            </div>
          </form>
          <h2>File preview</h2>
          <div class="ni-scroll"><table class="ff-table"><thead><tr>$previewHead</tr></thead><tbody>$previewBody</tbody></table></div>
        </div>
        ${style()}
    """.trimIndent()
}

private fun reportHtml(report: Map<*, *>?): String {
    if (report == null) return ""
    val summary = report["summary"]?.toString().orEmpty()
    val rawIssues = report["issues"]
    val issues = if (rawIssues is List<*>) rawIssues else emptyList<Any>()
    val rows = issues.take(40).joinToString("") { item ->
        val map = item as? Map<*, *> ?: return@joinToString ""
        val line = textOrEmpty(map["line"])
        val message = textOrEmpty(map["message"])
        "<tr><td>${escapeHtml(line)}</td><td>${escapeHtml(message)}</td></tr>"
    }
    val more = if (issues.size > 40) """<p>${issues.size - 40} more.</p>""" else ""
    val table = if (rows.isEmpty()) "" else """
        <table class="ff-table"><thead><tr><th>Row</th><th>Problem</th></tr></thead><tbody>$rows</tbody></table>
        $more
    """.trimIndent()
    return """<div class="ni-report"><p>${escapeHtml(summary)}</p>$table</div>"""
}

private fun firstSample(sheet: ImportSheet, index: Int): String? {
    for (row in sheet.rows) {
        val cell = if (index < row.cells.size) row.cells[index] else ""
        val trimmed = cell.trim()
        if (trimmed.isNotEmpty()) return trimmed
    }
    return null
}

private fun style(): String = """
    <style>
      .ni { display: flex; flex-direction: column; gap: 16px; max-width: 960px; }
      .ni-upload, .ni-match, .ni-actions { display: flex; flex-direction: column; gap: 12px; align-items: flex-start; }
      .ni-map { display: grid; grid-template-columns: minmax(160px, 220px) minmax(220px, 1fr); gap: 10px 16px; align-items: center; width: min(720px, 100%); }
      .ni-req { color: #9f1239; }
      .ni-error { color: #9f1239; margin: 0; }
      .ni-done { margin: 0; }
      .ni-scroll { overflow-x: auto; }
      .ni-report { display: flex; flex-direction: column; gap: 8px; }
      .ni-actions { flex-direction: row; }
    </style>
""".trimIndent()
