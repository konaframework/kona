package org.konaframework.grind

import java.nio.file.Path

/** `grind make:firefly-layout <Name> [--lang=kotlin|java]` scaffolds a layout component and its Crema view. */
class MakeFireflyLayoutCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:firefly-layout <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("layout", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        write(base, name, lang)
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            Names.write(
                base,
                Names.sourceFile(lang, "app/firefly/layouts", className),
                "Firefly layout",
                source(className, lang),
            )
            Names.write(
                base,
                Names.viewFile("firefly.layouts.${className.lowercase()}"),
                "Firefly layout view",
                view(className),
            )
        }

        private fun source(name: String, lang: String): String = if (lang == "java") {
            lines(
                "package app.firefly.layouts;",
                "",
                "import org.konaframework.firefly.schemas.Component;",
                "",
                "public class $name extends Component {",
                "    public $name() {",
                "        super(\"${name.lowercase()}\");",
                "        view(\"firefly.layouts.${name.lowercase()}\");",
                "    }",
                "",
                "    @Override",
                "    public String kind() {",
                "        return \"layout\";",
                "    }",
                "}",
            )
        } else {
            lines(
                "package app.firefly.layouts",
                "",
                "import org.konaframework.firefly.schemas.Component",
                "",
                "class $name : Component(\"${name.lowercase()}\") {",
                "    init {",
                "        view(\"firefly.layouts.${name.lowercase()}\")",
                "    }",
                "",
                "    override fun kind(): String = \"layout\"",
                "}",
            )
        }

        private fun view(name: String): String = """
            {{-- Layout: $name — use {!! icon('heroicon-o-…') !!} for SVG from kona-icons --}}
            <div class="ff-layout" x-data="ffField" data-ff-path="{{ path }}">
              @entangle({{ path }})
              <header class="ff-layout-heading">
                {!! icon('heroicon-o-squares-2x2', 'ff-icon') !!}
                <span>{{ label }}</span>
              </header>
              {{ content }}
            </div>
        """.trimIndent() + "\n"
    }
}
