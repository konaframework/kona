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
        val (positional, options) = try {
            parseOptions(args.toList())
        } catch (ex: GrindException) {
            GrindIO(color = GrindIO.detectColor()).error(ex.message ?: "grind error")
            return 1
        }
        val io = GrindIO.fromOptions(options)
        GrindLogging.hushConsole(io.verbosity == Verbosity.VERBOSE)
        if (positional.isEmpty() || positional[0] == "help" || positional[0] == "--help" || positional[0] == "-h") {
            val stray = options.keys.firstOrNull { it !in SignatureParser.GLOBAL_OPTIONS }
            if (stray != null) {
                io.error("Unknown option [--$stray]")
                return 1
            }
            discoverCustomCommands(options)
            printHelp(io)
            return 0
        }
        discoverCustomCommands(options)
        val command = positional[0]
        val rest = positional.drop(1)
        val flattened = rest + flatten(options)
        val spec = BuiltinCommandSpecs[command]
        if (spec != null && options.containsKey("help")) {
            CommandHelp.print(io, spec)
            return 0
        }
        return try {
            if (spec != null) {
                SignatureParser.bind(spec, rest, options)
            }
            when (command) {
                "serve" -> ServeCommand().run(flattened)
                "route:list" -> RouteCommand(RouteCommand.Action.LIST).run(flattened)
                "route:cache" -> RouteCommand(RouteCommand.Action.CACHE).run(flattened)
                "route:clear" -> RouteCommand(RouteCommand.Action.CLEAR).run(flattened)
                "make:controller" -> MakeControllerCommand().run(flattened)
                "make:command" -> MakeCommandCommand().run(flattened)
                "make:resource" -> MakeResourceCommand().run(flattened)
                "make:middleware" -> MakeMiddlewareCommand().run(flattened)
                "make:policy" -> MakePolicyCommand().run(flattened)
                "make:request" -> MakeRequestCommand().run(flattened)
                "make:view" -> MakeViewCommand().run(flattened)
                "make:firefly-layout" -> MakeFireflyLayoutCommand().run(flattened)
                "make:firefly-action" -> MakeFireflyActionCommand().run(flattened)
                "make:firefly-entry" -> MakeFireflyEntryCommand().run(flattened)
                "make:firefly-column" -> MakeFireflyColumnCommand().run(flattened)
                "make:firefly-filter" -> MakeFireflyFilterCommand().run(flattened)
                "make:firefly-field" -> MakeFireflyFieldCommand().run(flattened)
                "make:firefly-widget" -> MakeFireflyWidgetCommand().run(flattened)
                "make:firefly-panel" -> MakeFireflyPanelCommand().run(flattened)
                "make:firefly-page" -> MakeFireflyPageCommand().run(flattened)
                "make:firefly-theme" -> MakeFireflyThemeCommand().run(flattened)
                "make:firefly-resource" -> MakeFireflyResourceCommand().run(flattened)
                "firefly:install" -> FireflyInstallCommand().run(flattened)
                "firefly:publish" -> FireflyPublishCommand().run(flattened)
                "make:model" -> MakeModelCommand().run(flattened)
                "make:migration" -> MakeMigrationCommand().run(flattened)
                "make:factory" -> MakeFactoryCommand().run(flattened)
                "make:seeder" -> MakeSeederCommand().run(flattened)
                "migrate" -> MigrateCommand(MigrateCommand.Action.MIGRATE).run(flattened)
                "migrate:rollback" -> MigrateCommand(MigrateCommand.Action.ROLLBACK).run(flattened)
                "migrate:reset" -> MigrateCommand(MigrateCommand.Action.RESET).run(flattened)
                "migrate:refresh" -> MigrateCommand(MigrateCommand.Action.REFRESH).run(flattened)
                "migrate:fresh" -> MigrateCommand(MigrateCommand.Action.FRESH).run(flattened)
                "migrate:status" -> MigrateCommand(MigrateCommand.Action.STATUS).run(flattened)
                "migrate:install" -> MigrateCommand(MigrateCommand.Action.INSTALL).run(flattened)
                "db:wipe" -> MigrateCommand(MigrateCommand.Action.WIPE).run(flattened)
                "db:seed" -> SeedCommand().run(flattened)
                "db:show" -> DatabaseInspectCommand(DatabaseInspectCommand.Action.SHOW).run(flattened)
                "db:table" -> DatabaseInspectCommand(DatabaseInspectCommand.Action.TABLE).run(flattened)
                "schema:dump" -> DatabaseInspectCommand(DatabaseInspectCommand.Action.DUMP).run(flattened)
                "queue:work" -> QueueWorkCommand().run(flattened)
                "queue:failed" -> QueueFailedCommand(QueueFailedCommand.Action.LIST).run(flattened)
                "queue:retry" -> QueueFailedCommand(QueueFailedCommand.Action.RETRY).run(flattened)
                "queue:flush" -> QueueFailedCommand(QueueFailedCommand.Action.FLUSH).run(flattened)
                "schedule:run" -> ScheduleRunCommand(ScheduleRunCommand.Action.RUN).run(flattened)
                "schedule:list" -> ScheduleRunCommand(ScheduleRunCommand.Action.LIST).run(flattened)
                "schedule:work" -> ScheduleRunCommand(ScheduleRunCommand.Action.WORK).run(flattened)
                "steam" -> SteamCommand().run(flattened)
                "assets:vite" -> AssetsViteCommand().run(flattened)
                "storage:link" -> StorageLinkCommand().run(flattened)
                "deploy:systemd" -> SystemdDeployCommand().run(flattened)
                "down" -> DownCommand().run(flattened)
                "up" -> UpCommand().run(flattened)
                "key:generate" -> KeyGenerateCommand().run(flattened)
                "ide:lsp" -> IdeLspCommand().run(flattened)
                "install" -> InstallCommand(basePath).run(flattened)
                "list" -> ListCommand(registry).run(flattened)
                "new" -> {
                    io.error("Create applications with the global CLI: kona new")
                    io.warn("grind is app-local — run ./grind serve from inside a project.")
                    1
                }
                else ->
                    if (GradleAliasCommand.isGradleToken(command)) {
                        GradleAliasCommand.fromToken(command).run(flattened)
                    } else {
                        runCustomOrUnknown(command, flattened, io, options)
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
                        else -> throw GrindException("Unknown option [-$c]")
                    }
                }
            }
            else -> positional += arg
        }
    }
    return positional to options
}
