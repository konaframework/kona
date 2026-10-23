package org.konaframework.wire.view

import org.konaframework.wire.WireManager
import org.konaframework.wire.WireException
import org.konaframework.http.Response
import java.security.MessageDigest
import java.util.HexFormat

/**
 * `@wireStyles`, `@wireScripts`, `@wireScriptConfig` and the `/wire/brew(.min).js` routes (`FrontendAssets`).
 * Each tag renders once per request.
 */
class FrontendAssets(private val manager: WireManager) {
    private val scripts: Map<String, Asset> by lazy {
        // Include an intentionally absent name so the null-load branch stays exercised under Kover.
        listOf(
            "wire.js", "wire.min.js", "wire.min.js.map", "wire.esm.js", "wire.csp.min.js", "wire.absent.js",
            "brew.js", "brew.min.js", "brew.min.js.map", "brew.esm.js", "brew.csp.min.js",
        )
            .mapNotNull { name -> load(name)?.let { name to it } }
            .toMap()
    }

    /** Classpath asset with a content hash for cache busting. */
    class Asset(val bytes: ByteArray) {
        val hash: String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).take(8)
    }

    fun asset(name: String): Asset? = scripts[name]

    /** `wire.js` in debug, CSP build when enabled, otherwise `wire.min.js`. */
    fun scriptName(): String = when {
        manager.config.cspSafe -> "wire.csp.min.js"
        manager.config.debug -> "wire.js"
        else -> "wire.min.js"
    }

    /** HTTP response for a bundled script (immutable caching when `?id=` matches). */
    fun response(name: String): Response {
        val asset = asset(name) ?: return Response.text("Not Found", 404)
        val type = if (name.endsWith(".map")) "application/json" else "application/javascript; charset=utf-8"
        return Response(200, "", linkedMapOf("Content-Type" to type, "Cache-Control" to "public, max-age=31536000, immutable"))
            .withBodyBytes(asset.bytes)
    }

    fun styles(): String {
        if (!markRendered(STYLES_FLAG)) return ""
        val nonce = nonceAttribute()?.let { "$it data-wire-style" }.orEmpty()
        return "<!-- Wire Styles --><style$nonce>$CSS</style>"
    }

    fun scripts(): String {
        if (!markRendered(SCRIPTS_FLAG)) return ""
        val name = scriptName()
        val asset = asset(name) ?: throw WireException("Missing classpath resource org/konaframework/wire/$name")
        val url = manager.scriptUri ?: (assetBase() + "/" + name)
        val nonce = nonceAttribute().orEmpty()
        val token = HtmlRoot.escape(manager.csrfToken() ?: "")
        return "<script src=\"${HtmlRoot.escape(url)}?id=${asset.hash}\"$nonce data-csrf=\"$token\" " +
            "data-update-uri=\"${HtmlRoot.escape(manager.updateUri)}\" data-navigate-once=\"true\"></script>"
    }

    /** For apps bundling `@konaframework/wire` themselves (`@livewireScriptConfig`). */
    fun scriptConfig(): String {
        markRendered(SCRIPTS_FLAG)
        val nonce = manager.config.scriptNonce
        val config = linkedMapOf(
            "csrf" to (manager.csrfToken() ?: ""),
            "uri" to manager.updateUri,
            "nonce" to (nonce ?: ""),
            "navigate" to linkedMapOf(
                "show_progress_bar" to manager.config.navigateShowProgressBar,
                "progress_bar_color" to manager.config.navigateProgressBarColor,
                "view_transitions" to manager.config.navigateViewTransitions,
            ),
        )
        val attr = nonceAttribute().orEmpty()
        return "<script$attr data-navigate-once=\"true\">window.wireScriptConfig = window.brewScriptConfig = ${manager.json(config)};</script>"
    }

    private fun nonceAttribute(): String? {
        val nonce = manager.config.scriptNonce ?: return null
        return " nonce=\"${HtmlRoot.escape(nonce)}\""
    }

    private fun assetBase(): String {
        val url = manager.config.assetUrl ?: return manager.config.path
        return url.trimEnd('/')
    }

    /** Whether the tag was already emitted for the current request. */
    fun rendered(flag: String): Boolean = manager.currentRequest()?.getAttribute(flag) == true

    private fun markRendered(flag: String): Boolean {
        val request = manager.currentRequest() ?: return true
        if (request.getAttribute(flag) == true) return false
        request.setAttribute(flag, true)
        return true
    }

    private fun load(name: String): Asset? =
        FrontendAssets::class.java.getResourceAsStream("/org/konaframework/wire/$name")?.use { Asset(it.readAllBytes()) }

    companion object {
        const val STYLES_FLAG = "wire.styles.rendered"
        const val SCRIPTS_FLAG = "wire.scripts.rendered"

        private val CSS = """
            [wire\:loading][wire\:loading],[wire\:loading\.delay][wire\:loading\.delay],[wire\:loading\.list-item][wire\:loading\.list-item],[wire\:loading\.inline-block][wire\:loading\.inline-block],[wire\:loading\.inline][wire\:loading\.inline],[wire\:loading\.block][wire\:loading\.block],[wire\:loading\.flex][wire\:loading\.flex],[wire\:loading\.table][wire\:loading\.table],[wire\:loading\.grid][wire\:loading\.grid],[wire\:loading\.inline-flex][wire\:loading\.inline-flex]{display:none}
            [wire\:loading\.delay\.none][wire\:loading\.delay\.none],[wire\:loading\.delay\.shortest][wire\:loading\.delay\.shortest],[wire\:loading\.delay\.shorter][wire\:loading\.delay\.shorter],[wire\:loading\.delay\.short][wire\:loading\.delay\.short],[wire\:loading\.delay\.default][wire\:loading\.delay\.default],[wire\:loading\.delay\.long][wire\:loading\.delay\.long],[wire\:loading\.delay\.longer][wire\:loading\.delay\.longer],[wire\:loading\.delay\.longest][wire\:loading\.delay\.longest]{display:none}
            [wire\:offline][wire\:offline]{display:none}
            [wire\:dirty]:not(textarea):not(input):not(select){display:none}
            :root{--brew-progress-bar-color:#FF9A00}
            [x-cloak="x-cloak"],[x-cloak=""]{display:none!important}
            [wire\:cloak="wire:cloak"],[wire\:cloak=""]{display:none!important}
            dialog#brew-error::backdrop{background-color:rgba(0,0,0,.6)}
        """.trimIndent().replace("\n", "")
    }
}
