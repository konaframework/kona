package dev.kona.cli

import kotlin.system.exitProcess

/**
 * Global `kona` CLI — create applications. App-local work uses `./grind` inside the project.
 */
fun main(args: Array<String>) {
    exitProcess(KonaApplication().run(args))
}

class KonaApplication {
    fun run(args: Array<String>): Int {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help" || args[0] == "-h") {
            printHelp()
            return 0
        }
        return try {
            when (val command = args[0]) {
                "new" -> NewCommand().run(args.drop(1))
                "version", "--version", "-V" -> {
                    val v = KonaVersions.load()
                    println("kona ${v.kona}")
                    0
                }
                else -> {
                    System.err.println("Unknown command: $command")
                    printHelp()
                    1
                }
            }
        } catch (ex: KonaCliException) {
            System.err.println(ex.message)
            1
        }
    }

    private fun printHelp() {
        println(
            """
            Kona — create JVM applications

            Usage:
              kona new <name> [--lang=kotlin|java] [--kit=web|api|crema|inertia|brew] [--kona-version=x.y.z]
              kona version
              kona help

            Kits (--kit / --preset):
              web       HTML welcome with Crema (default)
              api       HTTP-only API-style app (no Crema)
              crema     Alias of web
              inertia   Inertia + Crema + Vite stubs
              brew      Brew reactive UI + Crema

            After creating an app, work inside it with the local ./grind CLI:
              cd <name>
              ./grind serve
              ./grind make:controller Post
            """.trimIndent(),
        )
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
