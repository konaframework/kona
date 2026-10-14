package dev.kona.grind

import kotlin.system.exitProcess

/**
 * `grind` CLI entry — Artisan / smith equivalent.
 */
fun main(args: Array<String>) {
    exitProcess(GrindApplication().run(args))
}

class GrindApplication {
    fun run(args: Array<String>): Int {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help" || args[0] == "-h") {
            printHelp()
            return 0
        }
        return try {
            when (val command = args[0]) {
                "serve" -> ServeCommand().run(args.drop(1))
                "new" -> NewCommand().run(args.drop(1))
                "make:controller" -> MakeControllerCommand().run(args.drop(1))
                else -> {
                    System.err.println("Unknown command: $command")
                    printHelp()
                    1
                }
            }
        } catch (ex: GrindException) {
            System.err.println(ex.message)
            1
        }
    }

    private fun printHelp() {
        println(
            """
            Kona grind

            Usage:
              grind serve [--host=127.0.0.1] [--port=8080] [--base=.]
              grind new <name> [--lang=kotlin|java]
              grind make:controller <Name>
              grind help
            """.trimIndent(),
        )
    }
}

class GrindException(message: String) : RuntimeException(message)

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
