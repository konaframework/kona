package org.konaframework.crema.assets

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads Vite’s manifest (`public/build/manifest.json`, or Vite 5+ `public/build/.vite/manifest.json`)
 * and optional `public/hot`, then emits `<script>` / `<link>` tags for entry points.
 *
 * Development (`public/hot` contains the dev-server origin) emits `@vite/client` and the
 * entry module URL Vite serves (the entry path relative to the Vite project root).
 * Production emits `/build/assets/…` from the manifest `file` field — never `/build/` plus
 * the source path.
 */
class AssetManifest(
    private val publicPath: Path,
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    fun hotUrl(): String? {
        val hot = publicPath.resolve("hot")
        if (!Files.isRegularFile(hot)) {
            return null
        }
        val url = Files.readString(hot).trim()
        return url.ifBlank { null }
    }

    fun manifestPath(): Path {
        val build = publicPath.resolve("build")
        val direct = build.resolve("manifest.json")
        if (Files.isRegularFile(direct)) {
            return direct
        }
        val nested = build.resolve(".vite").resolve("manifest.json")
        if (Files.isRegularFile(nested)) {
            return nested
        }
        return direct
    }

    @Suppress("UNCHECKED_CAST")
    fun loadManifest(): Map<String, Map<String, Any?>> {
        val path = manifestPath()
        if (!Files.isRegularFile(path)) {
            throw ViteException("Vite manifest not found: $path")
        }
        val tree = mapper.readTree(Files.readString(path))
        if (!tree.isObject) {
            throw ViteException("Vite manifest must be a JSON object: $path")
        }
        val result = linkedMapOf<String, Map<String, Any?>>()
        tree.fields().forEach { (key, value) ->
            result[key] = mapper.convertValue(value, Map::class.java) as Map<String, Any?>
        }
        return result
    }

    fun entry(name: String): Map<String, Any?> = resolveEntry(loadManifest(), name).second

    fun tags(vararg entries: String): String {
        val hot = hotUrl()
        if (hot != null) {
            return devTags(hot, entries)
        }
        val manifest = loadManifest()
        val parts = mutableListOf<String>()
        entries.forEach { name ->
            val (key, chunk) = resolveEntry(manifest, name)
            appendStyles(parts, manifest, key, linkedSetOf())
            val file = resolveFile(chunk, name)
            val url = publicBuildUrl(file)
            if (file.endsWith(".css")) {
                val link = """<link rel="stylesheet" href="$url">"""
                if (!parts.contains(link)) {
                    parts += link
                }
            } else {
                parts += """<script type="module" src="$url"></script>"""
            }
        }
        return parts.joinToString("\n")
    }

    fun assetUrl(entryName: String): String {
        val hot = hotUrl()
        if (hot != null) {
            return "${hot.trimEnd('/')}/${normalizeEntry(entryName)}"
        }
        val manifest = loadManifest()
        val chunk = resolveEntry(manifest, entryName).second
        return publicBuildUrl(resolveFile(chunk, entryName))
    }

    private fun devTags(hot: String, entries: Array<out String>): String {
        val base = hot.trimEnd('/')
        val parts = mutableListOf(
            """<script type="module" src="$base/@vite/client"></script>""",
        )
        entries.forEach { entry ->
            val path = normalizeEntry(entry)
            val url = "$base/$path"
            if (path.endsWith(".css")) {
                parts += """<link rel="stylesheet" href="$url">"""
            } else {
                parts += """<script type="module" src="$url"></script>"""
            }
        }
        return parts.joinToString("\n")
    }

    private fun resolveEntry(
        manifest: Map<String, Map<String, Any?>>,
        name: String,
    ): Pair<String, Map<String, Any?>> {
        manifest[name]?.let { return name to it }
        val normalized = normalizeEntry(name)
        manifest[normalized]?.let { return normalized to it }
        val match = manifest.entries.firstOrNull { (key, chunk) ->
            val keyNorm = normalizeEntry(key)
            val src = (chunk["src"] as? String)?.let(::normalizeEntry)
            keyNorm == normalized || src == normalized || keyNorm.endsWith("/$normalized")
        } ?: throw ViteException("Unable to locate file in Vite manifest: $name")
        return match.key to match.value
    }

    private fun appendStyles(
        parts: MutableList<String>,
        manifest: Map<String, Map<String, Any?>>,
        chunkKey: String,
        seen: MutableSet<String>,
    ) {
        if (!seen.add(chunkKey)) {
            return
        }
        val chunk = manifest[chunkKey] ?: return
        val css = chunk["css"]
        if (css is List<*>) {
            css.filterNotNull().forEach { href ->
                parts += """<link rel="stylesheet" href="${publicBuildUrl(href.toString())}">"""
            }
        }
        val imports = chunk["imports"]
        if (imports is List<*>) {
            imports.filterNotNull().forEach { imported ->
                appendStyles(parts, manifest, imported.toString(), seen)
            }
        }
    }

    private fun resolveFile(chunk: Map<String, Any?>, name: String): String {
        val file = chunk["file"] ?: throw ViteException("Vite manifest entry missing file: $name")
        return file.toString()
    }

    private fun publicBuildUrl(file: String): String {
        val trimmed = file.trim().replace('\\', '/').removePrefix("/")
        return if (trimmed.startsWith("build/")) "/$trimmed" else "/build/$trimmed"
    }

    private fun normalizeEntry(name: String): String {
        var value = name.trim().replace('\\', '/')
        while (value.startsWith("./")) {
            value = value.removePrefix("./")
        }
        return value.removePrefix("/")
    }
}

class ViteException(message: String) : RuntimeException(message)

/**
 * Facade for [AssetManifest] bound through [Vite.setManifest] / [CremaServiceProvider].
 */
object Vite {
    @Volatile
    private var manifest: AssetManifest? = null

    @JvmStatic
    fun setManifest(value: AssetManifest?) {
        manifest = value
    }

    @JvmStatic
    fun manifest(): AssetManifest =
        manifest ?: throw ViteException("AssetManifest is not set; register CremaServiceProvider or Vite.setManifest")

    @JvmStatic
    fun reset() {
        manifest = null
    }

    /** Emit script/link tags for one or more Vite entry points. */
    @JvmStatic
    fun tags(vararg entries: String): String = manifest().tags(*entries)

    /** Resolved URL for an entry (dev server when `public/hot` exists). */
    @JvmStatic
    fun asset(entry: String): String = manifest().assetUrl(entry)
}
