package dev.kona.cli

import dev.kona.support.console.ConsoleIO
import kotlin.system.exitProcess

/**
 * Global `kona` CLI — create applications. App-local work uses `./grind` inside the project.
 */
fun main(args: Array<String>) {
    exitProcess(KonaApplication().run(args))
}

class KonaApplication {
    fun run(args: Array<String>): Int {
        val io = ConsoleIO()
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help" || args[0] == "-h") {
            printHelp(io)
            return 0
        }
        return try {
            when (val command = args[0]) {
                "new" -> NewCommand().run(args.drop(1))
                "version", "--version", "-V" -> {
                    val v = KonaVersions.load()
                    io.writeln("kona ${v.kona}")
                    0
                }
                else -> {
                    io.error("Unknown command: $command")
                    printHelp(io)
                    1
                }
            }
        } catch (ex: KonaCliException) {
            io.error(ex.message!!)
            1
        }
    }

    private fun printHelp(io: ConsoleIO) {
        io.box(
            "kona",
            listOf(
                "Create JVM applications",
                "Then use ./grind inside the app",
            ),
        )
        io.twoColumn("new <name>", "[--lang=kotlin|java] [--kit=…]")
        io.twoColumn("version", "Print kona version")
        io.twoColumn("help", "Show this help")
        io.writeln()
        io.info("Kits: web (default), api, crema, inertia, brew")
    }
}

class KonaCliException(message: String) : RuntimeException(message)

internal fun parseOptions(args: List<String>): Pair<List<String>, Map<String, String>> {
    val positional = mutableListOf<String>()
    val options = linkedMapOf<String, String>()
    args.forEach { arg ->
        if (arg.startsWith("--")) {
            val body = arg.removePrefix("--")
            val idx = body.indexOf('=')
            if (idx < 0) {
                options[body] = "true"
            } else {
                options[body.substring(0, idx)] = body.substring(idx + 1)
            }
        } else {
            positional += arg
        }
    }
    return positional to options
}
