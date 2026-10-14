package dev.kona.inertia.assets

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads Vite’s `public/build/manifest.json` (and optional `public/hot`) and emits
 * `<script>` / `<link>` tags for entry points.
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

    fun manifestPath(): Path = publicPath.resolve("build").resolve("manifest.json")

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

    fun entry(name: String): Map<String, Any?> {
        val manifest = loadManifest()
        return manifest[name]
            ?: throw ViteException("Unable to locate file in Vite manifest: $name")
    }

    fun tags(vararg entries: String): String {
        val hot = hotUrl()
        if (hot != null) {
            val base = hot.trimEnd('/')
            val parts = mutableListOf(
                """<script type="module" src="$base/@vite/client"></script>""",
            )
            entries.forEach { entry ->
                parts += """<script type="module" src="$base/$entry"></script>"""
            }
            return parts.joinToString("\n")
        }
        val parts = mutableListOf<String>()
        entries.forEach { name ->
            val chunk = entry(name)
            val file = resolveFile(chunk, name)
            val css = chunk["css"]
            if (css is List<*>) {
                css.forEach { href ->
                    parts += """<link rel="stylesheet" href="/build/$href">"""
                }
            }
            parts += """<script type="module" src="/build/$file"></script>"""
        }
        return parts.joinToString("\n")
    }

    fun assetUrl(entryName: String): String {
        val hot = hotUrl()
        if (hot != null) {
            return "${hot.trimEnd('/')}/$entryName"
        }
        return "/build/${resolveFile(entry(entryName), entryName)}"
    }

    private fun resolveFile(chunk: Map<String, Any?>, name: String): String {
        val file = chunk["file"]
        if (file == null) {
            throw ViteException("Vite manifest entry missing file: $name")
        }
        return file.toString()
    }
}

class ViteException(message: String) : RuntimeException(message)

/**
 * Facade for [AssetManifest] bound through [Vite.setManifest] / [InertiaServiceProvider].
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
        manifest ?: throw ViteException("AssetManifest is not set; register InertiaServiceProvider or Vite.setManifest")

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
