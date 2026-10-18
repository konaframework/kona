package org.konaframework.brew.console

import org.konaframework.grind.ConsoleCommand
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./grind brew:layout` — publish the default full-page layout (`livewire:layout`).
 */
class BrewLayoutCommand : ConsoleCommand() {
    override val signature: String =
        "brew:layout {--base= : Project root} {--force : Overwrite} {--name=layouts.app : Layout view name}"

    override val description: String = "Publish a Brew full-page layout Crema template"

    override fun handle(): Int {
        val base = Path.of(option("base", ".")).toAbsolutePath().normalize()
        val force = optionBool("force")
        val name = option("name", "layouts.app")!!.replace('.', '/')
        val dest = base.resolve("src/main/resources/views/$name.crema.html")
        if (!force && Files.exists(dest)) {
            error("File already exists: $dest (use --force)")
            return FAILURE
        }
        Files.createDirectories(dest.parent)
        Files.writeString(dest, LAYOUT)
        success("Published $dest")
        return SUCCESS
    }
}

private val LAYOUT = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>{{ title ?: 'Brew' }}</title>
        @brewStyles
    </head>
    <body>
        {{ slot }}
        @brewScripts
    </body>
    </html>
""".trimIndent() + "\n"
