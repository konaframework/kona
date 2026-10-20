package org.konaframework.support.console

import org.konaframework.support.KoverExclude
import java.io.IOException
import java.io.InputStream

data class CommandResult(val exitCode: Int, val stdout: String)

fun interface CommandRunner {
    fun run(command: List<String>, inheritInput: Boolean, captureOutput: Boolean): CommandResult
}

interface ShutdownHooks {
    fun add(hook: Thread)

    fun remove(hook: Thread)
}

interface RawMode : AutoCloseable {
    fun enter()

    override fun close()
}

object NoopRawMode : RawMode {
    override fun enter() {}

    override fun close() {}
}

class RawModeException(message: String) : RuntimeException(message)

/**
 * POSIX TTY helpers for boxed prompts. Production uses `stty` (no JLine) so unit tests
 * inject a [CommandRunner] and never touch the real terminal.
 */
object Tty {
    const val LINE_PROMPTS_ENV = "KONA_LINE_PROMPTS"

    /**
     * Raw widgets when [input] is the process stdin **and** fd 0 is a TTY.
     * Injected streams (tests, pipes) stay line-based. `System.console()` is not
     * required — IDE terminals often have a TTY with a null console.
     */
    @JvmStatic
    @JvmOverloads
    fun shouldUseWidgets(
        input: InputStream,
        systemIn: InputStream = System.`in`,
        env: Map<String, String> = System.getenv(),
        probe: TtyProbe = TtyProbe(),
    ): Boolean {
        val force = env[LINE_PROMPTS_ENV]
        if (force != null && force != "0" && !force.equals("false", ignoreCase = true)) {
            return false
        }
        if (input !== systemIn) {
            return false
        }
        return probe.stdinIsTty()
    }

    @JvmStatic
    fun promptContentWidth(columns: Int): Int {
        if (columns <= 0) {
            return ConsoleIO.PROMPT_WIDTH
        }
        return (columns - 4).coerceIn(36, 80)
    }
}

class TtyProbe(internal val runner: CommandRunner = ProcessCommandRunner()) {
    fun stdinIsTty(): Boolean {
        val test = runner.run(listOf("test", "-t", "0"), inheritInput = true, captureOutput = false)
        if (test.exitCode == 0) {
            return true
        }
        if (test.exitCode == 127) {
            val stty = runner.run(listOf("stty", "size"), inheritInput = true, captureOutput = true)
            return stty.exitCode == 0
        }
        return false
    }

    fun columns(env: Map<String, String>): Int {
        val fromEnv = env["COLUMNS"]?.toIntOrNull()
        if (fromEnv != null && fromEnv > 0) {
            return fromEnv
        }
        val stty = runner.run(listOf("stty", "size"), inheritInput = true, captureOutput = true)
        if (stty.exitCode == 0) {
            val parts = stty.stdout.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val cols = parts.last().toIntOrNull()
                if (cols != null && cols > 0) {
                    return cols
                }
            }
        }
        return 80
    }
}

class ProcessCommandRunner : CommandRunner {
    override fun run(command: List<String>, inheritInput: Boolean, captureOutput: Boolean): CommandResult {
        return try {
            val pb = ProcessBuilder(command)
            if (inheritInput) {
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT)
            } else {
                pb.redirectInput(ProcessBuilder.Redirect.PIPE)
            }
            if (captureOutput) {
                pb.redirectErrorStream(true)
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            }
            val proc = pb.start()
            if (!inheritInput) {
                proc.outputStream.close()
            }
            val stdout =
                if (captureOutput) {
                    proc.inputStream.readBytes().toString(Charsets.UTF_8)
                } else {
                    ""
                }
            CommandResult(proc.waitFor(), stdout)
        } catch (_: IOException) {
            CommandResult(127, "")
        }
    }
}

@KoverExclude
object ProductionRaw {
    @JvmStatic
    fun open(): RawMode = SttyRawMode()
}

@KoverExclude
object RuntimeShutdownHooks : ShutdownHooks {
    override fun add(hook: Thread) {
        try {
            Runtime.getRuntime().addShutdownHook(hook)
        } catch (_: IllegalStateException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    override fun remove(hook: Thread) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (_: IllegalStateException) {
        } catch (_: IllegalArgumentException) {
        }
    }
}

/**
 * `stty -echo -icanon min 1 time 0` on fd 0; restores the `stty -g` token in [close],
 * a shutdown hook, and after Ctrl+C (the prompt loop restores before throwing).
 */
class SttyRawMode(
    private val runner: CommandRunner = ProcessCommandRunner(),
    private val hooks: ShutdownHooks = RuntimeShutdownHooks,
) : RawMode {
    private var saved: String? = null
    private var hook: Thread? = null

    override fun enter() {
        if (saved != null) {
            return
        }
        val state = runner.run(listOf("stty", "-g"), inheritInput = true, captureOutput = true)
        val token = state.stdout.trim()
        if (state.exitCode != 0 || token.isEmpty()) {
            throw RawModeException("stty -g failed")
        }
        val raw =
            runner.run(
                listOf("stty", "-echo", "-icanon", "min", "1", "time", "0"),
                inheritInput = true,
                captureOutput = false,
            )
        if (raw.exitCode != 0) {
            throw RawModeException("stty raw failed")
        }
        saved = token
        val restore = Thread { restoreQuietly() }
        hook = restore
        hooks.add(restore)
    }

    override fun close() {
        restoreQuietly()
        val registered = hook
        hook = null
        if (registered != null) {
            hooks.remove(registered)
        }
    }

    private fun restoreQuietly() {
        val token = saved ?: return
        saved = null
        runner.run(listOf("stty", token), inheritInput = true, captureOutput = false)
    }
}
