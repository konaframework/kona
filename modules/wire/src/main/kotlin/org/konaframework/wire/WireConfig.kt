package org.konaframework.wire

import org.konaframework.core.Config

/**
 * Wire settings (`config/wire` — also reads deprecated `config/brew` keys).
 * Preferred keys are `wire.*`; `brew.*` is the 0.3.0 compatibility fallback.
 */
class WireConfig @JvmOverloads constructor(
    /** HMAC key for snapshot checksums (`app.key`); a random per-process key is used when blank. */
    val key: String = "",
    /** Base path for Wire endpoints. */
    val path: String = "/wire",
    /** Package roots that `<wire:name />` resolves classes from (default `app.wire` and `app.brew`). */
    val classNamespaces: List<String> = emptyList(),
    /** View prefix for convention views (`wire.view_prefix` → `wire.<name>`). */
    val viewPrefix: String = "wire",
    /** Inject `@wireStyles` / `@wireScripts` into full HTML responses that lack them. */
    val injectAssets: Boolean = true,
    /** Wrap conditionals/loops in morph markers. */
    val injectMorphMarkers: Boolean = true,
    /** Require `_token` / `X-CSRF-TOKEN` on updates when a session is present. */
    val csrf: Boolean = true,
    /** Max request body size in bytes (`payload.max_size`); null (or `0` in config) disables. */
    val maxPayloadSize: Int? = 1024 * 1024,
    /** Max components per request (`payload.max_components`). */
    val maxComponents: Int? = 20,
    /** Max method calls per component (`payload.max_calls`). */
    val maxCalls: Int? = 50,
    /** Max dot-path depth for updates (`payload.max_nesting_depth`). */
    val maxNestingDepth: Int? = 10,
    /** Show detailed errors (debug modal on the client). */
    val debug: Boolean = false,
    /** Asset URL used in `@wireScripts` (`asset_url`). */
    val assetUrl: String? = null,
    /** CSP nonce added to Wire's `<script>` / `<style>` tags (`wire.nonce`). */
    val scriptNonce: String? = null,
    /** Default lazy-load placeholder HTML (`wire.lazy_placeholder`). */
    val lazyPlaceholder: String? = null,
    /** Default `make:wire` type: `class`, `sfc`, or `mfc` (`wire.make.type`). */
    val makeType: String = "class",
    /** Re-render after redirect effects (`wire.render_on_redirect`). */
    val renderOnRedirect: Boolean = false,
    /** Legacy model binding mode (`wire.legacy_model_binding`). */
    val legacyModelBinding: Boolean = false,
    /** Prefer smart wire keys (`wire.smart_wire_keys`). */
    val smartWireKeys: Boolean = true,
    /** Default layout for full-page components (`wire.layout`, default `layouts.app`). */
    val layout: String = "layouts.app",
    /** Show the navigate progress bar (`wire.navigate.show_progress_bar`). */
    val navigateShowProgressBar: Boolean = true,
    /** Progress bar color (`wire.navigate.progress_bar_color`). */
    val navigateProgressBarColor: String = "#FF9A00",
    /** Enable View Transitions API on navigate (`wire.navigate.view_transitions`). */
    val navigateViewTransitions: Boolean = false,
    /** Signed upload URL lifetime in seconds (`wire.temporary_file_upload.timeout`). */
    val uploadTimeoutSeconds: Long = 300,
    /** Delete temp uploads older than this many seconds (`wire.temporary_file_upload.cleanup`). */
    val uploadCleanupSeconds: Long = 24 * 60 * 60,
    /** Global temporary upload validation rules (`wire.temporary_file_upload.rules`). */
    val uploadRules: List<String> = listOf("required", "file", "max:12288"),
    /** Temp upload disk: `local` (signed Wire URL) or `s3` (pre-signed PUT). */
    val uploadDisk: String = "local",
    /** Base URL for S3-compatible pre-signed uploads when [uploadDisk] is `s3`. */
    val uploadS3Endpoint: String? = null,
    /** Serve the CSP-safe runtime (`wire.csp_safe`). */
    val cspSafe: Boolean = false,
) {
    val updateUri: String get() = "$path/update"

    companion object {
        /** `<wire:counter />` resolves `app.wire.Counter`. */
        const val DEFAULT_CLASS_NAMESPACE = "app.wire"

        /** Pre-0.3 apps used `app.brew`; still discovered when no namespace is configured. */
        const val LEGACY_CLASS_NAMESPACE = "app.brew"

        @JvmStatic
        fun from(config: Config): WireConfig {
            val namespaces = when (val ns = any(config, "class_namespace")) {
                is Collection<*> -> ns.map { it.toString() }
                null -> listOf(DEFAULT_CLASS_NAMESPACE, LEGACY_CLASS_NAMESPACE)
                else -> ns.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }
            return WireConfig(
                key = config.getString("app.key", ""),
                path = str(config, "path", "/wire"),
                classNamespaces = namespaces,
                viewPrefix = str(config, "view_prefix", "wire"),
                injectAssets = bool(config, "inject_assets", true),
                injectMorphMarkers = bool(config, "inject_morph_markers", true),
                csrf = bool(config, "csrf", true),
                maxPayloadSize = optionalInt(config, "payload.max_size", 1024 * 1024),
                maxComponents = optionalInt(config, "payload.max_components", 20),
                maxCalls = optionalInt(config, "payload.max_calls", 50),
                maxNestingDepth = optionalInt(config, "payload.max_nesting_depth", 10),
                debug = config.getBoolean("app.debug", false),
                assetUrl = strOrNull(config, "asset_url"),
                scriptNonce = strOrNull(config, "nonce"),
                lazyPlaceholder = strOrNull(config, "lazy_placeholder"),
                makeType = str(config, "make.type", "class"),
                renderOnRedirect = bool(config, "render_on_redirect", false),
                legacyModelBinding = bool(config, "legacy_model_binding", false),
                smartWireKeys = bool(config, "smart_wire_keys", true),
                layout = str(config, "layout", "layouts.app"),
                navigateShowProgressBar = bool(config, "navigate.show_progress_bar", true),
                navigateProgressBarColor = str(config, "navigate.progress_bar_color", "#FF9A00"),
                navigateViewTransitions = bool(config, "navigate.view_transitions", false),
                uploadTimeoutSeconds = (intOrNull(config, "temporary_file_upload.timeout") ?: 300).toLong(),
                uploadCleanupSeconds = (intOrNull(config, "temporary_file_upload.cleanup") ?: (24 * 60 * 60)).toLong(),
                uploadRules = when (val rules = any(config, "temporary_file_upload.rules")) {
                    is Collection<*> -> rules.map { it.toString() }
                    is String -> rules.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                    else -> listOf("required", "file", "max:12288")
                },
                uploadDisk = str(config, "temporary_file_upload.disk", "local"),
                uploadS3Endpoint = strOrNull(config, "temporary_file_upload.s3_endpoint"),
                cspSafe = bool(config, "csp_safe", false),
            )
        }

        private fun firstPresent(config: Config, suffix: String): String? = when {
            config.has("wire.$suffix") -> "wire.$suffix"
            config.has("brew.$suffix") -> "brew.$suffix"
            else -> null
        }

        private fun any(config: Config, suffix: String): Any? {
            val key = firstPresent(config, suffix) ?: return null
            return config.get<Any>(key)
        }

        private fun str(config: Config, suffix: String, default: String): String {
            val key = firstPresent(config, suffix) ?: return default
            return config.getString(key, default)
        }

        private fun strOrNull(config: Config, suffix: String): String? {
            val key = firstPresent(config, suffix) ?: return null
            return config.getString(key)
        }

        private fun bool(config: Config, suffix: String, default: Boolean): Boolean {
            val key = firstPresent(config, suffix) ?: return default
            return config.getBoolean(key, default)
        }

        private fun intOrNull(config: Config, suffix: String): Int? {
            val key = firstPresent(config, suffix) ?: return null
            return config.getInt(key)
        }

        private fun optionalInt(config: Config, suffix: String, default: Int): Int? {
            val key = firstPresent(config, suffix) ?: return default
            return config.getInt(key)?.takeIf { it > 0 }
        }
    }
}
