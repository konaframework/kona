package org.konaframework.http

import org.konaframework.core.Log
import org.konaframework.support.console.ConsoleIO
import java.util.concurrent.TimeUnit

/**
 * Development-friendly HTTP access log (method, path, status, duration).
 *
 * Writes Termwind-styled lines via [ConsoleIO] so `./grind serve` / `./gradlew run`
 * show request activity on the terminal. Optionally mirrors a plain line through [Log.info]
 * for the configured log stack (usually `storage/logs`).
 *
 * Registered automatically by [HttpServiceProvider] when [RequestLogging.enabled] is true.
 */
class LogRequests @JvmOverloads constructor(
    private val console: ConsoleIO = ConsoleIO(),
    private val alsoLog: Boolean = true) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val started = System.nanoTime()
        val response = next.handle(request)
        val millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        val target = formatTarget(request)
        val plain = "${request.method.uppercase()} $target  ${response.status}  ${millis}ms"
        writeConsole(request.method.uppercase(), target, response.status, millis)
        if (alsoLog) {
            Log.info(plain)
        }
        return response
    }

    private fun writeConsole(method: String, target: String, status: Int, millis: Long) {
        val methodPaint = console.paint(ConsoleIO.PRIMARY + ConsoleIO.BOLD, method.padEnd(7))
        val pathPaint = console.paint(ConsoleIO.DIM, target)
        val statusPaint = console.paint(statusColor(status) + ConsoleIO.BOLD, status.toString())
        val durationPaint = console.paint(ConsoleIO.DIM, "${millis}ms")
        console.writeln("$methodPaint $pathPaint  $statusPaint  $durationPaint")
    }

    companion object {
        @JvmStatic
        fun formatTarget(request: Request): String {
            if (request.query.isEmpty()) {
                return request.path
            }
            val qs =
                request.query.entries.joinToString("&") { (key, value) ->
                    if (value.isEmpty()) key else "$key=$value"
                }
            return "${request.path}?$qs"
        }

        @JvmStatic
        fun statusColor(status: Int): String =
            when {
                status in 200..299 -> ConsoleIO.GREEN
                status in 300..399 -> ConsoleIO.CYAN
                status in 400..499 -> ConsoleIO.YELLOW
                else -> ConsoleIO.RED
            }
    }
}
