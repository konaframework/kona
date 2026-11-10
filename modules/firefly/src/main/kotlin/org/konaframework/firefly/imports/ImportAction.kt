package org.konaframework.firefly.imports

import org.konaframework.firefly.actions.Action
import java.net.URLEncoder

/**
 * Header action that opens one importer. Filament's `ImportAction::make()->importer(...)`.
 *
 * The button is a link to `{panel}/import?importer={key}`. Register the same importer with
 * [org.konaframework.firefly.Panel.imports] so the panel mounts that page. The name defaults to `import`.
 *
 * This is not [org.konaframework.firefly.FireflyTransfer.importAction], which only opens an empty dialog.
 */
class ImportAction private constructor(name: String) : Action(name) {
    fun importer(importer: Importer): ImportAction = apply {
        validateImporter(importer)
        url(ImportLinks.href(importer.key))
    }

    override fun label(value: String): ImportAction {
        super.label(value)
        return this
    }

    companion object {
        @JvmStatic
        fun make(): ImportAction = make("import")

        @JvmStatic
        fun make(name: String): ImportAction {
            val action = ImportAction(name)
            action.label("Import")
            return action
        }
    }
}

/** Page path captured while a panel page renders, so the link uses the current panel (and tenant). */
internal object ImportContext {
    private val current = ThreadLocal<String?>()

    fun <T> use(path: String, block: () -> T): T {
        val previous = current.get()
        current.set(path)
        try {
            return block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    fun pagePath(): String? = current.get()
}

/** Fallback path remembered when the panel mounts, used if the action is built outside a page render. */
internal object ImportPaths {
    @Volatile
    var base: String? = null

    fun remember(path: String) {
        base = path
    }
}

internal object ImportLinks {
    fun href(key: String): String {
        val base = ImportContext.pagePath() ?: ImportPaths.base ?: "/import"
        return "$base?importer=${encode(key)}"
    }

    fun encode(key: String): String = URLEncoder.encode(key, Charsets.UTF_8)
}
