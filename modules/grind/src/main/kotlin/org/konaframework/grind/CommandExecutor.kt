package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.core.BindingException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Path

/**
 * Binds CLI args to a [ConsoleCommand] and runs [ConsoleCommand.handle],
 * including `--help` and optional `--isolated` locking.
 *
 * When [application] is set, the command receives [ConsoleCommand.app] and constructor /
 * injectable `handle(deps…)` parameters are resolved from the container.
 */
class CommandExecutor
@JvmOverloads
constructor(
    private val basePath: Path = Path.of(".").toAbsolutePath().normalize(),
    private val application: Application? = null,
    private val prompts: Prompts? = null,
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
        if (application != null) {
            command.bindApp(application)
        }
        if (prompts != null) {
            command.bindPrompts(prompts)
        }
        if (options.containsKey("isolated")) {
            if (command.isolatable) {
                return CommandIsolation(basePath).runIsolated(signature.name) {
                    invokeHandle(command)
                }
            }
            io.warn("Command ${signature.name} is not isolatable; ignoring --isolated")
        }
        return invokeHandle(command)
    }

    private fun invokeHandle(command: ConsoleCommand): Int {
        val app = application
        if (app == null) {
            return command.handle()
        }
        val injectable = selectInjectableHandle(command.javaClass)
        if (injectable == null || injectable.parameterCount == 0) {
            return command.handle()
        }
        injectable.isAccessible = true
        val args = injectable.parameterTypes.map { type -> app.container.make(type) }.toTypedArray()
        return try {
            injectable.invoke(command, *args) as Int
        } catch (ex: InvocationTargetException) {
            throw ex.targetException
        }
    }

    companion object {
        internal fun formatGrindError(ex: GrindException): String =
            ex.message ?: "Invalid arguments"

        /**
         * Builds a [ConsoleCommand] from the container when possible, otherwise the no-arg constructor.
         */
        internal fun createCommand(type: Class<out ConsoleCommand>, app: Application?): ConsoleCommand {
            if (app != null) {
                try {
                    return app.make(type)
                } catch (_: BindingException) {
                    // Fall through to no-arg construction for simple commands.
                }
            }
            return type.getDeclaredConstructor().newInstance()
        }

        /**
         * Prefers a concrete `handle(deps…): Int` with parameters (container-injected) over the
         * zero-arg [ConsoleCommand.handle] override.
         */
        internal fun selectInjectableHandle(type: Class<*>): Method? =
            type.declaredMethods
                .filter { method ->
                    method.name == "handle" &&
                        method.returnType == Int::class.javaPrimitiveType &&
                        method.declaringClass != ConsoleCommand::class.java
                }
                .maxByOrNull { it.parameterCount }
    }
}
