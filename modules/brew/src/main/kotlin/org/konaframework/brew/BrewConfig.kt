package org.konaframework.brew

import org.konaframework.core.Config

/**
 * Brew settings (`config/brew` — Livewire's `config/livewire.php`). Read from Kona [Config] keys under `brew.*`.
 */
class BrewConfig @JvmOverloads constructor(
    /** HMAC key for snapshot checksums (`app.key`); a random per-process key is used when blank. */
    val key: String = "",
    /** Base path for Brew endpoints. */
    val path: String = "/brew",
    /** Package roots that `<brew:name />` resolves classes from (`brew.class_namespace`, default `app.brew` in apps). */
    val classNamespaces: List<String> = emptyList(),
    /** View prefix for convention views (`brew.view_path` → `brew.<name>`). */
    val viewPrefix: String = "brew",
    /** Inject `@brewStyles` / `@brewScripts` into full HTML responses that lack them. */
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
    /** Asset URL used in `@brewScripts` (`asset_url`). */
    val assetUrl: String? = null,
    /** CSP nonce added to Brew's `<script>` / `<style>` tags (`brew.nonce`). */
    val scriptNonce: String? = null,
    /** Default lazy-load placeholder HTML (`brew.lazy_placeholder`). */
    val lazyPlaceholder: String? = null,
    /** Default `make:brew` type: `sfc`, `class`, or `mfc` (`brew.make.type`). */
    val makeType: String = "sfc",
    /** Re-render after redirect effects (`brew.render_on_redirect`). */
    val renderOnRedirect: Boolean = false,
    /** Legacy model binding mode (`brew.legacy_model_binding`). */
    val legacyModelBinding: Boolean = false,
    /** Prefer smart wire keys (`brew.smart_wire_keys`). */
    val smartWireKeys: Boolean = true,
    /** Default layout for full-page components (`brew.layout`, default `layouts.app`). */
    val layout: String = "layouts.app",
    /** Show the navigate progress bar (`brew.navigate.show_progress_bar`). */
    val navigateShowProgressBar: Boolean = true,
    /** Progress bar color (`brew.navigate.progress_bar_color`). */
    val navigateProgressBarColor: String = "#FF9A00",
    /** Enable View Transitions API on navigate (`brew.navigate.view_transitions`). */
    val navigateViewTransitions: Boolean = false,
    /** Signed upload URL lifetime in seconds (`brew.temporary_file_upload.timeout`). */
    val uploadTimeoutSeconds: Long = 300,
    /** Delete temp uploads older than this many seconds (`brew.temporary_file_upload.cleanup`). */
    val uploadCleanupSeconds: Long = 24 * 60 * 60,
    /** Global temporary upload validation rules (`brew.temporary_file_upload.rules`). */
    val uploadRules: List<String> = listOf("required", "file", "max:12288"),
    /** Temp upload disk: `local` (signed Brew URL) or `s3` (pre-signed PUT). */
    val uploadDisk: String = "local",
    /** Base URL for S3-compatible pre-signed uploads when [uploadDisk] is `s3`. */
    val uploadS3Endpoint: String? = null,
    /** Serve the CSP-safe runtime (`brew.csp_safe`). */
    val cspSafe: Boolean = false,
) {
    val updateUri: String get() = "$path/update"

    companion object {
        /** Livewire's `App\Livewire` default: `<brew:counter />` resolves `app.brew.Counter`. */
        const val DEFAULT_CLASS_NAMESPACE = "app.brew"

        @JvmStatic
        fun from(config: Config): BrewConfig {
            val namespaces = when (val ns = config.get<Any>("brew.class_namespace")) {
                is Collection<*> -> ns.map { it.toString() }
                null -> listOf(DEFAULT_CLASS_NAMESPACE)
                else -> ns.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }
            return BrewConfig(
                key = config.getString("app.key", ""),
                path = config.getString("brew.path", "/brew"),
                classNamespaces = namespaces,
                viewPrefix = config.getString("brew.view_prefix", "brew"),
                injectAssets = config.getBoolean("brew.inject_assets", true),
                injectMorphMarkers = config.getBoolean("brew.inject_morph_markers", true),
                csrf = config.getBoolean("brew.csrf", true),
                maxPayloadSize = optionalInt(config, "brew.payload.max_size", 1024 * 1024),
                maxComponents = optionalInt(config, "brew.payload.max_components", 20),
                maxCalls = optionalInt(config, "brew.payload.max_calls", 50),
                maxNestingDepth = optionalInt(config, "brew.payload.max_nesting_depth", 10),
                debug = config.getBoolean("app.debug", false),
                assetUrl = config.getString("brew.asset_url"),
                scriptNonce = config.getString("brew.nonce"),
                lazyPlaceholder = config.getString("brew.lazy_placeholder"),
                makeType = config.getString("brew.make.type", "sfc"),
                renderOnRedirect = config.getBoolean("brew.render_on_redirect", false),
                legacyModelBinding = config.getBoolean("brew.legacy_model_binding", false),
                smartWireKeys = config.getBoolean("brew.smart_wire_keys", true),
                layout = config.getString("brew.layout", "layouts.app"),
                navigateShowProgressBar = config.getBoolean("brew.navigate.show_progress_bar", true),
                navigateProgressBarColor = config.getString("brew.navigate.progress_bar_color", "#FF9A00"),
                navigateViewTransitions = config.getBoolean("brew.navigate.view_transitions", false),
                uploadTimeoutSeconds = (config.getInt("brew.temporary_file_upload.timeout") ?: 300).toLong(),
                uploadCleanupSeconds = (config.getInt("brew.temporary_file_upload.cleanup") ?: (24 * 60 * 60)).toLong(),
                uploadRules = when (val rules = config.get<Any>("brew.temporary_file_upload.rules")) {
                    is Collection<*> -> rules.map { it.toString() }
                    is String -> rules.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                    else -> listOf("required", "file", "max:12288")
                },
                uploadDisk = config.getString("brew.temporary_file_upload.disk", "local"),
                uploadS3Endpoint = config.getString("brew.temporary_file_upload.s3_endpoint"),
                cspSafe = config.getBoolean("brew.csp_safe", false),
            )
        }

        private fun optionalInt(config: Config, key: String, default: Int): Int? {
            if (!config.has(key)) return default
            return config.getInt(key)?.takeIf { it > 0 }
        }
    }
}
