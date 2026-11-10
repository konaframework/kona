package org.konaframework.firefly.imports

import org.konaframework.firefly.support.FireflyException

/**
 * One field the matcher can bind to a spreadsheet column.
 *
 * [name] is the key the app reads from [ImportRow.values]. [requiredMapping] means the user must
 * pick a column. An optional field can stay on "Don't import", and then [name] is absent from the row.
 */
class ImportColumn internal constructor(
    val name: String,
    val label: String,
    val requiredMapping: Boolean,
    val aliases: List<String>,
) {
    fun label(value: String): ImportColumn {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) throw FireflyException("Import column [$name] label is blank")
        return ImportColumn(name, trimmed, requiredMapping, aliases)
    }

    /** The matcher rejects an empty selection. Optional fields may stay unmapped. */
    fun requiredMapping(): ImportColumn = requiredMapping(true)

    /** When [required] is true the matcher rejects an empty selection. Optional fields may stay unmapped. */
    fun requiredMapping(required: Boolean): ImportColumn =
        ImportColumn(name, label, required, aliases)

    /** Header texts that count as this column when the matcher guesses. Replaces any earlier aliases. */
    fun aliases(vararg aliases: String): ImportColumn {
        val cleaned = aliases.map { it.trim() }.filter { it.isNotEmpty() }
        return ImportColumn(name, label, requiredMapping, cleaned)
    }

    companion object {
        private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        @JvmStatic
        fun make(name: String): ImportColumn {
            val trimmed = name.trim()
            if (!NAME.matches(trimmed)) {
                throw FireflyException(
                    "Import column [$name] must start with a letter or underscore, then letters, digits, and underscores",
                )
            }
            return ImportColumn(trimmed, trimmed, false, emptyList())
        }
    }
}
