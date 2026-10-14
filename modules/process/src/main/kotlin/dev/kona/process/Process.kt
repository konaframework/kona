package dev.kona.process

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * External process runner.
 */
class ProcessRunner(
    private val defaultTimeoutSeconds: Long = 60,
) {
    fun run(command: List<String>): ProcessResult =
        run(command, null, emptyMap(), defaultTimeoutSeconds)

    fun run(command: List<String>, workingDirectory: File?): ProcessResult =
        run(command, workingDirectory, emptyMap(), defaultTimeoutSeconds)

    fun run(command: List<String>, workingDirectory: File?, env: Map<String, String>): ProcessResult =
        run(command, workingDirectory, env, defaultTimeoutSeconds)

    fun run(
        command: List<String>,
        workingDirectory: File?,
        env: Map<String, String>,
        timeoutSeconds: Long,
    ): ProcessResult {
        val builder = ProcessBuilder(command)
        if (workingDirectory != null) {
            builder.directory(workingDirectory)
        }
        if (env.isNotEmpty()) {
            builder.environment().putAll(env)
        }
        builder.redirectErrorStream(false)
        val proc = builder.start()
        val stdoutBuffer = ByteArray(8192)
        val stderrBuffer = ByteArray(8192)
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread {
            while (true) {
                val n = proc.inputStream.read(stdoutBuffer)
                if (n < 0) break
                stdout.append(String(stdoutBuffer, 0, n))
            }
        }
        val errThread = Thread {
            while (true) {
                val n = proc.errorStream.read(stderrBuffer)
                if (n < 0) break
                stderr.append(String(stderrBuffer, 0, n))
            }
        }
        outThread.start()
        errThread.start()
        val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            outThread.join(1_000)
            errThread.join(1_000)
            throw IllegalStateException("Process timed out after ${timeoutSeconds}s: $command")
        }
        outThread.join()
        errThread.join()
        return ProcessResult(proc.exitValue(), stdout.toString(), stderr.toString())
    }

    fun run(vararg command: String): ProcessResult = run(command.toList())
}

object Process {
    private val runner = ProcessRunner()

    @JvmStatic
    fun run(vararg command: String): ProcessResult = runner.run(command.toList())

    @JvmStatic
    fun runner(): ProcessRunner = runner
}
