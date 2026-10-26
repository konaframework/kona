package org.konaframework.grind

import java.nio.file.Path

/** `grind make:firefly-widget <Name> [--lang=kotlin|java]` scaffolds a widget and its Crema view. */
class MakeFireflyWidgetCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:firefly-widget <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("widget", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        write(base, name, lang)
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            Names.write(
                base,
                Names.sourceFile(lang, "app/firefly/widgets", className),
                "Firefly widget",
                source(className, lang),
            )
            Names.write(
                base,
                Names.viewFile("firefly.widgets.${className.lowercase()}"),
                "Firefly widget view",
                view(className),
            )
        }

        private fun source(name: String, lang: String): String = if (lang == "java") {
            lines(
                "package app.firefly.widgets;",
                "",
                "import org.konaframework.firefly.widgets.Widget;",
                "",
                "public class $name extends Widget {",
                "    public $name() {",
                "        super(\"${name.lowercase()}\");",
                "        view(\"firefly.widgets.${name.lowercase()}\");",
                "    }",
                "}",
            )
        } else {
            lines(
                "package app.firefly.widgets",
                "",
                "import org.konaframework.firefly.widgets.Widget",
                "",
                "class $name : Widget(\"${name.lowercase()}\") {",
                "    init {",
                "        view(\"firefly.widgets.${name.lowercase()}\")",
                "    }",
                "}",
            )
        }

        private fun view(name: String): String = """
            {{-- Widget: $name --}}
            <div class="ff-widget" x-data="ffWidget" data-ff-widget="$name">
              {{ content }}
            </div>
        """.trimIndent() + "\n"
    }
}
