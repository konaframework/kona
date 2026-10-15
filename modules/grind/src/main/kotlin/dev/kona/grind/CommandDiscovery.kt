package dev.kona.grind

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Discovers custom grind commands via `META-INF/kona/commands` (one FQCN per line).
 * Same shape as [PackageDiscovery] for providers.
 */
object CommandDiscovery {
    const val RESOURCE = "META-INF/kona/commands"

    fun commandNames(loader: ClassLoader): List<String> {
        val names = linkedSetOf<String>()
        val resources = loader.getResources(RESOURCE)
        while (resources.hasMoreElements()) {
            resources.nextElement().openStream().use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { line ->
                            if (line.isEmpty()) {
                                false
                            } else {
                                !line.startsWith("#")
                            }
                        }
                        .forEach { names += it }
                }
            }
        }
        return names.toList()
    }

    fun loadCommands(loader: ClassLoader): List<ConsoleCommand> =
        commandNames(loader).map { name ->
            val type = Class.forName(name, true, loader)
            require(ConsoleCommand::class.java.isAssignableFrom(type)) {
                "Command [$name] must extend ConsoleCommand"
            }
            @Suppress("UNCHECKED_CAST")
            (type as Class<out ConsoleCommand>).getDeclaredConstructor().newInstance()
        }

    fun registerAll(registry: CommandRegistry, loader: ClassLoader): List<String> {
        val names = commandNames(loader)
        loadCommands(loader).forEach { cmd -> registry.register(cmd.javaClass) }
        return names
    }
}
