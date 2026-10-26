package org.konaframework.grind

import java.nio.file.Path

/** `grind make:firefly-action <Name> [--lang=kotlin|java]` scaffolds an action class and its Crema view. */
class MakeFireflyActionCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:firefly-action <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("action", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        write(base, name, lang)
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            val wire = className.substring(0, 1).lowercase() + className.substring(1)
            Names.write(
                base,
                Names.sourceFile(lang, "app/firefly/actions", className),
                "Firefly action",
                source(className, wire, lang),
            )
            Names.write(
                base,
                Names.viewFile("firefly.actions.${className.lowercase()}"),
                "Firefly action view",
                view(className, wire),
            )
        }

        private fun source(name: String, wire: String, lang: String): String = if (lang == "java") {
            lines(
                "package app.firefly.actions;",
                "",
                "import org.konaframework.firefly.actions.Action;",
                "",
                "public class $name extends Action {",
                "    public $name() {",
                "        super(\"$wire\");",
                "        label(\"$name\");",
                "        view(\"firefly.actions.${name.lowercase()}\");",
                "    }",
                "}",
            )
        } else {
            lines(
                "package app.firefly.actions",
                "",
                "import org.konaframework.firefly.actions.Action",
                "",
                "open class $name : Action(\"$wire\") {",
                "    init {",
                "        label(\"$name\")",
                "        view(\"firefly.actions.${name.lowercase()}\")",
                "    }",
                "}",
            )
        }

        private fun view(name: String, wire: String): String = """
            {{-- Action: $name --}}
            <div class="ff-action" x-data="{ open: false }">
              <button type="button" class="ff-action-trigger" @click="open = true">$name</button>
              <div class="ff-action-dialog" x-show="open" style="display:none">
                <button type="button" x-data="ffAction('$wire')" wire:click="$wire">$name</button>
              </div>
            </div>
        """.trimIndent() + "\n"
    }
}
