package org.konaframework.grind

import org.konaframework.core.Application

/**
 * Named grind commands available to [GrindApplication] and [Grind.call].
 */
class CommandRegistry {
    private val factories = linkedMapOf<String, () -> ConsoleCommand>()
    private val types = linkedMapOf<String, Class<out ConsoleCommand>>()

    fun register(factory: () -> ConsoleCommand): CommandRegistry {
        val probe = factory()
        val name = probe.name()
        factories[name] = factory
        types[name] = probe.javaClass
        return this
    }

    fun register(type: Class<out ConsoleCommand>): CommandRegistry {
        val probe = type.getDeclaredConstructor().newInstance()
        val name = probe.name()
        types[name] = type
        factories[name] = { type.getDeclaredConstructor().newInstance() }
        return this
    }

    fun has(name: String): Boolean = factories.containsKey(name)

    fun create(name: String): ConsoleCommand? = factories[name]?.invoke()

    /** Instantiates via the application container when [app] is set. */
    fun create(name: String, app: Application?): ConsoleCommand? {
        val type = types[name]
        if (type != null) {
            return CommandExecutor.createCommand(type, app)
        }
        return create(name)
    }

    fun type(name: String): Class<out ConsoleCommand>? = types[name]

    fun names(): Set<String> = factories.keys

    /** Name → description for `grind list`. */
    fun summaries(): List<Pair<String, String>> =
        factories.map { (name, factory) ->
            val cmd = factory()
            name to cmd.description.ifEmpty { cmd.signature }
        }

    fun merge(other: CommandRegistry): CommandRegistry {
        other.factories.forEach { (name, factory) -> factories[name] = factory }
        other.types.forEach { (name, type) -> types[name] = type }
        return this
    }

    /** Registers [Application.consoleCommands] after validating each extends [ConsoleCommand]. */
    fun registerApplicationCommands(app: Application): CommandRegistry {
        for (raw in app.consoleCommands()) {
            if (!ConsoleCommand::class.java.isAssignableFrom(raw)) {
                throw GrindException("Command [${raw.name}] must extend ConsoleCommand")
            }
            @Suppress("UNCHECKED_CAST")
            val type = raw as Class<out ConsoleCommand>
            val instance = CommandExecutor.createCommand(type, app)
            val name = instance.name()
            types[name] = type
            factories[name] = { CommandExecutor.createCommand(type, app) }
        }
        return this
    }
}
