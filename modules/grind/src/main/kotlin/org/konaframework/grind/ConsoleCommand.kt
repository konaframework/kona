package org.konaframework.grind

import org.konaframework.core.Application

/**
 * Base type for custom grind commands (app or package).
 *
 * Override [signature], [description], and [handle]. Use [argument] / [option] after the
 * runner binds CLI input. Prefer [info] / [table] / [error] over raw prints.
 *
 * When grind boots the application (META-INF / provider commands), [app] is bound and
 * constructor / `handle(deps…)` parameters are resolved from the container.
 */
abstract class ConsoleCommand {
    abstract val signature: String

    open val description: String = ""

    /** When true, `--isolated` is accepted and only one process may run the command at a time. */
    open val isolatable: Boolean = false

    private lateinit var boundIo: GrindIO

    private lateinit var input: CommandInput

    private var boundApp: Application? = null

    private var boundPrompts: Prompts? = null

    val io: GrindIO
        get() = boundIo

    /** Booted application for this invocation (META-INF / provider / ConsoleTester.forApp). */
    val app: Application
        get() = boundApp ?: throw GrindException("Application is not bound to this command")

    /** True when [app] has been bound for this run. */
    fun hasApp(): Boolean = boundApp != null

    val parsedSignature: CommandSignature by lazy { SignatureParser.parse(signature, description) }

    fun name(): String = parsedSignature.name

    internal fun bind(input: CommandInput, io: GrindIO) {
        this.input = input
        this.boundIo = io
    }

    internal fun bindApp(application: Application) {
        boundApp = application
    }

    internal fun bindPrompts(prompts: Prompts) {
        boundPrompts = prompts
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

    fun intro(title: String) = io.intro(title)

    fun outro(message: String) = io.outro(message)

    fun note(message: String) = io.note(message)

    fun note(message: String, label: String) = io.note(message, label)

    fun table(headers: List<String>, rows: List<List<String>>) = io.table(headers, rows)

    fun progress(current: Int, total: Int) = io.progress(current, total, "")

    fun progress(current: Int, total: Int, label: String) = io.progress(current, total, label)

    fun verbose(message: String) = io.verbose(message)

    /** Interactive text prompt (see [Prompts.ask]). */
    @JvmOverloads
    fun ask(question: String, default: String? = null): String = prompts().ask(question, default)

    /** Yes/No confirm prompt (see [Prompts.confirm]). */
    @JvmOverloads
    fun confirm(question: String, default: Boolean = false): Boolean = prompts().confirm(question, default)

    /** Choice list prompt (see [Prompts.choice]). */
    @JvmOverloads
    fun choice(question: String, options: List<String>, defaultIndex: Int = 0): String =
        prompts().choice(question, options, defaultIndex)

    /** Hidden-input prompt (does not echo). Alias of [password]. */
    @JvmOverloads
    fun secret(question: String = "Password"): String = prompts().secret(question)

    /** Hidden-input prompt (does not echo). Alias of [secret]. */
    @JvmOverloads
    fun password(question: String = "Password"): String = secret(question)

    private fun prompts(): Prompts = boundPrompts ?: Prompts()

    /**
     * Command body. Return [SUCCESS] or [FAILURE] (or other non-zero).
     *
     * Prefer overriding this zero-arg form, or declare another `handle(deps…): Int` whose
     * parameters are resolved from the application container when the app is bound.
     */
    open fun handle(): Int {
        throw GrindException("${javaClass.name} must override handle()")
    }

    companion object {
        const val SUCCESS = 0
        const val FAILURE = 1
    }
}
