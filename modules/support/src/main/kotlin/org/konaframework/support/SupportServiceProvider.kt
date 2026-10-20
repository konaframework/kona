package org.konaframework.support

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import org.konaframework.support.date.KonaDate
import org.konaframework.support.lang.Lang
import org.konaframework.support.lang.LangFiles
import org.konaframework.support.lang.Translator
import java.nio.file.Files
import java.nio.file.Path
import java.time.DateTimeException
import java.time.ZoneId

/**
 * Registers shared support services (translator, default timezone).
 *
 * When `lang.path` is set, or `{base}/lang` exists, locale files are loaded into the
 * shared [Translator] via [LangFiles]. [Lang] is wired at register time so
 * `Translations.__` / [org.konaframework.support.lang.translate] work immediately after boot.
 *
 * `app.locale` / `app.fallback_locale` seed the translator; `app.timezone` seeds [KonaDate].
 */
class SupportServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        val locale = app.config.getString("app.locale", "en").ifBlank { "en" }
        val fallback = app.config.getString("app.fallback_locale", "en").ifBlank { "en" }
        val translator = Translator(locale, fallback)
        val langPath = resolveLangPath(app)
        if (langPath != null) {
                LangFiles.loadInto(translator, langPath)
        }
        Lang.setTranslator(translator)
        applyTimezone(app)
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

        internal fun applyTimezone(app: Application) {
            val raw = app.config.getString("app.timezone", "UTC").ifBlank { "UTC" }
            val zone = try {
                ZoneId.of(raw)
            } catch (_: DateTimeException) {
                ZoneId.of("UTC")
            }
            KonaDate.setDefaultTimezone(zone)
        }
    }
}
