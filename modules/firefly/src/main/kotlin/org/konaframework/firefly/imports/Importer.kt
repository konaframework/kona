package org.konaframework.firefly.imports

import org.konaframework.firefly.support.FireflyException

/**
 * One list's importer. The framework parses the file and renders the matcher.
 * The app decides what a row means and what to write.
 *
 * [resolve] must not write. [apply] is called only for Import, and only when [ImportResolution.issues]
 * is empty. The framework does not guess a match key, a model, or a transaction.
 */
abstract class Importer {
    abstract val key: String
    abstract val title: String
    abstract val listLabel: String
    abstract val listPath: String
    abstract val columns: List<ImportColumn>
    abstract val uploadText: String
    abstract val matchText: String

    /** Sample CSV. The header row is this importer's columns, in declaration order, unless you override it. */
    open fun template(): String = columns.joinToString(",") { it.name } + "\n"

    /** Dry run. Return create and update counts and any row problems. Do not write. */
    abstract fun resolve(rows: List<ImportRow>): ImportResolution

    /** Persist a resolution that has no problems. Not called for Test, and not called when any row has a problem. */
    abstract fun apply(resolution: ImportResolution)
}

/** One data row after the matcher has bound columns. A missing [values] key was left on Don't import. */
data class ImportRow(val line: Int, val values: Map<String, String>)

/** A problem on a file line. Line 1 is the header when the problem is the mapping itself. */
data class ImportProblem(val line: Int, val message: String)

/**
 * What [Importer.resolve] decided. [summary] is the sentence Test and Import show.
 * [creates] and [updates] are counts of records the app would write.
 */
interface ImportResolution {
    val creates: Int
    val updates: Int
    val issues: List<ImportProblem>
    val ready: Boolean get() = issues.isEmpty()
    fun summary(): String
}

internal fun validateImporter(importer: Importer) {
    if (!IMPORT_KEY.matches(importer.key)) {
        throw FireflyException(
            "Importer key [${importer.key}] must start with a letter or underscore, then letters, digits, and underscores",
        )
    }
    if (importer.title.isBlank()) throw FireflyException("Importer [${importer.key}] title is blank")
    if (importer.listLabel.isBlank()) throw FireflyException("Importer [${importer.key}] list label is blank")
    if (importer.listPath.isBlank()) throw FireflyException("Importer [${importer.key}] list path is blank")
    if (importer.uploadText.isBlank()) throw FireflyException("Importer [${importer.key}] upload text is blank")
    if (importer.matchText.isBlank()) throw FireflyException("Importer [${importer.key}] match text is blank")
    if (importer.columns.isEmpty()) throw FireflyException("Importer [${importer.key}] has no columns")
    val names = HashSet<String>()
    for (column in importer.columns) {
        if (!names.add(column.name)) {
            throw FireflyException("Importer [${importer.key}] repeats column [${column.name}]")
        }
    }
}

private val IMPORT_KEY = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

/** Mapping failed before [Importer.resolve]. Creates and updates stay at zero. */
internal class ImportFailures(override val issues: List<ImportProblem>) : ImportResolution {
    override val creates: Int = 0
    override val updates: Int = 0

    override fun summary(): String {
        val noun = if (issues.size == 1) "problem" else "problems"
        return "The file still has ${issues.size} $noun."
    }
}

internal fun ImportResolution.toFlash(module: String): Map<String, Any?> = mapOf(
    "module" to module,
    "creates" to creates,
    "updates" to updates,
    "ready" to ready,
    "summary" to summary(),
    "issues" to issues.map { mapOf("line" to it.line, "message" to it.message) },
)
