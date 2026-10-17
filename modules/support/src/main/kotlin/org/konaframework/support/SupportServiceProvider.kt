package org.konaframework.support

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import org.konaframework.support.lang.Lang
import org.konaframework.support.lang.LangFiles
import org.konaframework.support.lang.Translator
import java.nio.file.Files
import java.nio.file.Path

/**
 * Registers shared support services (translator).
 *
 * When `lang.path` is set, or `{base}/lang` exists, locale files are loaded into the
 * shared [Translator] via [LangFiles]. [Lang] is wired at register time so
 * `Translations.__` / [org.konaframework.support.lang.translate] work immediately after boot.
 */
class SupportServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        val translator = Translator()
        val langPath = resolveLangPath(app)
        if (langPath != null) {
                LangFiles.loadInto(translator, langPath)
        }
        Lang.setTranslator(translator)
        app.container.instance(Translator::class.java, translator)
    }

    companion object {
        internal fun resolveLangPath(app: Application): Path? {
            val configured = app.config.getString("lang.path")
            if (!configured.isNullOrBlank()) {
                val path = Path.of(configured)
                val resolved = if (path.isAbsolute) path else app.basePath.resolve(configured)
                if (Files.exists(resolved)) {
                    return resolved
                }
            }
            val defaultPath = app.basePath.resolve("lang")
            return if (Files.exists(defaultPath)) defaultPath else null
        }
    }
}
