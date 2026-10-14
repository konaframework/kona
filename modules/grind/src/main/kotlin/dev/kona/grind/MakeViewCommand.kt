package dev.kona.grind

import java.nio.file.Files
import java.nio.file.Path

class MakeViewCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:view <name>")
        }
        val name = positional.first()
        if (!VIEW_NAME.matches(name)) {
            throw GrindException("Invalid view name [$name]: use dotted names like home or posts.show")
        }
        val base = Path.of(options.getOrDefault("base", ".")).toAbsolutePath().normalize()
        val relative = "resources/views/${name.replace('.', '/')}.crema.html"
        val dest = base.resolve(relative)
        if (Files.exists(dest)) {
            throw GrindException("View already exists: $dest")
        }
        Files.createDirectories(dest.parent)
        val title = name.substringAfterLast('.')
        Files.writeString(
            dest,
            """
            {{-- View: $name --}}
            <h1>$title</h1>
            """.trimIndent() + "\n",
        )
        println("View created: $relative")
        return 0
    }
}

private val VIEW_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")
