package org.konaframework.grind

import org.konaframework.cadence.CadenceException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * App-local `grind` CLI — serve and generate code inside a Kona application.
 * Create apps with the global `kona new` command.
 */
fun main(args: Array<String>) {
    exitProcess(GrindApplication().run(args))
}

class GrindApplication
@JvmOverloads
constructor(
    private val registry: CommandRegistry = CommandRegistry(),
    private val basePath: Path = Path.of(".").toAbsolutePath().normalize(),
    private val classLoader: ClassLoader = GrindApplication::class.java.classLoader,
) {
    fun run(args: Array<String>): Int {
        val (positional, options) = parseOptions(args.toList())
        val io = GrindIO.fromOptions(options)
        GrindLogging.hushConsole(io.verbosity == Verbosity.VERBOSE)
        if (positional.isEmpty() || positional[0] == "help" || positional[0] == "--help" || positional[0] == "-h") {
            discoverCustomCommands(options)
            printHelp(io)
            return 0
        }
        discoverCustomCommands(options)
        return try {
            when (val command = positional[0]) {
                "serve" -> ServeCommand().run(positional.drop(1) + flatten(options))
                "route:list" -> RouteCommand(RouteCommand.Action.LIST).run(positional.drop(1) + flatten(options))
                "route:cache" -> RouteCommand(RouteCommand.Action.CACHE).run(positional.drop(1) + flatten(options))
                "route:clear" -> RouteCommand(RouteCommand.Action.CLEAR).run(positional.drop(1) + flatten(options))
                "make:controller" -> MakeControllerCommand().run(positional.drop(1) + flatten(options))
                "make:command" -> MakeCommandCommand().run(positional.drop(1) + flatten(options))
                "make:resource" -> MakeResourceCommand().run(positional.drop(1) + flatten(options))
                "make:middleware" -> MakeMiddlewareCommand().run(positional.drop(1) + flatten(options))
                "make:policy" -> MakePolicyCommand().run(positional.drop(1) + flatten(options))
                "make:request" -> MakeRequestCommand().run(positional.drop(1) + flatten(options))
                "make:view" -> MakeViewCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-layout" -> MakeFireflyLayoutCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-action" -> MakeFireflyActionCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-entry" -> MakeFireflyEntryCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-column" -> MakeFireflyColumnCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-filter" -> MakeFireflyFilterCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-field" -> MakeFireflyFieldCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-widget" -> MakeFireflyWidgetCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-panel" -> MakeFireflyPanelCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-page" -> MakeFireflyPageCommand().run(positional.drop(1) + flatten(options))
                "make:firefly-theme" -> MakeFireflyThemeCommand().run(positional.drop(1) + flatten(options))
                "firefly:install" -> FireflyInstallCommand().run(positional.drop(1) + flatten(options))
                "firefly:publish" -> FireflyPublishCommand().run(positional.drop(1) + flatten(options))
                "make:model" -> MakeModelCommand().run(positional.drop(1) + flatten(options))
                "make:migration" -> MakeMigrationCommand().run(positional.drop(1) + flatten(options))
                "make:factory" -> MakeFactoryCommand().run(positional.drop(1) + flatten(options))
                "make:seeder" -> MakeSeederCommand().run(positional.drop(1) + flatten(options))
                "migrate" -> MigrateCommand(MigrateCommand.Action.MIGRATE).run(positional.drop(1) + flatten(options))
                "migrate:rollback" -> MigrateCommand(MigrateCommand.Action.ROLLBACK).run(positional.drop(1) + flatten(options))
                "migrate:reset" -> MigrateCommand(MigrateCommand.Action.RESET).run(positional.drop(1) + flatten(options))
                "migrate:refresh" -> MigrateCommand(MigrateCommand.Action.REFRESH).run(positional.drop(1) + flatten(options))
                "migrate:fresh" -> MigrateCommand(MigrateCommand.Action.FRESH).run(positional.drop(1) + flatten(options))
                "migrate:status" -> MigrateCommand(MigrateCommand.Action.STATUS).run(positional.drop(1) + flatten(options))
                "migrate:install" -> MigrateCommand(MigrateCommand.Action.INSTALL).run(positional.drop(1) + flatten(options))
                "db:wipe" -> MigrateCommand(MigrateCommand.Action.WIPE).run(positional.drop(1) + flatten(options))
                "db:seed" -> SeedCommand().run(positional.drop(1) + flatten(options))
                "db:show" -> DatabaseInspectCommand(DatabaseInspectCommand.Action.SHOW).run(positional.drop(1) + flatten(options))
                "db:table" -> DatabaseInspectCommand(DatabaseInspectCommand.Action.TABLE).run(positional.drop(1) + flatten(options))
                "schema:dump" -> DatabaseInspectCommand(DatabaseInspectCommand.Action.DUMP).run(positional.drop(1) + flatten(options))
                "queue:work" -> QueueWorkCommand().run(positional.drop(1) + flatten(options))
                "queue:failed" -> QueueFailedCommand(QueueFailedCommand.Action.LIST).run(positional.drop(1) + flatten(options))
                "queue:retry" -> QueueFailedCommand(QueueFailedCommand.Action.RETRY).run(positional.drop(1) + flatten(options))
                "queue:flush" -> QueueFailedCommand(QueueFailedCommand.Action.FLUSH).run(positional.drop(1) + flatten(options))
                "schedule:run" -> ScheduleRunCommand(ScheduleRunCommand.Action.RUN).run(positional.drop(1) + flatten(options))
                "schedule:list" -> ScheduleRunCommand(ScheduleRunCommand.Action.LIST).run(positional.drop(1) + flatten(options))
                "schedule:work" -> ScheduleRunCommand(ScheduleRunCommand.Action.WORK).run(positional.drop(1) + flatten(options))
                "steam" -> SteamCommand().run(positional.drop(1) + flatten(options))
                "assets:vite" -> AssetsViteCommand().run(positional.drop(1) + flatten(options))
                "storage:link" -> StorageLinkCommand().run(positional.drop(1) + flatten(options))
                "deploy:systemd" -> SystemdDeployCommand().run(positional.drop(1) + flatten(options))
                "down" -> DownCommand().run(positional.drop(1) + flatten(options))
                "up" -> UpCommand().run(positional.drop(1) + flatten(options))
                "key:generate" -> KeyGenerateCommand().run(positional.drop(1) + flatten(options))
                "ide:lsp" -> IdeLspCommand().run(positional.drop(1) + flatten(options))
                "install" -> InstallCommand(basePath).run(positional.drop(1) + flatten(options))
                "list" -> ListCommand(registry).run(positional.drop(1) + flatten(options))
                "new" -> {
                    io.error("Create applications with the global CLI: kona new")
                    io.warn("grind is app-local — run ./grind serve from inside a project.")
                    1
                }
                else ->
                    if (GradleAliasCommand.isGradleToken(command)) {
                        GradleAliasCommand.fromToken(command).run(positional.drop(1) + flatten(options))
                    } else {
                        runCustomOrUnknown(command, positional.drop(1) + flatten(options), io, options)
                    }
            }
        } catch (ex: GrindException) {
            io.error(ex.message ?: "grind error")
            1
        } catch (ex: CadenceException) {
            io.error(ex.message ?: "cadence error")
            1
        }
    }

    private fun discoverCustomCommands(options: Map<String, String>) {
        CommandDiscovery.registerAll(registry, classLoader)
        val base = options["base"]?.let { Path.of(it).toAbsolutePath().normalize() } ?: basePath
        val dirs = APP_OUTPUT_DIRS.map { base.resolve(it) }.filter { Files.isDirectory(it) }
        if (dirs.isEmpty()) {
            return
        }
        val loader = URLClassLoader(dirs.map { it.toUri().toURL() }.toTypedArray(), classLoader)
        CommandDiscovery.registerAll(registry, loader)
    }

    private fun runCustomOrUnknown(
        command: String,
        args: List<String>,
        io: GrindIO,
        options: Map<String, String>,
    ): Int {
        val custom = registry.create(command)
        if (custom != null) {
            val base = options["base"]?.let { Path.of(it).toAbsolutePath().normalize() } ?: basePath
            return CommandExecutor(base).run(custom, args)
        }
        io.error("Unknown command: $command")
        printHelp(io)
        return 1
    }

    private fun flatten(options: Map<String, String>): List<String> =
        options.map { (k, v) -> if (v == "true") "--$k" else "--$k=$v" }

    private fun printHelp(io: GrindIO) {
        io.intro("grind")
        io.box(
            "grind",
            listOf(
                "Kona app CLI — run from your project root",
                "Global: --verbose|-v  --quiet|-q",
            ),
        )
        io.title("Available commands")
        (ListCommand.BUILTIN + registry.summaries()).distinctBy { it.first }.forEach { (cmd, summary) ->
            io.twoColumn(cmd, summary)
        }
        io.writeln()
        io.info("Create apps with: kona new [name] [--lang=kotlin|java] [--kit=…]")
        io.info("Custom commands: grind make:command <Name>")
        io.outro("https://docs.konaframework.org/docs/grind/")
    }

    companion object {
        private val APP_OUTPUT_DIRS = listOf(
            "build/classes/kotlin/main",
            "build/classes/java/main",
            "build/resources/main",
        )
    }
}

