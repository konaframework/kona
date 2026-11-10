package org.konaframework.firefly.imports

import org.konaframework.firefly.notifications.FireflyNotification
import org.konaframework.http.Request
import org.konaframework.http.RequestSession

internal data class ImportPageModel(
    val title: String,
    val listLabel: String,
    val listPath: String,
    val html: String,
)

internal sealed interface ImportResult {
    data class Page(val model: ImportPageModel) : ImportResult
    data class Redirect(val location: String) : ImportResult
    data class Download(val filename: String, val body: String) : ImportResult
    data class Text(val status: Int, val body: String) : ImportResult
}

/**
 * Upload, match, test, and import for the importers registered on one panel.
 * Session values are strings, maps, lists, and numbers.
 */
internal class ImportRuntime(
    private val importers: List<Importer>,
    private val maxBytes: Int,
    private val pagePath: String,
    private val csrfField: String,
) {
    fun show(request: Request): ImportResult {
        val session = request.session()
        val requestedKey = blankToNull(request.input("importer"))
        val requested = find(requestedKey)
        if (session != null && requested != null) {
            val storedKey = textOf(session.get(MODULE))
            if (requested.key != storedKey) clearFile(session)
            session.put(MODULE, requested.key)
        }
        val importer = if (requestedKey != null) {
            requested
        } else if (session == null) {
            null
        } else {
            find(textOf(session.get(MODULE)))
        }
        return ImportResult.Page(pageModel(importer, session))
    }

    fun upload(request: Request): ImportResult {
        val session = request.session()
        if (session == null) return ImportResult.Text(500, "Session required")
        val importer = postedOrStored(request, session)
        if (importer == null) return fail(session, "Choose an importer.", null)
        val file = request.file("file")
        if (file == null || file.isEmpty()) return fail(session, "Choose a CSV or Excel file to import.", importer)
        if (file.size > maxBytes.toLong()) return fail(session, importTooLarge(maxBytes), importer)
        val opened = try {
            openImport(file.contents, file.extension())
        } catch (ex: IllegalArgumentException) {
            return fail(session, stated(ex.message, "That file could not be read."), importer)
        }
        if (opened.sheet.headers.isEmpty()) {
            return fail(session, "The file needs a header row and at least one column.", importer)
        }
        val label = importFileLabel(file.clientOriginalName, opened.sheetName, opened.sheetCount)
        session.put(MODULE, importer.key)
        session.put(SHEET, opened.sheet.toSession())
        session.put(NAME, label)
        session.put(MAP, null)
        return ImportResult.Redirect(url(importer))
    }

    fun run(request: Request): ImportResult {
        val session = request.session()
        if (session == null) return ImportResult.Text(500, "Session required")
        val importer = postedOrStored(request, session)
        if (importer == null) return fail(session, "Choose an importer.", null)
        session.put(MODULE, importer.key)
        val sheet = ImportSheet.fromSession(session.get(SHEET)) ?: return fail(session, "Upload a file first.", importer)
        val mapping = importer.columns.associate { column ->
            column.name to columnIndex(request.input("map_${column.name}"))
        }
        session.put(MAP, mapping.mapValues { (_, value) -> value?.toString().orEmpty() })
        val blocked = ImportMapping.issues(importer.columns, mapping, sheet)
        val resolution = if (blocked != null) {
            ImportFailures(blocked)
        } else {
            importer.resolve(ImportMapping.rows(importer.columns, mapping, sheet))
        }
        if (request.input("commit") == "1") {
            if (!resolution.ready) {
                session.flash(REPORT, resolution.toFlash(importer.key))
                return ImportResult.Redirect(url(importer))
            }
            return try {
                importer.apply(resolution)
                clearFile(session)
                FireflyNotification.make("import-${importer.key}")
                    .title("${importer.listLabel} imported")
                    .body(resolution.summary())
                    .success()
                    .send(session)
                session.flash(DONE, resolution.summary())
                ImportResult.Redirect(url(importer))
            } catch (ex: IllegalArgumentException) {
                fail(session, stated(ex.message, "The import did not finish."), importer)
            }
        }
        session.flash(REPORT, resolution.toFlash(importer.key))
        return ImportResult.Redirect(url(importer))
    }

    fun cancel(request: Request): ImportResult {
        val session = request.session() ?: return ImportResult.Redirect(pagePath)
        val importer = postedOrStored(request, session)
        clearFile(session)
        return ImportResult.Redirect(url(importer))
    }

    fun template(request: Request): ImportResult {
        val importer = find(request.input("importer")?.trim())
            ?: return ImportResult.Text(422, "Choose an importer.")
        return ImportResult.Download("${importer.key}-import.csv", importer.template())
    }

    private fun pageModel(importer: Importer?, session: RequestSession?): ImportPageModel {
        val title = if (importer == null) "Import" else importer.title
        val sheet = if (session == null) null else ImportSheet.fromSession(session.get(SHEET))
        val report = reportFor(session, importer)
        val html = importMarkup(
            importers = importers,
            importer = importer,
            sheet = sheet,
            fileName = textOrEmpty(if (session == null) null else session.get(NAME)),
            csrf = csrfField,
            error = textOrEmpty(if (session == null) null else session.get(ERROR)),
            done = textOrEmpty(if (session == null) null else session.get(DONE)),
            storedMap = session?.get(MAP) as? Map<*, *>,
            report = report,
            pagePath = pagePath,
        )
        return ImportPageModel(
            title = title,
            listLabel = if (importer == null) "" else importer.listLabel,
            listPath = if (importer == null) "" else importer.listPath,
            html = html,
        )
    }

    private fun reportFor(session: RequestSession?, importer: Importer?): Map<*, *>? {
        if (importer == null || session == null) return null
        val report = session.get(REPORT) as? Map<*, *>
        if (report == null) return null
        return if (textOf(report["module"]) == importer.key) report else null
    }

    private fun postedOrStored(request: Request, session: RequestSession): Importer? {
        val posted = blankToNull(request.input("importer"))
        val stored = blankToNull(textOf(session.get(MODULE)))
        return find(if (posted != null) posted else stored)
    }

    private fun find(key: String?): Importer? {
        val name = blankToNull(key)
        if (name == null) return null
        return importers.firstOrNull { it.key == name }
    }

    private fun fail(session: RequestSession, message: String, importer: Importer?): ImportResult {
        session.flash(ERROR, message)
        return ImportResult.Redirect(url(importer))
    }

    private fun url(importer: Importer?): String =
        if (importer == null) pagePath else "$pagePath?importer=${ImportLinks.encode(importer.key)}"

    private fun clearFile(session: RequestSession) {
        session.put(SHEET, null)
        session.put(NAME, null)
        session.put(MAP, null)
    }

    private companion object {
        const val SHEET = "firefly_import_sheet"
        const val NAME = "firefly_import_name"
        const val MAP = "firefly_import_map"
        const val MODULE = "firefly_import_module"
        const val REPORT = "firefly_import_report"
        const val ERROR = "firefly_import_error"
        const val DONE = "firefly_import_done"
    }
}
