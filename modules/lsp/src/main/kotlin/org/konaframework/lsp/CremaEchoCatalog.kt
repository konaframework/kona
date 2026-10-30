package org.konaframework.lsp

/**
 * Echo helpers and conventional variables offered inside `{{ }}` / `{!! !!}`.
 * Keep in sync with kona `org.konaframework.crema.CremaHelpers` and kona-idea
 * `CremaEchoCatalog`.
 */
object CremaEchoCatalog {
    data class Item(
        val name: String,
        val insertText: String,
        val detail: String,
        val helper: Boolean,
    )

    @JvmField
    val HELPERS: List<Item> =
        listOf(
            Item("url", "url('')", "url(path) · Crema helper", true),
            Item("route", "route('')", "route(name) · Crema helper", true),
            Item("asset", "asset('')", "asset(path) · Crema helper", true),
            Item("csrf_token", "csrf_token()", "csrf_token() · Crema helper", true),
            Item("old", "old('')", "old(key) · Crema helper", true),
            Item("env", "env('')", "env(key) · Crema helper", true),
            Item("config", "config('')", "config(key) · Crema helper", true),
            Item("trans", "trans('')", "trans(key) · Crema helper", true),
            Item("__", "__('')", "__(key) · Crema helper", true),
            Item("lang", "lang('')", "lang(key) · Crema helper", true),
            Item("icon", "icon('')", "icon(name, class?) · Crema helper", true),
            Item("isset", "isset()", "isset(name) · Crema helper", true),
            Item("empty", "empty()", "empty(name) · Crema helper", true),
        )

    @JvmField
    val VARIABLES: List<Item> =
        listOf(
            Item("errors", "errors", "Validation errors map", false),
            Item("user", "user", "Authenticated user", false),
            Item("auth_user", "auth_user", "Authenticated user", false),
            Item("_token", "_token", "CSRF token", false),
            Item("asset_url", "asset_url", "CDN / asset prefix", false),
            Item("slot", "slot", "Component default slot", false),
            Item("loop", "loop", "Current loop meta", false),
            Item("loop.index", "loop.index", "Loop index (0-based)", false),
            Item("loop.iteration", "loop.iteration", "Loop iteration (1-based)", false),
            Item("loop.first", "loop.first", "True on the first iteration", false),
            Item("loop.last", "loop.last", "True on the last iteration", false),
            Item("loop.even", "loop.even", "True on even iterations", false),
            Item("loop.odd", "loop.odd", "True on odd iterations", false),
            Item("loop.count", "loop.count", "Loop item count", false),
            Item("loop.remaining", "loop.remaining", "Items after this one", false),
            Item("loop.depth", "loop.depth", "Nested loop depth", false),
            Item("loop.parent", "loop.parent", "Parent loop meta", false),
        )

    @JvmStatic
    fun completions(prefix: String): List<Item> {
        val needle = prefix.lowercase()
        return (HELPERS + VARIABLES).filter { item ->
            needle.isEmpty() || item.name.lowercase().startsWith(needle)
        }
    }
}
