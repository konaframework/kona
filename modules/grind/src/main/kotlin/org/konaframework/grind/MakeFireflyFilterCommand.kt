package org.konaframework.grind

import java.nio.file.Path

/** `grind make:firefly-filter <Name> [--lang=kotlin|java]` scaffolds a table filter and its Crema view. */
class MakeFireflyFilterCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:firefly-filter <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("filter", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        write(base, name, lang)
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            Names.write(
                base,
                Names.sourceFile(lang, "app/firefly/filters", className),
                "Firefly filter",
                source(className, lang),
            )
            Names.write(
                base,
                Names.viewFile("firefly.filters.${className.lowercase()}"),
                "Firefly filter view",
                view(className),
            )
        }

        private fun source(name: String, lang: String): String = if (lang == "java") {
            lines(
                "package app.firefly.filters;",
                "",
                "import org.konaframework.firefly.tables.Filter;",
                "",
                "public class $name extends Filter {",
                "    public $name() {",
                "        super(\"${name.lowercase()}\");",
                "        attribute(\"${name.lowercase()}\");",
                "    }",
                "}",
            )
        } else {
            lines(
                "package app.firefly.filters",
                "",
                "import org.konaframework.firefly.tables.Filter",
                "",
                "class $name : Filter(\"${name.lowercase()}\") {",
                "    init {",
                "        attribute(\"${name.lowercase()}\")",
                "    }",
                "}",
            )
        }

        private fun view(name: String): String = """
            {{-- Filter: $name --}}
            <div class="ff-filter" x-data="ffColumn('{{ name }}', true)" data-ff-path="{{ name }}">
              @entangle({{ name }})
              <input x-on:change="${'$'}wire.${'$'}call('applyFilter', '{{ name }}', ${'$'}event.target.value)">
            </div>
        """.trimIndent() + "\n"
    }
}
