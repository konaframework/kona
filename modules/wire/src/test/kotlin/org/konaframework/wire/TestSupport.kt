package org.konaframework.wire

import org.konaframework.wire.hydration.Coercion
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.MapViewFinder

internal fun brew(
    views: Map<String, String> = emptyMap(),
    config: WireConfig = WireConfig(key = "test-key"),
    container: org.konaframework.core.Container? = null,
): WireManager = WireManager(config, CremaEngine(MapViewFinder(views.toMutableMap())), container)

@Suppress("UNCHECKED_CAST")
internal fun parse(json: String): Map<String, Any?> = Coercion.mapper.readValue(json, Map::class.java) as Map<String, Any?>

/** Extracts and decodes an HTML attribute value. */
internal fun attr(html: String, name: String): String {
    val m = Regex("""$name="([^"]*)"""").find(html) ?: error("Missing $name in $html")
    return m.groupValues[1].replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&amp;", "&")
}

internal fun snapshotOf(html: String): Map<String, Any?> = parse(attr(html, "wire:snapshot"))

internal fun call(method: String, vararg params: Any?): Map<String, Any?> =
    mapOf("path" to "", "method" to method, "params" to params.toList(), "metadata" to emptyMap<String, Any?>())