class GrindException(message: String?) : RuntimeException(message)

internal fun parseOptions(args: List<String>): Pair<List<String>, Map<String, String>> {
    val positional = mutableListOf<String>()
    val options = linkedMapOf<String, String>()
    args.forEach { arg ->
        when {
            arg.startsWith("--") -> {
                val body = arg.removePrefix("--")
                val idx = body.indexOf('=')
                if (idx < 0) {
                    options[body] = "true"
                } else {
                    options[body.substring(0, idx)] = body.substring(idx + 1)
                }
                // Laravel aliases: --seed → seeder
                if (options.containsKey("seed") && !options.containsKey("seeder")) {
                    options["seeder"] = options.getValue("seed")
                }
            }
            // Short / clustered Laravel-style flags: -m, -mcr, -a, -v, …
            arg.startsWith("-") && arg.length > 1 -> {
                for (c in arg.removePrefix("-")) {
                    when (c) {
                        'v' -> options["verbose"] = "true"
                        'q' -> options["quiet"] = "true"
                        'h' -> options["help"] = "true"
                        'm' -> options["migration"] = "true"
                        'f' -> options["factory"] = "true"
                        's' -> options["seeder"] = "true"
                        'c' -> options["controller"] = "true"
                        'r' -> options["resource"] = "true"
                        'p' -> options["policy"] = "true"
                        'a' -> options["all"] = "true"
                        'R' -> options["requests"] = "true"
                        'w' -> options["watch"] = "true"
                    }
                }
            }
            else -> positional += arg
        }
    }
    return positional to options
}
