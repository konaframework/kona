package org.konaframework.crema

import org.konaframework.core.Env
import org.konaframework.core.Kona
import org.konaframework.http.Url
import org.konaframework.icons.Icons
import org.konaframework.support.lang.Lang

/**
 * Echo helpers available inside Crema `{{ }}` / `{!! !!}` (and as call expressions
 * in directives). Keep names in sync with kona-lsp [CremaEchoCatalog] and
 * kona-idea `CremaEchoCatalog`.
 */
object CremaHelpers {
    data class Helper(
        val name: String,
        val signature: String,
        val insertText: String,
        val detail: String,
        val callable: Boolean = true,
    )

    data class Variable(
        val name: String,
        val detail: String,
    )

    @JvmField
    val HELPERS: List<Helper> =
        listOf(
            Helper("url", "url(path)", "url('')", "Rooted or absolute URL"),
            Helper("route", "route(name)", "route('')", "Named route URL"),
            Helper("asset", "asset(path)", "asset('')", "Public asset URL"),
            Helper("csrf_token", "csrf_token()", "csrf_token()", "Session CSRF token"),
            Helper("old", "old(key)", "old('')", "Flashed old input"),
            Helper("env", "env(key)", "env('')", "Environment value"),
            Helper("config", "config(key)", "config('')", "Config value"),
            Helper("trans", "trans(key)", "trans('')", "Translation string"),
            Helper("__", "__(key)", "__('')", "Translation string"),
            Helper("lang", "lang(key)", "lang('')", "Translation string"),
            Helper("icon", "icon(name, class?)", "icon('')", "Inline SVG from kona-icons (use {!! !!})"),
            Helper("isset", "isset(name)", "isset()", "True when the path is non-null", callable = false),
            Helper("empty", "empty(name)", "empty()", "True when the value is not truthy", callable = false),
        )

    @JvmField
    val VARIABLES: List<Variable> =
        listOf(
            Variable("errors", "Validation errors map"),
            Variable("user", "Authenticated user"),
            Variable("auth_user", "Authenticated user"),
            Variable("_token", "CSRF token"),
            Variable("asset_url", "CDN / asset prefix"),
            Variable("slot", "Component default slot"),
            Variable("loop", "Current loop meta"),
            Variable("loop.index", "Loop index (0-based)"),
            Variable("loop.iteration", "Loop iteration (1-based)"),
            Variable("loop.first", "True on the first iteration"),
            Variable("loop.last", "True on the last iteration"),
            Variable("loop.even", "True on even iterations"),
            Variable("loop.odd", "True on odd iterations"),
            Variable("loop.count", "Loop item count"),
            Variable("loop.remaining", "Items after this one"),
            Variable("loop.depth", "Nested loop depth"),
            Variable("loop.parent", "Parent loop meta"),
        )

    private val CALLABLE: Set<String> = HELPERS.filter { it.callable }.map { it.name }.toSet()

    private val IDENT = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    @JvmStatic
    fun isCallable(name: String): Boolean = name in CALLABLE

    @JvmStatic
    fun completions(prefix: String): List<String> {
        val needle = prefix.lowercase()
        val helpers =
            HELPERS.map { it.name }.filter { needle.isEmpty() || it.lowercase().startsWith(needle) }
        val variables =
            VARIABLES.map { it.name }.filter { needle.isEmpty() || it.lowercase().startsWith(needle) }
        return (helpers + variables).distinct()
    }

    /**
     * Parses `name(args)` at the top level. Returns null when [expr] is not a call.
     */
    @JvmStatic
    fun parseCall(expr: String): Pair<String, String>? {
        val trimmed = expr.trim()
        if (!trimmed.endsWith(')')) {
            return null
        }
        val open = trimmed.indexOf('(')
        if (open <= 0) {
            return null
        }
        val name = trimmed.substring(0, open).trim()
        if (!IDENT.matches(name)) {
            return null
        }
        var depth = 0
        var quote: Char? = null
        var i = open
        while (i < trimmed.length) {
            val ch = trimmed[i]
            val q = quote
            when {
                q != null -> {
                    if (ch == q) {
                        quote = null
                    }
                }
                ch == '\'' || ch == '"' -> quote = ch
                ch == '(' -> depth++
                ch == ')' -> {
                    depth--
                    if (depth == 0) {
                        if (i != trimmed.lastIndex) {
                            return null
                        }
                        return name to trimmed.substring(open + 1, i)
                    }
                }
            }
            i++
        }
        return null
    }

