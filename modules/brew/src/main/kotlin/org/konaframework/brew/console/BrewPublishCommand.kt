package org.konaframework.brew.console

import org.konaframework.grind.ConsoleCommand
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./grind brew:publish` — publish Brew config (`livewire:publish --config`).
 */
class BrewPublishCommand : ConsoleCommand() {
    override val signature: String =
        "brew:publish {--config : Publish config/brew defaults} {--base= : Project root} {--force : Overwrite}"

    override val description: String = "Publish Brew configuration files"

    override fun handle(): Int {
        val base = Path.of(option("base", ".")).toAbsolutePath().normalize()
        val force = optionBool("force")
        val dest = base.resolve("config/brew.kona.kts")
        if (!force && Files.exists(dest)) {
            error("File already exists: config/brew.kona.kts (use --force)")
            return FAILURE
        }
        Files.createDirectories(dest.parent)
        Files.writeString(dest, CONFIG)
        success("Published config/brew.kona.kts")
        return SUCCESS
    }
}

private val CONFIG = """
    // Brew defaults (`config/livewire.php` equivalent). Loaded under the `brew` key.
    mapOf(
        "path" to "/brew",
        "class_namespace" to "app.brew",
        "view_prefix" to "brew",
        "layout" to "layouts.app",
        "inject_assets" to true,
        "inject_morph_markers" to true,
        "csrf" to true,
        "csp_safe" to false,
        "make" to mapOf("type" to "sfc"),
        "lazy_placeholder" to null,
        "render_on_redirect" to false,
        "legacy_model_binding" to false,
        "smart_wire_keys" to true,
        "release_token" to null,
        "temporary_file_upload" to mapOf(
            "disk" to "local",
            "timeout" to 300,
            "cleanup" to 86400,
            "rules" to "required|file|max:12288",
            "s3_endpoint" to null,
        ),
        "navigate" to mapOf(
            "show_progress_bar" to true,
            "progress_bar_color" to "#FF9A00",
            "view_transitions" to false,
        ),
        "payload" to mapOf(
            "max_size" to 1_048_576,
            "max_components" to 20,
            "max_calls" to 50,
            "max_nesting_depth" to 10,
        ),
    )
""".trimIndent() + "\n"
