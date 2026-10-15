package dev.kona.grind

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
            printCommandHelp(io, signature)
            return ConsoleCommand.SUCCESS
        }
        val input = try {
            SignatureParser.bind(signature, positional, options)
        } catch (ex: GrindException) {
            io.error(formatGrindError(ex))
            printCommandHelp(io, signature)
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

    private fun printCommandHelp(io: GrindIO, signature: CommandSignature) {
        val heading = if (signature.description.isEmpty()) signature.name else signature.description
        io.box(signature.name, listOf(heading))
        io.title("Signature")
        val rendered = buildString {
            append(signature.name)
            signature.arguments.forEach { append(" {").append(formatArg(it)).append('}') }
            signature.options.forEach { append(" {").append(formatOpt(it)).append('}') }
        }
        io.writeln(rendered)
        signature.helpLines().forEach { io.writeln(it) }
    }

    private fun formatArg(arg: ArgumentDefinition): String =
        buildString {
            append(arg.name)
            when {
                arg.isArray -> append('*')
                arg.optional -> append('?')
            }
        }

    private fun formatOpt(opt: OptionDefinition): String =
        buildString {
            append("--")
            append(opt.name)
            if (opt.acceptsValue) {
                append('=')
                if (opt.default != null) append(opt.default)
            }
        }

    companion object {
        internal fun formatGrindError(ex: GrindException): String =
            ex.message ?: "Invalid arguments"
    }
}
