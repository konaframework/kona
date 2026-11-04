package org.konaframework.testing

import org.konaframework.core.Application
import org.konaframework.grind.AppCommandRunner
import org.konaframework.grind.CommandRegistry
import org.konaframework.grind.GrindApplication
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Runs grind commands and captures stdout for assertions.
 *
 * Use the no-arg constructor for the full `GrindApplication` CLI, or construct with a
 * booted [Application] to run custom commands against that app (container, config,
 * database) without [org.konaframework.grind.AppLoader].
 *
 * ```kotlin
 * ConsoleTester(app).run("mail:digest", "42").assertOk()
 * ```
 *
 * Java: `new ConsoleTester(app).run("mail:digest", "42")`.
 */
class ConsoleTester {
    private val application: Application?
    private val registry: CommandRegistry

    constructor() {
        application = null
        registry = CommandRegistry()
    }

    constructor(application: Application) {
        this.application = application
        this.registry = CommandRegistry()
    }

    constructor(application: Application, registry: CommandRegistry) {
        this.application = application
        this.registry = registry
    }

    fun run(vararg args: String): ConsoleResult {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        return try {
            val app = application
            val code =
                if (app != null) {
                    require(args.isNotEmpty()) { "ConsoleTester with an application requires a command name" }
                    AppCommandRunner.runWithApp(app, args[0], args.drop(1), registry)
                } else {
                    GrindApplication().run(arrayOf(*args))
                }
            ConsoleResult(code, buffer.toString())
        } finally {
            System.setOut(original)
        }
    }
}

data class ConsoleResult(val exitCode: Int, val output: String) {
    fun assertOk(): ConsoleResult {
        require(exitCode == 0) { "Expected exit 0 but was $exitCode: $output" }
        return this
    }

    fun assertExit(code: Int): ConsoleResult {
        require(exitCode == code) { "Expected exit $code but was $exitCode: $output" }
        return this
    }

    fun assertSee(text: String): ConsoleResult {
        require(output.contains(text)) { "Output missing [$text]: $output" }
        return this
    }

    fun assertDontSee(text: String): ConsoleResult {
        require(!output.contains(text)) { "Output unexpectedly contains [$text]: $output" }
        return this
    }
}
