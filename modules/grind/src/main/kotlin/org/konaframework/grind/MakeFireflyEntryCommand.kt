package org.konaframework.grind

import java.nio.file.Path

/** `grind make:firefly-entry <Name> [--lang=kotlin|java]` scaffolds an entry component and its Crema view. */
class MakeFireflyEntryCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:firefly-entry <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("entry", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        write(base, name, lang)
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            Names.write(
                base,
                Names.sourceFile(lang, "app/firefly/entries", className),
                "Firefly entry",
                source(className, lang),
            )
            Names.write(
                base,
                Names.viewFile("firefly.entries.${className.lowercase()}"),
                "Firefly entry view",
                view(className),
            )
        }

        private fun source(name: String, lang: String): String = if (lang == "java") {
            lines(
                "package app.firefly.entries;",
                "",
                "import org.konaframework.firefly.infolists.Entry;",
                "",
                "public class $name extends Entry<$name> {",
                "    public $name() {",
                "        super(\"${name.lowercase()}\");",
                "        view(\"firefly.entries.${name.lowercase()}\");",
                "        meta(\"ff-view\", \"firefly.entries.${name.lowercase()}\");",
                "    }",
                "",
                "    @Override",
                "    public String kind() {",
                "        return \"entry\";",
                "    }",
                "}",
            )
        } else {
            lines(
                "package app.firefly.entries",
                "",
                "import org.konaframework.firefly.infolists.Entry",
                "",
                "class $name : Entry<$name>(\"${name.lowercase()}\") {",
                "    init {",
                "        view(\"firefly.entries.${name.lowercase()}\")",
                "        meta(\"ff-view\", \"firefly.entries.${name.lowercase()}\")",
                "    }",
                "",
                "    override fun kind(): String = \"entry\"",
                "}",
            )
        }

        private fun view(name: String): String = """
            {{-- Entry: $name --}}
            <div class="ff-entry" x-data="ffEntry" data-ff-path="{{ path }}">
              @entangle({{ path }})
              {{ content }}
            </div>
        """.trimIndent() + "\n"
    }
}
