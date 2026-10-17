package io.konaframework.grind

class MakeViewCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:view <name>")
        }
        // Accept dotted (`brew.todos`) or nested path (`brew/todos`) forms.
        val name = positional.first().replace('/', '.')
        if (!VIEW_NAME.matches(name)) {
            throw GrindException(
                "Invalid view name [${positional.first()}]: use dotted or nested names like home, posts.show, or brew/todos",
            )
        }
        val base = Names.base(options)
        val relative = Names.viewFile(name)
        val title = name.substringAfterLast('.')
        Names.write(
            base,
            relative,
            "View",
            """
            {{-- View: $name --}}
            <h1>$title</h1>
            """.trimIndent() + "\n",
        )
        return 0
    }
}

private val VIEW_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")
