package org.konaframework.grind

import org.konaframework.core.Application
import java.nio.file.Path

/**
 * Runs custom [ConsoleCommand]s inside a booted application (Laravel Artisan parity).
 *
 * When a Kona project root is found, boots via [MigrateCommand.withApplication] (same path as
 * `migrate` / `db:seed`), merges META-INF discovery + [Application.commands], then executes.
 * Without a project root (unit tests / bare registry), runs the command without booting.
 */
object AppCommandRunner {
    fun run(
        commandName: String,
        args: List<String>,
        options: Map<String, String>,
        registry: CommandRegistry,
        basePath: Path,
        io: GrindIO,
        prompts: Prompts? = null,
    ): Int {
        val rawBase = options["base"]
        val start =
            if (rawBase == null) {
                basePath
            } else {
                Path.of(rawBase).toAbsolutePath().normalize()
            }
        val root = ServeCommand.findProjectRoot(start)
        if (root == null) {
            return runWithoutBoot(commandName, args, registry, start, io, prompts)
        }
        val bootOptions = LinkedHashMap(options)
        bootOptions.putIfAbsent("base", root.toString())
        return MigrateCommand.withApplication(bootOptions) { app, loader ->
            CommandDiscovery.registerAll(registry, loader)
            registry.registerApplicationCommands(app)
            val custom = registry.create(commandName, app)
            if (custom == null) {
                io.error("Unknown command: $commandName")
                return@withApplication ConsoleCommand.FAILURE
            }
            CommandExecutor(root, app, prompts).run(custom, args)
        }
    }

    /**
     * Runs a custom command against an already-booted [app] (tests / in-process callers).
     * Does not compile or use [AppLoader].
     */
    fun runWithApp(
        app: Application,
        commandName: String,
        args: List<String>,
        registry: CommandRegistry = CommandRegistry(),
        prompts: Prompts? = null,
    ): Int {
        val loader = Thread.currentThread().contextClassLoader ?: app.javaClass.classLoader
        CommandDiscovery.registerAll(registry, loader)
        registry.registerApplicationCommands(app)
        val custom = registry.create(commandName, app)
            ?: throw GrindException("Unknown command: $commandName")
        return CommandExecutor(app.basePath, app, prompts).run(custom, args)
    }

    private fun runWithoutBoot(
        commandName: String,
        args: List<String>,
        registry: CommandRegistry,
        base: Path,
        io: GrindIO,
        prompts: Prompts?,
    ): Int {
        val custom = registry.create(commandName)
        if (custom != null) {
            return CommandExecutor(base, application = null, prompts = prompts).run(custom, args)
        }
        io.error("Unknown command: $commandName")
        return ConsoleCommand.FAILURE
    }
}
