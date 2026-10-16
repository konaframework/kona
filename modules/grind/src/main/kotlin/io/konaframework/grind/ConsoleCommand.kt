package io.konaframework.grind

/**
 * Base type for custom grind commands (app or package).
 *
 * Override [signature], [description], and [handle]. Use [argument] / [option] after the
 * runner binds CLI input. Prefer [info] / [table] / [error] over raw prints.
 */
abstract class ConsoleCommand {
    abstract val signature: String

    open val description: String = ""

    /** When true, `--isolated` is accepted and only one process may run the command at a time. */
    open val isolatable: Boolean = false

    private lateinit var boundIo: GrindIO

    private lateinit var input: CommandInput

    val io: GrindIO
        get() = boundIo

    val parsedSignature: CommandSignature by lazy { SignatureParser.parse(signature, description) }

    fun name(): String = parsedSignature.name

    internal fun bind(input: CommandInput, io: GrindIO) {
        this.input = input
        this.boundIo = io
    }

    fun argument(name: String): String? = input.argument(name)

    fun argumentOrFail(name: String): String =
        argument(name) ?: throw GrindException("Missing argument [$name]")

    fun arguments(name: String): List<String> = input.arguments(name)

    fun option(name: String): String? = input.option(name)

    fun option(name: String, default: String): String = input.option(name) ?: default

    fun hasOption(name: String): Boolean = input.hasOption(name)

    fun optionBool(name: String): Boolean {
        val value = input.option(name) ?: return false
        return value == "true" || value == "1" || value.equals("yes", ignoreCase = true)
    }

    fun line() = io.line()

    fun line(message: String) = io.line(message)

    fun info(message: String) = io.info(message)

    fun success(message: String) = io.success(message)

    fun warn(message: String) = io.warn(message)

    fun error(message: String) = io.error(message)

    fun title(message: String) = io.title(message)

    fun table(headers: List<String>, rows: List<List<String>>) = io.table(headers, rows)

    fun progress(current: Int, total: Int) = io.progress(current, total, "")

    fun progress(current: Int, total: Int, label: String) = io.progress(current, total, label)

    fun verbose(message: String) = io.verbose(message)

    /** Command body. Return [SUCCESS] or [FAILURE] (or other non-zero). */
    abstract fun handle(): Int

    companion object {
        const val SUCCESS = 0
        const val FAILURE = 1
    }
}
