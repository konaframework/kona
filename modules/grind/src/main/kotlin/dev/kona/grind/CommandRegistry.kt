package dev.kona.grind

/**
 * Named grind commands available to [GrindApplication] and [Grind.call].
 */
class CommandRegistry {
    private val factories = linkedMapOf<String, () -> ConsoleCommand>()

    fun register(factory: () -> ConsoleCommand): CommandRegistry {
        val probe = factory()
        factories[probe.name()] = factory
        return this
    }

    fun register(type: Class<out ConsoleCommand>): CommandRegistry =
        register { type.getDeclaredConstructor().newInstance() }

    fun has(name: String): Boolean = factories.containsKey(name)

    fun create(name: String): ConsoleCommand? = factories[name]?.invoke()

    fun names(): Set<String> = factories.keys

    /** Name → description for `grind list`. */
    fun summaries(): List<Pair<String, String>> =
        factories.map { (name, factory) ->
            val cmd = factory()
            name to cmd.description.ifEmpty { cmd.signature }
        }

    fun merge(other: CommandRegistry): CommandRegistry {
        other.factories.forEach { (name, factory) -> factories[name] = factory }
        return this
    }
}
