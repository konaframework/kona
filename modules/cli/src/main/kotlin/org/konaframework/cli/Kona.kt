package org.konaframework.cli

import org.konaframework.support.console.ConsoleIO
import kotlin.system.exitProcess

/**
 * Global `kona` CLI — create applications, and optionally proxy to an app's `./grind`.
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
                "install" -> {
                    // Install lives on grind only — keep a clear redirect for old muscle memory.
                    io.error("Use: kona grind install <package> (or ./grind install inside the app)")
                    1
                }
                "grind" -> GrindCommand().run(args.drop(1))
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
        io.intro("kona")
        io.box(
            "kona",
            listOf(
                "Create JVM applications",
                "Day-to-day work: ./grind or kona grind …",
            ),
        )
        io.title("Commands")
        io.twoColumn("new <name>", "[--lang=kotlin|java] [--kit=…] [--database=…]")
        io.twoColumn("grind <command>", "Proxy to the nearest app's ./grind")
        io.twoColumn("version", "Print kona version")
        io.twoColumn("help", "Show this help")
        io.writeln()
        io.info("Kits: web (default), api, crema, inertia, brew")
        io.info("Packages: kona grind install <alias>  (or ./grind install)")
        io.outro("https://docs.konaframework.org")
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
