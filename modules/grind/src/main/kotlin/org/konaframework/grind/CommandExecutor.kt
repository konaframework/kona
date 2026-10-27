package org.konaframework.grind

import java.nio.file.Path

/**
 * Binds CLI args to a [ConsoleCommand] and runs [ConsoleCommand.handle],
 * including `--help` and optional `--isolated` locking.
 */
class CommandExecutor(
    private val basePath: Path = Path.of(".").toAbsolutePath().normalize(),
) {
    fun run(command: ConsoleCommand, args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        val signature = command.parsedSignature
        if (options.containsKey("help")) {
            CommandHelp.print(io, signature)
            return ConsoleCommand.SUCCESS
        }
        val input = try {
            SignatureParser.bind(signature, positional, options)
        } catch (ex: GrindException) {
            io.error(formatGrindError(ex))
            CommandHelp.print(io, signature)
            return ConsoleCommand.FAILURE
        }
        command.bind(input, io)
        if (options.containsKey("isolated")) {
            if (command.isolatable) {
                return CommandIsolation(basePath).runIsolated(signature.name) {
                    command.handle()
                }
            }
            io.warn("Command ${signature.name} is not isolatable; ignoring --isolated")
        }
        return command.handle()
    }

    companion object {
        internal fun formatGrindError(ex: GrindException): String =
            ex.message ?: "Invalid arguments"
    }
}
