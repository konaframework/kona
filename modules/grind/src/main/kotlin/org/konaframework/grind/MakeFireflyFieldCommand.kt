package org.konaframework.grind

import java.nio.file.Path

/** `grind make:firefly-field <Name> [--lang=kotlin|java]` scaffolds a form field and its Crema view. */
class MakeFireflyFieldCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:firefly-field <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("field", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        write(base, name, lang)
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            Names.write(
                base,
                Names.sourceFile(lang, "app/firefly/fields", className),
                "Firefly field",
                source(className, lang),
            )
            Names.write(
                base,
                Names.viewFile("firefly.fields.${className.lowercase()}"),
                "Firefly field view",
                view(className),
            )
        }

        private fun source(name: String, lang: String): String = if (lang == "java") {
            lines(
                "package app.firefly.fields;",
                "",
                "import org.konaframework.firefly.forms.Field;",
                "",
                "public class $name extends Field<$name> {",
                "    public $name() {",
                "        super(\"${name.lowercase()}\");",
                "        view(\"firefly.fields.${name.lowercase()}\");",
                "    }",
                "",
                "    @Override",
                "    public String kind() {",
                "        return \"field\";",
                "    }",
                "}",
            )
        } else {
            lines(
                "package app.firefly.fields",
                "",
                "import org.konaframework.firefly.forms.Field",
                "",
                "class $name : Field<$name>(\"${name.lowercase()}\") {",
                "    init {",
                "        view(\"firefly.fields.${name.lowercase()}\")",
                "    }",
                "",
                "    override fun kind(): String = \"field\"",
                "}",
            )
        }

        private fun view(name: String): String = """
            {{-- Field: $name --}}
            <div class="ff-field" x-data="ffField" data-ff-path="{{ path }}">
              @entangle({{ path }})
              {{ content }}
            </div>
        """.trimIndent() + "\n"
    }
}
