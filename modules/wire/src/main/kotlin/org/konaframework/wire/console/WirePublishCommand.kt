package org.konaframework.wire.console

import org.konaframework.grind.ConsoleCommand
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./grind wire:publish` — publish Wire config (`livewire:publish --config`).
 */
open class WirePublishCommand : ConsoleCommand() {
    protected open val commandToken: String = "wire:publish"
    protected open val deprecationHint: String? = null
    protected open val configFileName: String = "wire.kona.kts"

    override val signature: String
        get() =
            "$commandToken {--config : Publish config/$configFileName defaults} {--base= : Project root} {--force : Overwrite}"

    override val description: String = "Publish Wire configuration files"

    override fun handle(): Int {
        deprecationHint?.let { warn(it) }
        val base = Path.of(option("base", ".")).toAbsolutePath().normalize()
        val force = optionBool("force")
        val dest = base.resolve("config/$configFileName")
        if (!force && Files.exists(dest)) {
            error("File already exists: config/$configFileName (use --force)")
            return FAILURE
        }
        Files.createDirectories(dest.parent)
        Files.writeString(dest, CONFIG)
        success("Published config/$configFileName")
        return SUCCESS
    }
}

private val CONFIG = """
    // Wire defaults. Loaded under the `wire` key (`brew` remains a compatibility fallback).
    mapOf(
        "path" to "/wire",
        "class_namespace" to "app.wire",
        "view_prefix" to "wire",
        "layout" to "layouts.app",
        "inject_assets" to true,
        "inject_morph_markers" to true,
        "csrf" to true,
        "csp_safe" to false,
        "make" to mapOf("type" to "class"),
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
