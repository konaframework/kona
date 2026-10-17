package org.konaframework.grind

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * `grind make:command` — generate a [ConsoleCommand] stub and register it in
 * `META-INF/kona/commands`.
 */
class MakeCommandCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:command <Name> [--command=app:name] [--lang=kotlin|java]")
        }
        val raw = positional.first()
        val simple = if (raw.endsWith("Command")) raw else raw + "Command"
        Names.requireClassName("command", simple)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val commandName = options["command"] ?: defaultCommandName(simple)
        val relative = Names.sourceFile(lang, "app/console/commands", simple)
        Names.write(base, relative, "Command", stub(simple, commandName, lang))
        appendDiscovery(base, "app.console.commands.$simple")
        return 0
    }

    private fun defaultCommandName(className: String): String {
        val stem = className.removeSuffix("Command")
        val snake = stem.replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
        return "app:$snake"
    }

    private fun appendDiscovery(base: Path, fqcn: String) {
        val meta = base.resolve("src/main/resources/$DISCOVERY")
        Files.createDirectories(meta.parent)
        val existing = if (Files.isRegularFile(meta)) Files.readString(meta) else ""
        if (existing.lineSequence().any { it.trim() == fqcn }) {
            return
        }
        val prefix = if (existing.isNotEmpty() && !existing.endsWith("\n")) "\n" else ""
        Files.writeString(
            meta,
            existing + prefix + fqcn + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        GrindIO(color = GrindIO.detectColor()).info("Registered in $DISCOVERY")
    }

    private fun stub(className: String, commandName: String, lang: String): String {
        if (lang == "java") {
            return """
                package app.console.commands;

                import org.konaframework.grind.ConsoleCommand;

                public class $className extends ConsoleCommand {
                    @Override
                    public String getSignature() {
                        return "$commandName";
                    }

                    @Override
                    public String getDescription() {
                        return "TODO: describe $className";
                    }

                    @Override
                    public int handle() {
                        info("$className running");
                        return SUCCESS;
                    }
                }
            """.trimIndent() + "\n"
        }
        return """
            package app.console.commands

            import org.konaframework.grind.ConsoleCommand

            class $className : ConsoleCommand() {
                override val signature: String = "$commandName"

                override val description: String = "TODO: describe $className"

                override fun handle(): Int {
                    info("$className running")
                    return SUCCESS
                }
            }
        """.trimIndent() + "\n"
    }

    companion object {
        const val DISCOVERY = "META-INF/kona/commands"
    }
}
