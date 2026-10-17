package org.konaframework.grind

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

/** Default bind port for `grind serve` and generated `.env` files. */
const val DEFAULT_SERVE_PORT = 4000

/**
 * Port availability + interactive bump when the preferred port is taken.
 */
object ServePorts {
    fun isAvailable(host: String, port: Int): Boolean =
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(resolveBindHost(host), port))
                true
            }
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    /**
     * Returns [preferred] when free; otherwise asks via [prompts] whether to try
     * [preferred]+1, [preferred]+2, … until accepted or the user declines.
     */
    fun resolve(
        host: String,
        preferred: Int,
        prompts: Prompts,
        io: GrindIO = GrindIO(),
        maxAttempts: Int = 100,
    ): Int {
        require(preferred in 1..65535) { "port out of range: $preferred" }
        var candidate = preferred
        var attempts = 0
        while (attempts < maxAttempts) {
            if (isAvailable(host, candidate)) {
                if (candidate != preferred) {
                    io.info("Using port $candidate")
                }
                return candidate
            }
            val next = candidate + 1
            if (next > 65535) {
                throw GrindException("No free port found at or above $preferred")
            }
            if (!prompts.confirm("Port $candidate is in use. Use $next instead?", default = true)) {
                throw GrindException("Port $candidate is already in use")
            }
            candidate = next
            attempts++
        }
        throw GrindException("No free port found at or above $preferred")
    }

    /** HOST / PORT from CLI options, then process env / `.env`, then defaults. */
    fun preferredHostPort(
        root: Path,
        options: Map<String, String>,
        env: Map<String, String> = System.getenv(),
    ): Pair<String, Int> {
        val file = readDotEnv(root.resolve(".env"))
        val host = options["host"]
            ?: env["HOST"]
            ?: file["HOST"]
            ?: "127.0.0.1"
        val portRaw = options["port"]
            ?: env["PORT"]
            ?: file["PORT"]
            ?: DEFAULT_SERVE_PORT.toString()
        val port = portRaw.toIntOrNull()
            ?: throw GrindException("Invalid PORT value: $portRaw")
        return host to port
    }

    internal fun readDotEnv(path: Path): Map<String, String> {
        if (!Files.isRegularFile(path)) {
            return emptyMap()
        }
        val out = linkedMapOf<String, String>()
        Files.readAllLines(path).forEach { raw ->
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEach
            }
            if (line.startsWith("export ")) {
                line = line.removePrefix("export ").trim()
            }
            val idx = line.indexOf('=')
            if (idx <= 0) {
                return@forEach
            }
            val key = line.substring(0, idx).trim()
            var value = line.substring(idx + 1).trim()
            val quoted = value.startsWith("\"") || value.startsWith("'")
            if (!quoted) {
                val hash = value.indexOf(" #")
                if (hash >= 0) {
                    value = value.substring(0, hash).trim()
                }
            }
            value = value.removeSurrounding("\"").removeSurrounding("'")
            out[key] = value
        }
        return out
    }

    private fun resolveBindHost(host: String): java.net.InetAddress =
        when (host) {
            "0.0.0.0", "::", "[::]" -> java.net.InetAddress.getByName("0.0.0.0")
            else -> java.net.InetAddress.getByName(host)
        }
}
