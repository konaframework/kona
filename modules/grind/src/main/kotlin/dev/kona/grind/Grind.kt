package dev.kona.grind

import dev.kona.extract.ExtractException
import kotlin.system.exitProcess

/**
 * App-local `grind` CLI — serve and generate code inside a Kona application.
 * Create apps with the global `kona new` command.
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
                "make:controller" -> MakeControllerCommand().run(args.drop(1))
                "make:model" -> MakeModelCommand().run(args.drop(1))
                "make:migration" -> MakeMigrationCommand().run(args.drop(1))
                "make:factory" -> MakeFactoryCommand().run(args.drop(1))
                "make:seeder" -> MakeSeederCommand().run(args.drop(1))
                "migrate" -> MigrateCommand(MigrateCommand.Action.MIGRATE).run(args.drop(1))
                "migrate:rollback" -> MigrateCommand(MigrateCommand.Action.ROLLBACK).run(args.drop(1))
                "migrate:reset" -> MigrateCommand(MigrateCommand.Action.RESET).run(args.drop(1))
                "migrate:status" -> MigrateCommand(MigrateCommand.Action.STATUS).run(args.drop(1))
                "db:seed" -> SeedCommand().run(args.drop(1))
                "new" -> {
                    System.err.println("Create applications with the global CLI: kona new <name>")
                    System.err.println("grind is app-local — run ./grind serve from inside a project.")
                    1
                }
                else -> {
                    System.err.println("Unknown command: $command")
                    printHelp()
                    1
                }
            }
        } catch (ex: GrindException) {
            System.err.println(ex.message)
            1
        } catch (ex: ExtractException) {
            System.err.println(ex.message)
            1
        }
    }

    private fun printHelp() {
        println(
            """
            grind — Kona app CLI (run from your project root)

            Usage:
              ./grind serve [--host=127.0.0.1] [--port=8080] [--base=.]
                            [--in-process [--bootstrap=bootstrap.App] [--once]]
              ./grind make:controller <Name> [--lang=kotlin|java] [--base=.]
              ./grind make:model <Name> [--migration] [--factory] [--seeder] [--all] [--lang=kotlin|java]
              ./grind make:migration <snake_name> [--create=table] [--table=table] [--lang=kotlin|java]
              ./grind make:factory <Name>Factory [--model=Name] [--lang=kotlin|java]
              ./grind make:seeder <Name>Seeder [--lang=kotlin|java]
              ./grind migrate [--database=name] [--bootstrap=bootstrap.App]
              ./grind migrate:rollback [--step=1]
              ./grind migrate:reset
              ./grind migrate:status
              ./grind db:seed [--class=database.seeders.DatabaseSeeder]
              ./grind help

            Database commands boot the app in-process from build/classes (run ./gradlew classes first)
            and use the MigrationRegistry bound in its container.

            Create a new application with:
              kona new <name> [--lang=kotlin|java]
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
