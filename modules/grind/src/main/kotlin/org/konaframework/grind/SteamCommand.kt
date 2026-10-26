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
    /** Test seam. Production uses [createEngine]. */
    internal var engineFactory: (ClassLoader) -> ScriptEngine? = { loader -> createEngine(loader) }

    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO(out = output, verbosity = GrindIO.fromOptions(options).verbosity)
        MigrateCommand.withApplication(options) { app, loader ->
            io.intro("steam")
            io.box("steam", listOf("Kona REPL — type :quit to exit"))
            val engine = engineFactory(loader)
            if (engine != null) {
                engine.put("app", app)
            }
            val reader = BufferedReader(InputStreamReader(input))
            while (true) {
                output.print("steam> ")
                output.flush()
                val line = reader.readLine()
                if (line == null) {
                    break
                }
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

    internal fun createEngine(loader: ClassLoader): ScriptEngine? {
        val manager = ScriptEngineManager(loader)
        return selectEngine(
            manager.getEngineByExtension("kts"),
            manager.getEngineByName("kotlin"),
            manager.getEngineByName("js"),
        )
    }
}

internal fun selectEngine(kts: ScriptEngine?, kotlinEngine: ScriptEngine?, js: ScriptEngine?): ScriptEngine? {
    if (kts != null) {
        return kts
    }
    if (kotlinEngine != null) {
        return kotlinEngine
    }
    return js
}

internal fun evaluateFallback(app: org.konaframework.core.Application, expression: String): Any? {
    val configGet = Regex("""^config\((['"])(.+)\1\)$""").matchEntire(expression)
    val matchedMath = expression.matches(Regex("""^\d+(\s*[+\-*/]\s*\d+)+$"""))
    if (configGet == null && expression != "app" && !matchedMath) {
        throw GrindException(
            "No scripting engine available. Try config(\"key\"), app, or simple arithmetic.",
        )
    }
    if (configGet != null) {
        return app.config.getString(configGet.groupValues[2])
    }
    if (expression == "app") {
        return app
    }
    return simpleMath(expression)
}

internal fun simpleMath(expression: String): Long {
    val tokens = Regex("""\d+|[+\-*/]""").findAll(expression.replace(" ", "")).map { it.value }.toList()
    if (tokens.isEmpty()) {
        throw GrindException("empty expression")
    }
    var value = tokens[0].toLong()
    var index = 1
    while (index < tokens.size) {
        val op = tokens[index]
        val rhs = tokens[index + 1].toLong()
        val add = op == "+"
        val sub = op == "-"
        val mul = op == "*"
        value = if (add) {
            value + rhs
        } else if (sub) {
            value - rhs
        } else if (mul) {
            value * rhs
        } else {
            value / rhs
        }
        index += 2
    }
    return value
}
