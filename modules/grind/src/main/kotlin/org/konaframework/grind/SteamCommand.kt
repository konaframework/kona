package org.konaframework.grind

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException

/**
 * `grind steam` — interactive REPL in a bootstrapped application context.
 *
 * Binds `app` into the Kotlin scripting engine when available; otherwise falls back to
 * a tiny evaluator that prints resolved container types / config keys.
 */
class SteamCommand(
    private val input: InputStream = System.`in`,
    private val output: PrintStream = System.out,
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO(out = output, verbosity = GrindIO.fromOptions(options).verbosity)
        MigrateCommand.withApplication(options) { app, loader ->
            io.box("steam", listOf("Kona REPL — type :quit to exit"))
            val engine = createEngine(loader)
            engine?.put("app", app)
            val reader = BufferedReader(InputStreamReader(input))
            while (true) {
                output.print("steam> ")
                output.flush()
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed == ":quit" || trimmed == ":exit" || trimmed == "exit") break
                try {
                    val result = if (engine != null) {
                        engine.eval(trimmed)
                    } else {
                        evaluateFallback(app, trimmed)
                    }
                    if (result != null) {
                        io.writeln(result.toString())
                    }
                } catch (ex: ScriptException) {
                    io.error(ex.message ?: ex.toString())
                } catch (ex: Exception) {
                    io.error(ex.message ?: ex.toString())
                }
            }
        }
        return 0
    }

    private fun createEngine(loader: ClassLoader): ScriptEngine? {
        val manager = ScriptEngineManager(loader)
        return manager.getEngineByExtension("kts")
            ?: manager.getEngineByName("kotlin")
            ?: manager.getEngineByName("js")
    }

    private fun evaluateFallback(app: org.konaframework.core.Application, expression: String): Any? {
        val configGet = Regex("""^config\((['"])(.+)\1\)$""").matchEntire(expression)
        if (configGet != null) {
            return app.config.getString(configGet.groupValues[2])
        }
        if (expression == "app") return app
        if (expression.matches(Regex("""^\d+(\s*[+\-*/]\s*\d+)+$"""))) {
            return simpleMath(expression)
        }
        throw GrindException(
            "No scripting engine available. Try config(\"key\"), app, or simple arithmetic.",
        )
    }

    private fun simpleMath(expression: String): Long {
        val tokens = Regex("""\d+|[+\-*/]""").findAll(expression.replace(" ", "")).map { it.value }.toList()
        require(tokens.isNotEmpty()) { "empty expression" }
        var value = tokens[0].toLong()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val rhs = tokens[i + 1].toLong()
            value = when (op) {
                "+" -> value + rhs
                "-" -> value - rhs
                "*" -> value * rhs
                "/" -> value / rhs
                else -> throw GrindException("Unknown operator $op")
            }
            i += 2
        }
        return value
    }
}
