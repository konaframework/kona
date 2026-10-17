package org.konaframework.support.lang

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple localization translator (Laravel `__()` / Translator shape).
 *
 * Lines are loaded as `locale.key = message` maps; placeholders use `:name`.
 */
class Translator(
    private var locale: String = "en",
    private var fallback: String = "en",
) {
    constructor(locale: String) : this(locale, "en")

    constructor() : this("en", "en")
    private val lines = ConcurrentHashMap<String, MutableMap<String, String>>()

    fun getLocale(): String = locale

    fun setLocale(locale: String): Translator {
        this.locale = locale
        return this
    }

    fun getFallback(): String = fallback

    fun setFallback(fallback: String): Translator {
        this.fallback = fallback
        return this
    }

    fun addLines(locale: String, lines: Map<String, String>): Translator {
        val bag = this.lines.getOrPut(locale.lowercase(Locale.ROOT)) { ConcurrentHashMap() }
        bag.putAll(lines)
        return this
    }

    fun has(key: String, locale: String = this.locale): Boolean =
        lines[locale.lowercase(Locale.ROOT)]?.containsKey(key) == true

    fun get(key: String): String = get(key, emptyMap(), locale)

    fun get(key: String, replace: Map<String, Any?>): String = get(key, replace, locale)

    fun get(key: String, replace: Map<String, Any?>, locale: String): String {
        val template = resolve(key, locale) ?: return key
        return replace(template, replace)
    }

    /** Alias matching Laravel `__`. */
    fun trans(key: String): String = get(key)

    fun trans(key: String, replace: Map<String, Any?>): String = get(key, replace)

    fun trans(key: String, replace: Map<String, Any?>, locale: String): String =
        get(key, replace, locale)

    fun choice(key: String, number: Int, replace: Map<String, Any?> = emptyMap(), locale: String = this.locale): String {
        val template = resolve(key, locale) ?: return key
        val chosen = choose(template, number)
        val withCount = replace.toMutableMap()
        if (!withCount.containsKey("count")) {
            withCount["count"] = number
        }
        return replace(chosen, withCount)
    }

    private fun resolve(key: String, locale: String): String? {
        val primary = lines[locale.lowercase(Locale.ROOT)]?.get(key)
        if (primary != null) {
            return primary
        }
        if (locale != fallback) {
            return lines[fallback.lowercase(Locale.ROOT)]?.get(key)
        }
        return null
    }

    private fun replace(template: String, replace: Map<String, Any?>): String {
        var result = template
        for ((name, value) in replace) {
            val text = if (value == null) "" else value.toString()
            result = result.replace(":$name", text)
        }
        return result
    }

    private fun choose(template: String, number: Int): String {
        val parts = template.split('|')
        if (parts.size == 1) {
            return template
        }
        return if (number == 1) parts[0].trim() else parts.last().trim()
    }
}

/** Global translator holder for `__()`-style helpers. */
object Lang {
    @Volatile
    private var translator: Translator = Translator()

    @JvmStatic
    fun setTranslator(translator: Translator) {
        this.translator = translator
    }

    @JvmStatic
    fun translator(): Translator = translator

    @JvmStatic
    @JvmOverloads
    fun get(key: String, replace: Map<String, Any?> = emptyMap(), locale: String? = null): String =
        if (locale == null) translator.get(key, replace) else translator.get(key, replace, locale)

    @JvmStatic
    fun setLocale(locale: String) {
        translator.setLocale(locale)
    }

    @JvmStatic
    fun getLocale(): String = translator.getLocale()
}
