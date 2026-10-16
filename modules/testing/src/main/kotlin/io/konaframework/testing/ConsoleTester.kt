package io.konaframework.testing

import io.konaframework.grind.GrindApplication
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Runs grind commands and captures stdout for assertions.
 */
class ConsoleTester {
    fun run(vararg args: String): ConsoleResult {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        return try {
            val code = GrindApplication().run(arrayOf(*args))
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