    @JvmStatic
    fun call(name: String, args: List<Any?>, context: CremaContext): Any? =
        when (name) {
            "url" -> url(stringArg(args, 0))
            "route" -> route(stringArg(args, 0))
            "asset" -> asset(stringArg(args, 0), context)
            "csrf_token" -> csrfToken(context)
            "old" -> old(stringArg(args, 0) ?: "", args.getOrNull(1), context)
            "env" -> env(stringArg(args, 0) ?: "", args.getOrNull(1)?.toString())
            "config" -> config(stringArg(args, 0) ?: "")
            "trans", "__" -> trans(stringArg(args, 0) ?: "", args.getOrNull(1))
            "lang" -> lang(stringArg(args, 0) ?: "", args.getOrNull(1), context)
            "icon" -> icon(stringArg(args, 0), stringArg(args, 1))
            else -> null
        }

    @JvmStatic
    fun url(path: String?): String {
        val target = path?.trim().orEmpty().ifEmpty { "/" }
        return try {
            Url.url(target)
        } catch (_: IllegalStateException) {
            rooted(target)
        }
    }

    @JvmStatic
    fun route(name: String?): String {
        val named = name?.trim().orEmpty()
        if (named.isEmpty()) {
            return ""
        }
        return try {
            Url.route(named)
        } catch (_: Exception) {
            rooted(named)
        }
    }

    @JvmStatic
    fun asset(path: String?, context: CremaContext): String {
        val relative = (path ?: "").trim().trimStart('/')
        val base = (context.get("asset_url") ?: context.get("assetUrl") ?: "").toString().trimEnd('/')
        return if (base.isEmpty()) rooted(relative) else "$base/${relative.ifEmpty { "" }}".trimEnd('/')
    }

    @JvmStatic
    fun csrfToken(context: CremaContext): String {
        val token = context.get("_token") ?: context.get("csrf_token") ?: return ""
        return token.toString()
    }

    @JvmStatic
    fun old(key: String, default: Any?, context: CremaContext): Any? {
        for (bagName in listOf("old", "_old_input", "oldInput")) {
            val bag = context.get(bagName)
            if (bag is Map<*, *> && bag.containsKey(key)) {
                return bag[key]
            }
        }
        return default
    }

    @JvmStatic
    fun env(key: String, default: String?): String? {
        if (key.isEmpty()) {
            return default
        }
        return Env.get(key, default)
    }

    @JvmStatic
    fun config(key: String): Any? {
        if (key.isEmpty()) {
            return null
        }
        val app =
            try {
                Kona.app()
            } catch (_: IllegalStateException) {
                return null
            }
        return app.config.get<Any>(key)
    }

    @JvmStatic
    fun trans(key: String, replace: Any?): String {
        if (key.isEmpty()) {
            return ""
        }
        @Suppress("UNCHECKED_CAST")
        val map = replace as? Map<String, Any?> ?: emptyMap()
        return Lang.get(key, map)
    }

    @JvmStatic
    fun lang(key: String, replace: Any?, context: CremaContext): String {
        val override = context.get("lang")
        if (override is Map<*, *> && override.containsKey(key)) {
            return (override[key] ?: "").toString()
        }
        return trans(key, replace)
    }

    /**
     * Renders an SVG from [kona-icons](org.konaframework.icons.Icons).
     * Echo with `{!! icon('heroicon-o-bell') !!}` so the markup is not escaped.
     */
    @JvmStatic
    fun icon(name: String?, className: String? = null): String {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ""
        }
        val svg = Icons.svg(trimmed)
        if (!className.isNullOrBlank()) {
            svg.className(className.trim())
        }
        return svg.toHtml()
    }

    internal fun rooted(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun stringArg(args: List<Any?>, index: Int): String? {
        if (index >= args.size) {
            return null
        }
        return args[index]?.toString()
    }
}
