package org.konaframework.support.console

import org.konaframework.support.KoverExclude
import java.io.File
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
     * Raw widgets when [input] is the process stdin **and** either fd 0 is a TTY
     * or the controlling terminal (`/dev/tty`) can take `stty`. Gradle JavaExec
     * workers typically see a pipe on fd 0 even when `./grind` was started from
     * a real terminal — without `/dev/tty`, Yes/No arrows never move.
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
        return probe.stdinIsTty() || probe.controllingTtyUsable()
    }

    @JvmStatic
    fun promptContentWidth(columns: Int): Int {
        if (columns <= 0) {
            return ConsoleIO.PROMPT_WIDTH
        }
        return (columns - 4).coerceIn(36, 80)
    }
}

class TtyProbe(
    internal val runner: CommandRunner = ProcessCommandRunner(),
    internal val controllingTty: String? = null,
    private val osName: String = System.getProperty("os.name", ""),
) {
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

    fun controllingTtyUsable(): Boolean {
        val path = controllingTty ?: return false
        val state =
            runner.run(
                sttyCommand(listOf("-g"), path, osName),
                inheritInput = false,
                captureOutput = true,
            )
        return state.exitCode == 0 && state.stdout.trim().isNotEmpty()
    }

    fun columns(env: Map<String, String>): Int {
        val fromEnv = env["COLUMNS"]?.toIntOrNull()
        if (fromEnv != null && fromEnv > 0) {
            return fromEnv
        }
        val inherit = runner.run(listOf("stty", "size"), inheritInput = true, captureOutput = true)
        parseColumns(inherit)?.let { return it }
        val path = controllingTty
        if (path != null) {
            val viaDevice =
                runner.run(
                    sttyCommand(listOf("size"), path, osName),
                    inheritInput = false,
                    captureOutput = true,
                )
            parseColumns(viaDevice)?.let { return it }
        }
        return 80
    }

    private fun parseColumns(stty: CommandResult): Int? {
        if (stty.exitCode != 0) {
            return null
        }
        val parts = stty.stdout.trim().split(Regex("\\s+"))
        if (parts.size >= 2) {
            val cols = parts.last().toIntOrNull()
            if (cols != null && cols > 0) {
                return cols
            }
        }
        return null
    }
}

/** POSIX `stty -F` (Linux) / `stty -f` (macOS) so raw mode can target `/dev/tty`. */
internal fun sttyFileFlag(osName: String): String {
    val n = osName.lowercase()
    return if (n.startsWith("mac") || n.contains("darwin")) "-f" else "-F"
}

internal fun sttyCommand(args: List<String>, device: String?, osName: String): List<String> {
    if (device.isNullOrEmpty()) {
        return listOf("stty") + args
    }
    return listOf("stty", sttyFileFlag(osName), device) + args
}

@KoverExclude
object DefaultControllingTty {
    fun path(): String? {
        val f = File("/dev/tty")
        return if (f.exists()) "/dev/tty" else null
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
    fun open(): RawMode {
        val probe = TtyProbe(controllingTty = DefaultControllingTty.path())
        val device = if (probe.stdinIsTty()) null else probe.controllingTty
        return SttyRawMode(ttyDevice = device)
    }
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
    private val ttyDevice: String? = null,
    private val osName: String = System.getProperty("os.name", ""),
) : RawMode {
    private var saved: String? = null
    private var hook: Thread? = null

    override fun enter() {
        if (saved != null) {
            return
        }
        val state = stty(listOf("-g"), capture = true)
        val token = state.stdout.trim()
        if (state.exitCode != 0 || token.isEmpty()) {
            throw RawModeException("stty -g failed")
        }
        val raw = stty(listOf("-echo", "-icanon", "min", "1", "time", "0"), capture = false)
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
        stty(listOf(token), capture = false)
    }

    private fun stty(args: List<String>, capture: Boolean): CommandResult {
        val inherit = ttyDevice == null
        return runner.run(sttyCommand(args, ttyDevice, osName), inherit, capture)
    }
}
