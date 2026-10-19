package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

class ServePortsTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun preferredHostPortFromOptionsEnvAndDefaults() {
        val root = temp.resolve("app")
        Files.createDirectories(root)
        Files.writeString(
            root.resolve(".env"),
            """
            # comment
            export HOST=10.0.0.1
            PORT=4111 # trailing
            EMPTY=
            NOEQ
            QUOTED="quoted-host"
            APP=x
            """.trimIndent() + "\n",
        )
        assertEquals("0.0.0.0" to 9000, ServePorts.preferredHostPort(root, mapOf("host" to "0.0.0.0", "port" to "9000")))
        assertEquals("10.0.0.1" to 4111, ServePorts.preferredHostPort(root, emptyMap(), env = emptyMap()))
        assertEquals(
            "env-host" to 4222,
            ServePorts.preferredHostPort(root, emptyMap(), env = mapOf("HOST" to "env-host", "PORT" to "4222")),
        )
        assertEquals(
            "127.0.0.1" to DEFAULT_SERVE_PORT,
            ServePorts.preferredHostPort(temp.resolve("missing"), emptyMap(), env = emptyMap()),
        )
        assertThrows(GrindException::class.java) {
            ServePorts.preferredHostPort(root, mapOf("port" to "nope"))
        }
        Files.writeString(root.resolve(".env"), "\n# only comment\nPORT=1\n")
        assertEquals("127.0.0.1" to 1, ServePorts.preferredHostPort(root, emptyMap(), env = emptyMap()))
        assertEquals(emptyMap<String, String>(), ServePorts.readDotEnv(temp.resolve("nope.env")))
        Files.writeString(root.resolve(".env"), "QUOTED='quoted-host'\n")
        assertEquals("quoted-host", ServePorts.readDotEnv(root.resolve(".env"))["QUOTED"])
        // default env map overload
        ServePorts.preferredHostPort(temp.resolve("missing"), emptyMap())
    }

    @Test
    fun resolveUsesPreferredWhenFreeAndBumpsWhenOccupied() {
        val out = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), color = false)
        val free = freePort()
        assertTrue(ServePorts.isAvailable("127.0.0.1", free))
        assertEquals(
            free,
            ServePorts.resolve(
                "127.0.0.1",
                free,
                Prompts(ByteArrayInputStream(ByteArray(0)), PrintStream(out), color = false),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ServePorts.resolve(
                "127.0.0.1",
                70000,
                Prompts(ByteArrayInputStream(ByteArray(0)), PrintStream(out), color = false),
                io,
            )
        }

        occupyPortWithFreeSuccessor().use { occupied ->
            val port = occupied.localPort
            assertFalse(ServePorts.isAvailable("127.0.0.1", port))
            val next = port + 1
            val resolved =
                ServePorts.resolve(
                    "127.0.0.1",
                    port,
                    Prompts(ByteArrayInputStream("y\n".toByteArray()), PrintStream(out), color = false),
                    io,
                )
            assertEquals(next, resolved)
            assertTrue(out.toString().contains("Using port $next"))
        }

        ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")).use { occupied ->
            assertThrows(GrindException::class.java) {
                ServePorts.resolve(
                    "127.0.0.1",
                    occupied.localPort,
                    Prompts(ByteArrayInputStream("n\n".toByteArray()), PrintStream(out), color = false),
                    io,
                )
            }
        }
    }

    @Test
    fun resolveRejectsOutOfRangeAndExhaustsAttempts() {
        val out = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), color = false)
        assertThrows(IllegalArgumentException::class.java) {
            ServePorts.resolve(
                "127.0.0.1",
                0,
                Prompts(ByteArrayInputStream("y\n".toByteArray()), PrintStream(out), color = false),
                io,
            )
        }
        ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")).use { occupied ->
            assertThrows(GrindException::class.java) {
                ServePorts.resolve(
                    "127.0.0.1",
                    occupied.localPort,
                    Prompts(ByteArrayInputStream("y\n".toByteArray()), PrintStream(out), color = false),
                    io,
                    maxAttempts = 0,
                )
            }
        }
    }

    @Test
    fun isAvailableHandlesWildcardAndBadHost() {
        val free = freePort()
        assertTrue(ServePorts.isAvailable("0.0.0.0", free))
        assertTrue(ServePorts.isAvailable("::", freePort()))
        assertTrue(ServePorts.isAvailable("[::]", freePort()))
        assertFalse(ServePorts.isAvailable("this.host.definitely.invalid.kona", 4000))
        assertFalse(ServePorts.isAvailable("127.0.0.1", -1))
    }

    @Test
    fun localUrlMapsWildcardBindToLoopback() {
        assertTrue(ServePorts.isWildcardBind("0.0.0.0"))
        assertTrue(ServePorts.isWildcardBind("::"))
        assertTrue(ServePorts.isWildcardBind("[::]"))
        assertFalse(ServePorts.isWildcardBind("127.0.0.1"))
        assertEquals("http://localhost:4000", ServePorts.localUrl("127.0.0.1", 4000))
        assertEquals("http://localhost:9000", ServePorts.localUrl("0.0.0.0", 9000))
        assertEquals("http://localhost:4000", ServePorts.localUrl("::", 4000))
        assertEquals("http://10.0.0.2:8080", ServePorts.localUrl("10.0.0.2", 8080))
        assertTrue(ServePorts.isBridgeOrContainerIface("docker0"))
        assertTrue(ServePorts.isBridgeOrContainerIface("cni0"))
        assertTrue(ServePorts.isBridgeOrContainerIface("podman0"))
        assertTrue(ServePorts.isBridgeOrContainerIface("br-0c10a6f068bd"))
        assertTrue(ServePorts.isBridgeOrContainerIface("vethabc123"))
        assertTrue(ServePorts.isBridgeOrContainerIface("docker_gwbridge"))
        assertTrue(ServePorts.isBridgeOrContainerIface("br0"))
        assertTrue(ServePorts.isBridgeOrContainerIface("virbr0"))
        assertTrue(ServePorts.isBridgeOrContainerIface("lxcbr0"))
        assertTrue(ServePorts.isBridgeOrContainerIface("flannel.1"))
        assertTrue(ServePorts.isBridgeOrContainerIface("cni-podman0"))
        assertFalse(ServePorts.isBridgeOrContainerIface("eth0"))
        assertEquals(0, ServePorts.ifacePreference("eth0"))
        assertEquals(0, ServePorts.ifacePreference("enp0s3"))
        assertEquals(1, ServePorts.ifacePreference("wlan0"))
        assertEquals(1, ServePorts.ifacePreference("wlp2s0"))
        assertEquals(10, ServePorts.ifacePreference("tun0"))
        // Prefer eth0 over Docker bridges when present on this host
        val lan = ServePorts.lanAddress()
        if (lan != null) {
            assertFalse(lan == "172.19.0.1", "must not advertise Docker bridge gateway")
            assertFalse(lan == "172.18.0.1", "must not advertise docker0")
        }
        assertTrue(ServePorts.isWsl(mapOf("WSL_DISTRO_NAME" to "Ubuntu")))
        assertTrue(ServePorts.isWsl(mapOf("WSL_INTEROP" to "/run/WSL/1_interop")))
        ServePorts.isWsl(emptyMap())
    }

    @Test
    fun resolveAtMaxPortWhenOccupiedThrows() {
        val out = ByteArrayOutputStream()
        val socket =
            try {
                ServerSocket(65535, 50, java.net.InetAddress.getByName("127.0.0.1"))
            } catch (_: Exception) {
                return
            }
        socket.use {
            assertThrows(GrindException::class.java) {
                ServePorts.resolve(
                    "127.0.0.1",
                    65535,
                    Prompts(ByteArrayInputStream("y\n".toByteArray()), PrintStream(out), color = false),
                    GrindIO(PrintStream(out), color = false),
                )
            }
        }
    }
}
