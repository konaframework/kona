package org.konaframework.grind

import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

/** Default bind port for `grind serve` and generated `.env` files. */
const val DEFAULT_SERVE_PORT = 4000

/**
 * Port availability + interactive bump when the preferred port is taken.
 */
object ServePorts {
    /** True when [host] listens on all interfaces (`0.0.0.0` / IPv6 any). */
    fun isWildcardBind(host: String): Boolean =
        host == "0.0.0.0" || host == "::" || host == "[::]"

    /**
     * Browser-facing URL for [host]:[port]. Wildcard binds (`0.0.0.0`) map to `localhost`
     * (works with WSL localhost forwarding and native loopback).
     */
    fun localUrl(host: String, port: Int): String {
        val display = when {
            isWildcardBind(host) || host == "127.0.0.1" -> "localhost"
            else -> host
        }
        return "http://$display:$port"
    }

    /**
     * Best non-loopback IPv4 for a “Network” URL when binding `0.0.0.0`.
     * Skips Docker/bridge (`docker0`, `br-*`, `veth*`) so WSL does not advertise
     * `172.19.0.1`-style bridge gateways that hang in a Windows browser.
     */
    fun lanAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { iface ->
                    iface.isUp && !iface.isLoopback && !isBridgeOrContainerIface(iface.name)
                }
                .flatMap { iface ->
                    iface.inetAddresses.toList().asSequence()
                        .filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress }
                        .map { iface.name to it.hostAddress }
                }
                .sortedBy { (name, _) -> ifacePreference(name) }
                .map { it.second }
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /** Docker / CNI / libvirt bridges — not useful as a public “Network” URL. */
    internal fun isBridgeOrContainerIface(name: String): Boolean {
        val n = name.lowercase()
        return n == "docker0" ||
            n == "cni0" ||
            n == "podman0" ||
            n.startsWith("br-") ||
            n.startsWith("veth") ||
            n.startsWith("docker") ||
            n.startsWith("br0") ||
            n.startsWith("virbr") ||
            n.startsWith("lxc") ||
            n.startsWith("flannel") ||
            n.startsWith("cni-")
    }

    /** Lower score = preferred (`eth0` / `en*` before random NICs). */
    internal fun ifacePreference(name: String): Int {
        val n = name.lowercase()
        return when {
            n == "eth0" || n.startsWith("en") -> 0
            n.startsWith("wl") || n.startsWith("wlan") -> 1
            else -> 10
        }
    }

    /** True when running under Windows Subsystem for Linux. */
    fun isWsl(env: Map<String, String> = System.getenv()): Boolean {
        if (env.containsKey("WSL_DISTRO_NAME") || env.containsKey("WSL_INTEROP")) {
            return true
        }
        return try {
            Files.exists(Path.of("/proc/sys/fs/binfmt_misc/WSLInterop")) ||
                Files.readString(Path.of("/proc/version")).contains("microsoft", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

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
