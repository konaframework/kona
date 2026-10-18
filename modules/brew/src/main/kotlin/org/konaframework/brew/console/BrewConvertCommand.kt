package org.konaframework.brew.console

import org.konaframework.grind.ConsoleCommand
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./grind brew:convert` — convert between inline-template (SFC), class+view, and multi-file (`livewire:convert`).
 */
class BrewConvertCommand : ConsoleCommand() {
    override val signature: String =
        "brew:convert {path : Path to a .kt/.java component} " +
            "{--to=class : Target form: sfc, class, or mfc} {--base= : Project root} {--force : Overwrite}"

    override val description: String = "Convert a Brew component between SFC, class+view, and multi-file layouts"

    override fun handle(): Int {
        val base = Path.of(option("base", ".")).toAbsolutePath().normalize()
        val relative = argumentOrFail("path")
        val source = base.resolve(relative).normalize()
        if (!Files.isRegularFile(source)) {
            error("Component file not found: $relative")
            return FAILURE
        }
        val target = option("to", "class").lowercase()
        when (target) {
            "sfc", "class", "mfc" -> Unit
            else -> {
                error("Unknown --to [$target]; expected sfc, class, or mfc")
                return FAILURE
            }
        }
        val text = Files.readString(source)
        val force = optionBool("force")
        val java = source.toString().endsWith(".java")
        return when (target) {
            "mfc" -> writeMfc(source, force)
            "class" -> writeClass(base, source, text, java, force)
            else -> writeSfc(source, text, java)
        }
    }

    private fun writeSfc(source: Path, text: String, java: Boolean): Int {
        if ("template()" in text) {
            info("Already an SFC-style component: $source")
            return SUCCESS
        }
        val body = if (java) {
            text.replace("Object render()", "String template()")
                .replace(Regex("""return view\("[^"]+"\);"""), "return \"\"\"\\n<div></div>\\n\"\"\";")
        } else {
            text.replace(Regex("""override fun render\(\)\s*=\s*view\("[^"]+"\)"""), "override fun template() = \"\"\"\\n<div></div>\\n\"\"\"")
        }
        Files.writeString(source, body)
        success("Converted to SFC: $source")
        return SUCCESS
    }

    private fun writeClass(base: Path, source: Path, text: String, java: Boolean, force: Boolean): Int {
        if ("template()" !in text) {
            info("No inline template() found; already class-style?")
            return SUCCESS
        }
        val viewName = "brew." + source.fileName.toString().substringBeforeLast('.').replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
        val viewRel = "src/main/resources/views/${viewName.replace('.', '/')}.crema.html"
        val viewPath = base.resolve(viewRel)
        when {
            force -> Unit
            Files.exists(viewPath) -> {
                error("View already exists: $viewRel (use --force)")
                return FAILURE
            }
            else -> Unit
        }
        Files.createDirectories(viewPath.parent)
        Files.writeString(viewPath, "<div>{{ count }}</div>\n")
        val body = if (java) {
            text.replace("String template()", "Object render()")
                .replace(Regex("""return\s*""\"[\s\S]*?""\";"""), "return view(\"$viewName\");")
        } else {
            text.replace(Regex("""override fun template\(\)\s*=\s*""\"[\s\S]*?""\""""), "override fun render() = view(\"$viewName\")")
        }
        Files.writeString(source, body)
        success("Converted to class+view: $source + $viewRel")
        return SUCCESS
    }

    private fun writeMfc(source: Path, force: Boolean): Int {
        val name = source.fileName.toString().substringBeforeLast('.')
        val dir = source.parent.resolve(name.lowercase())
        Files.createDirectories(dir)
        val js = dir.resolve("$name.js")
        val css = dir.resolve("$name.css")
        when {
            force -> Unit
            Files.exists(js) || Files.exists(css) -> {
                error("MFC assets already exist under $dir (use --force)")
                return FAILURE
            }
            else -> Unit
        }
        Files.writeString(js, "export default function (\$wire) {}\n")
        Files.writeString(css, "/* $name */\n")
        success("Created MFC assets under $dir")
        return SUCCESS
    }
}
