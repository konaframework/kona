package org.konaframework.grind

import java.nio.file.Path

/** `grind make:firefly-column <Name> [--lang=kotlin|java]` scaffolds a table column and its Crema view. */
class MakeFireflyColumnCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:firefly-column <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("column", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        write(base, name, lang)
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            Names.write(
                base,
                Names.sourceFile(lang, "app/firefly/columns", className),
                "Firefly column",
                source(className, lang),
            )
            Names.write(
                base,
                Names.viewFile("firefly.columns.${className.lowercase()}"),
                "Firefly column view",
                view(className),
            )
        }

        private fun source(name: String, lang: String): String = if (lang == "java") {
            lines(
                "package app.firefly.columns;",
                "",
                "import org.konaframework.firefly.tables.TextColumn;",
                "",
                "public class $name extends TextColumn {",
                "    public $name() {",
                "        super(\"${name.lowercase()}\");",
                "        view(\"firefly.columns.${name.lowercase()}\");",
                "    }",
                "}",
            )
        } else {
            lines(
                "package app.firefly.columns",
                "",
                "import org.konaframework.firefly.tables.TextColumn",
                "",
                "class $name : TextColumn(\"${name.lowercase()}\") {",
                "    init {",
                "        view(\"firefly.columns.${name.lowercase()}\")",
                "    }",
                "}",
            )
        }

        private fun view(name: String): String = """
            {{-- Column: $name --}}
            <div class="ff-column" x-data="ffColumn('{{ name }}', true)" data-ff-path="{{ name }}">
              @entangle({{ name }})
              <button type="button" x-on:click="${'$'}wire.${'$'}call('{{ name }}')"></button>
            </div>
        """.trimIndent() + "\n"
    }
}
