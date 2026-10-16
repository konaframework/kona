package io.konaframework.crema

import io.konaframework.crema.assets.Vite
import io.konaframework.crema.assets.ViteException

/**
 * Vite tag helper for Crema `@vite(...)` directives.
 *
 * When [Vite.setManifest] / [CremaServiceProvider] has bound an [io.konaframework.crema.assets.AssetManifest],
 * tags honor `public/hot` and `public/build/manifest.json`. Otherwise falls back to `/build/...` paths.
 */
object ViteTags {
    @JvmStatic
    fun tags(vararg entries: String): String =
        try {
            Vite.tags(*entries)
        } catch (_: ViteException) {
            entries.joinToString("\n") { tag(it) }
        }

    @JvmStatic
    fun tags(entries: Collection<String>): String = tags(*entries.toTypedArray())

    @JvmStatic
    fun tag(entry: String): String {
        return try {
            // Prefer full manifest/hot resolution when available.
            Vite.tags(entry)
        } catch (_: ViteException) {
            val trimmed = entry.trim().removePrefix("/")
            val href =
                if (trimmed.startsWith("build/")) {
                    "/$trimmed"
                } else {
                    "/build/$trimmed"
                }
            if (trimmed.endsWith(".css")) {
                """<link rel="stylesheet" href="$href">"""
            } else {
                """<script type="module" src="$href"></script>"""
            }
        }
    }
}
