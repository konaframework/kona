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
        val (positional, options) = parseOptions(args.toList())
        val io = GrindIO.fromOptions(options)
        if (positional.isEmpty() || positional[0] == "help" || positional[0] == "--help" || positional[0] == "-h") {
            printHelp(io)
            return 0
        }
        return try {
            when (val command = positional[0]) {
                "serve" -> ServeCommand().run(positional.drop(1) + flatten(options))
                "make:controller" -> MakeControllerCommand().run(positional.drop(1) + flatten(options))
                "make:middleware" -> MakeMiddlewareCommand().run(positional.drop(1) + flatten(options))
                "make:request" -> MakeRequestCommand().run(positional.drop(1) + flatten(options))
                "make:view" -> MakeViewCommand().run(positional.drop(1) + flatten(options))
                "make:model" -> MakeModelCommand().run(positional.drop(1) + flatten(options))
                "make:migration" -> MakeMigrationCommand().run(positional.drop(1) + flatten(options))
                "make:factory" -> MakeFactoryCommand().run(positional.drop(1) + flatten(options))
                "make:seeder" -> MakeSeederCommand().run(positional.drop(1) + flatten(options))
                "migrate" -> MigrateCommand(MigrateCommand.Action.MIGRATE).run(positional.drop(1) + flatten(options))
                "migrate:rollback" -> MigrateCommand(MigrateCommand.Action.ROLLBACK).run(positional.drop(1) + flatten(options))
                "migrate:reset" -> MigrateCommand(MigrateCommand.Action.RESET).run(positional.drop(1) + flatten(options))
                "migrate:status" -> MigrateCommand(MigrateCommand.Action.STATUS).run(positional.drop(1) + flatten(options))
                "db:seed" -> SeedCommand().run(positional.drop(1) + flatten(options))
                "queue:work" -> QueueWorkCommand().run(positional.drop(1) + flatten(options))
                "queue:failed" -> QueueFailedCommand(QueueFailedCommand.Action.LIST).run(positional.drop(1) + flatten(options))
                "queue:retry" -> QueueFailedCommand(QueueFailedCommand.Action.RETRY).run(positional.drop(1) + flatten(options))
                "queue:flush" -> QueueFailedCommand(QueueFailedCommand.Action.FLUSH).run(positional.drop(1) + flatten(options))
                "schedule:run" -> ScheduleRunCommand(ScheduleRunCommand.Action.RUN).run(positional.drop(1) + flatten(options))
                "schedule:list" -> ScheduleRunCommand(ScheduleRunCommand.Action.LIST).run(positional.drop(1) + flatten(options))
                "steam" -> SteamCommand().run(positional.drop(1) + flatten(options))
                "assets:vite" -> AssetsViteCommand().run(positional.drop(1) + flatten(options))
                "ide:lsp" -> IdeLspCommand().run(positional.drop(1) + flatten(options))
                "list" -> ListCommand().run(positional.drop(1) + flatten(options))
                "new" -> {
                    io.error("Create applications with the global CLI: kona new <name>")
                    io.error("grind is app-local — run ./grind serve from inside a project.")
                    1
                }
                else -> {
                    io.error("Unknown command: $command")
                    printHelp(io)
                    1
                }
            }
        } catch (ex: GrindException) {
            io.error(ex.message ?: "grind error")
            1
        } catch (ex: ExtractException) {
            io.error(ex.message ?: "extract error")
            1
        }
    }

    private fun flatten(options: Map<String, String>): List<String> =
        options.map { (k, v) -> if (v == "true") "--$k" else "--$k=$v" }

    private fun printHelp(io: GrindIO) {
        io.writeln(
            """
            grind — Kona app CLI (run from your project root)

            Global options: --verbose|-v  --quiet|-q

            Usage:
              ./grind list
              ./grind serve [--host=127.0.0.1] [--port=8080] [--base=.]
                            [--in-process [--bootstrap=bootstrap.App] [--once]]
              ./grind make:controller <Name> [--resource] [--lang=kotlin|java] [--base=.]
              ./grind make:middleware <Name> [--lang=kotlin|java] [--base=.]
              ./grind make:request <Name> [--lang=kotlin|java] [--base=.]
              ./grind make:view <name> [--base=.]
              ./grind make:model <Name> [--migration] [--factory] [--seeder] [--all] [--lang=kotlin|java]
              ./grind make:migration <snake_name> [--create=table] [--table=table] [--lang=kotlin|java]
              ./grind make:factory <Name>Factory [--model=Name] [--lang=kotlin|java]
              ./grind make:seeder <Name>Seeder [--lang=kotlin|java]
              ./grind migrate [--database=name] [--bootstrap=bootstrap.App]
              ./grind migrate:rollback [--step=1]
              ./grind migrate:reset
              ./grind migrate:status
              ./grind db:seed [--class=database.seeders.DatabaseSeeder]
              ./grind queue:work [--connection=name] [--queue=name] [--max=N]
              ./grind queue:failed
              ./grind queue:retry <id>|--all
              ./grind queue:flush
              ./grind schedule:run
              ./grind schedule:list
              ./grind steam [--bootstrap=bootstrap.App]
              ./grind assets:vite
              ./grind ide:lsp
              ./grind help

            Database, queue, schedule, and steam commands boot the app in-process from build/classes
            (run ./gradlew classes first) and use services bound in its container.

            Create a new application with:
              kona new <name> [--lang=kotlin|java] [--kit=api|crema|inertia|brew]
            """.trimIndent(),
        )
    }
}

class GrindException(message: String) : RuntimeException(message)

internal fun parseOptions(args: List<String>): Pair<List<String>, Map<String, String>> {
    val positional = mutableListOf<String>()
    val options = linkedMapOf<String, String>()
    args.forEach { arg ->
        when {
            arg == "-v" -> options["verbose"] = "true"
            arg == "-q" -> options["quiet"] = "true"
            arg.startsWith("--") -> {
                val body = arg.removePrefix("--")
                val idx = body.indexOf('=')
                if (idx < 0) {
                    options[body] = "true"
                } else {
                    options[body.substring(0, idx)] = body.substring(idx + 1)
                }
            }
            else -> positional += arg
        }
    }
    return positional to options
}
