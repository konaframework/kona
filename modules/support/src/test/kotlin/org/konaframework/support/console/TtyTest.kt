package org.konaframework.support.console

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class TtyTest {
    @Test
    fun shouldUseWidgetsRespectsInjectedStreamsAndEnv() {
        val same = ByteArrayInputStream(byteArrayOf(1))
        val tty = TtyProbe(CommandRunner { _, _, _ -> CommandResult(0, "") })
        val notTty = TtyProbe(CommandRunner { _, _, _ -> CommandResult(1, "") })
        assertTrue(Tty.shouldUseWidgets(same, same, emptyMap(), tty))
        assertFalse(Tty.shouldUseWidgets(same, ByteArrayInputStream(byteArrayOf(2)), emptyMap(), tty))
        assertFalse(Tty.shouldUseWidgets(same, same, mapOf(Tty.LINE_PROMPTS_ENV to "1"), tty))
        assertFalse(Tty.shouldUseWidgets(same, same, mapOf(Tty.LINE_PROMPTS_ENV to "true"), tty))
        assertTrue(Tty.shouldUseWidgets(same, same, mapOf(Tty.LINE_PROMPTS_ENV to "0"), tty))
        assertTrue(Tty.shouldUseWidgets(same, same, mapOf(Tty.LINE_PROMPTS_ENV to "false"), tty))
        assertFalse(Tty.shouldUseWidgets(same, same, emptyMap(), notTty))
        assertFalse(Tty.shouldUseWidgets(ByteArrayInputStream("x".toByteArray())))

        val pipeNoDevice = TtyProbe(CommandRunner { _, _, _ -> CommandResult(1, "") }, controllingTty = null)
        assertFalse(Tty.shouldUseWidgets(same, same, emptyMap(), pipeNoDevice))
        val pipeWithTty =
            TtyProbe(
                CommandRunner { cmd, _, _ ->
                    if (cmd.contains("-g")) CommandResult(0, "saved-state\n") else CommandResult(1, "")
                },
                controllingTty = "/dev/tty",
            )
        assertTrue(Tty.shouldUseWidgets(same, same, emptyMap(), pipeWithTty))
        val pipeTtyBroken =
            TtyProbe(
                CommandRunner { cmd, _, _ ->
                    if (cmd.contains("-g")) CommandResult(0, "  ") else CommandResult(1, "")
                },
                controllingTty = "/dev/tty",
            )
        assertFalse(Tty.shouldUseWidgets(same, same, emptyMap(), pipeTtyBroken))
        val pipeTtyFail =
            TtyProbe(
                CommandRunner { _, _, _ -> CommandResult(1, "err") },
                controllingTty = "/dev/tty",
            )
        assertFalse(Tty.shouldUseWidgets(same, same, emptyMap(), pipeTtyFail))
        val macProbe =
            TtyProbe(
                CommandRunner { cmd, _, _ ->
                    if (cmd == listOf("stty", "-f", "/dev/tty", "-g")) CommandResult(0, "tok") else CommandResult(1, "")
                },
                controllingTty = "/dev/tty",
                osName = "Mac OS X",
            )
        assertTrue(macProbe.controllingTtyUsable())
    }

    @Test
    fun promptContentWidthClamps() {
        assertEquals(ConsoleIO.PROMPT_WIDTH, Tty.promptContentWidth(0))
        assertEquals(ConsoleIO.PROMPT_WIDTH, Tty.promptContentWidth(-4))
        assertEquals(36, Tty.promptContentWidth(20))
        assertEquals(60, Tty.promptContentWidth(64))
        assertEquals(76, Tty.promptContentWidth(80))
        assertEquals(80, Tty.promptContentWidth(200))
    }

    @Test
    fun probeStdinAndColumns() {
        val tty = TtyProbe(CommandRunner { cmd, _, _ ->
            when {
                cmd == listOf("test", "-t", "0") -> CommandResult(0, "")
                else -> CommandResult(1, "")
            }
        })
        assertTrue(tty.stdinIsTty())

        val viaStty = TtyProbe(CommandRunner { cmd, _, _ ->
            when {
                cmd == listOf("test", "-t", "0") -> CommandResult(127, "")
                cmd == listOf("stty", "size") -> CommandResult(0, "24 72\n")
                else -> CommandResult(1, "")
            }
        })
        assertTrue(viaStty.stdinIsTty())
        assertEquals(72, viaStty.columns(emptyMap()))

        val sttyNotTty = TtyProbe(CommandRunner { cmd, _, _ ->
            when {
                cmd == listOf("test", "-t", "0") -> CommandResult(127, "")
                else -> CommandResult(1, "")
            }
        })
        assertFalse(sttyNotTty.stdinIsTty())

        val notTty = TtyProbe(CommandRunner { _, _, _ -> CommandResult(1, "") })
        assertFalse(notTty.stdinIsTty())

        assertEquals(90, notTty.columns(mapOf("COLUMNS" to "90")))
        assertEquals(80, notTty.columns(mapOf("COLUMNS" to "0")))
        assertEquals(80, notTty.columns(mapOf("COLUMNS" to "nope")))
        assertEquals(80, TtyProbe(CommandRunner { _, _, _ -> CommandResult(0, "only") }).columns(emptyMap()))
        assertEquals(80, TtyProbe(CommandRunner { _, _, _ -> CommandResult(0, "24 -2") }).columns(emptyMap()))
        assertEquals(80, TtyProbe(CommandRunner { _, _, _ -> CommandResult(0, "24 xx") }).columns(emptyMap()))
        assertEquals(80, TtyProbe(CommandRunner { _, _, _ -> CommandResult(1, "24 80") }).columns(emptyMap()))

        val viaDevice =
            TtyProbe(
                CommandRunner { cmd, _, _ ->
                    when {
                        cmd == listOf("stty", "size") -> CommandResult(1, "")
                        cmd.contains("size") && cmd.contains("/dev/tty") -> CommandResult(0, "24 100\n")
                        else -> CommandResult(1, "")
                    }
                },
                controllingTty = "/dev/tty",
            )
        assertEquals(100, viaDevice.columns(emptyMap()))
        val deviceBad =
            TtyProbe(
                CommandRunner { cmd, _, _ ->
                    when {
                        cmd == listOf("stty", "size") -> CommandResult(1, "")
                        cmd.contains("size") -> CommandResult(0, "24 xx")
                        else -> CommandResult(1, "")
                    }
                },
                controllingTty = "/dev/tty",
            )
        assertEquals(80, deviceBad.columns(emptyMap()))
        assertEquals(
            80,
            TtyProbe(
                CommandRunner { _, _, _ -> CommandResult(1, "") },
                controllingTty = "/dev/tty",
            ).columns(emptyMap()),
        )
        val macCols =
            TtyProbe(
                CommandRunner { cmd, _, _ ->
                    when {
                        cmd == listOf("stty", "size") -> CommandResult(1, "")
                        cmd == listOf("stty", "-f", "/dev/tty", "size") -> CommandResult(0, "24 88")
                        else -> CommandResult(1, "")
                    }
                },
                controllingTty = "/dev/tty",
                osName = "Darwin",
            )
        assertEquals(88, macCols.columns(emptyMap()))
    }

    @Test
    fun sttyFileFlagAndCommand() {
        assertEquals("-f", sttyFileFlag("Mac OS X"))
        assertEquals("-f", sttyFileFlag("Darwin"))
        assertEquals("-F", sttyFileFlag("Linux"))
        assertEquals("-F", sttyFileFlag("Windows 11"))
        assertEquals(listOf("stty", "-g"), sttyCommand(listOf("-g"), null, "Linux"))
        assertEquals(listOf("stty", "-g"), sttyCommand(listOf("-g"), "", "Linux"))
        assertEquals(listOf("stty", "-F", "/dev/tty", "-g"), sttyCommand(listOf("-g"), "/dev/tty", "Linux"))
        assertEquals(listOf("stty", "-f", "/dev/tty", "size"), sttyCommand(listOf("size"), "/dev/tty", "Mac OS X"))
    }

    @Test
    fun processCommandRunnerRealCommands() {
        val runner = ProcessCommandRunner()
        assertEquals(0, runner.run(listOf("true"), inheritInput = false, captureOutput = false).exitCode)
        assertEquals(1, runner.run(listOf("false"), inheritInput = false, captureOutput = false).exitCode)
        val echoed = runner.run(listOf("echo", "kona-tty"), inheritInput = false, captureOutput = true)
        assertEquals(0, echoed.exitCode)
        assertTrue(echoed.stdout.contains("kona-tty"))
        val inherit = runner.run(listOf("true"), inheritInput = true, captureOutput = false)
        assertEquals(0, inherit.exitCode)
        val inheritCapture = runner.run(listOf("echo", "hi"), inheritInput = true, captureOutput = true)
        assertEquals(0, inheritCapture.exitCode)
        val missing = runner.run(listOf("kona-definitely-missing-binary-xyz"), inheritInput = false, captureOutput = true)
        assertEquals(127, missing.exitCode)
        assertEquals(127, runner.run(listOf("kona-definitely-missing-binary-xyz"), inheritInput = true, captureOutput = false).exitCode)
    }

    @Test
    fun sttyRawModeEnterRestoreAndFailures() {
        val log = mutableListOf<List<String>>()
        val runner = CommandRunner { cmd, _, _ ->
            log.add(cmd)
            when {
                cmd == listOf("stty", "-g") -> CommandResult(0, "saved-state\n")
                cmd[0] == "stty" && cmd.contains("-echo") -> CommandResult(0, "")
                cmd == listOf("stty", "saved-state") -> CommandResult(0, "")
                else -> CommandResult(1, "")
            }
        }
        val hooks = RecordingHooks()
        val mode = SttyRawMode(runner, hooks)
        mode.enter()
        mode.enter()
        assertEquals(1, hooks.added.size)
        mode.close()
        assertEquals(1, hooks.removed.size)
        mode.close()
        assertTrue(log.any { it == listOf("stty", "-g") })
        assertTrue(log.any { it.contains("-icanon") })
        assertTrue(log.any { it == listOf("stty", "saved-state") })

        val failG = SttyRawMode(CommandRunner { _, _, _ -> CommandResult(1, "") }, RecordingHooks())
        assertThrows(RawModeException::class.java) { failG.enter() }
        val emptyG = SttyRawMode(CommandRunner { _, _, _ -> CommandResult(0, "  ") }, RecordingHooks())
        assertThrows(RawModeException::class.java) { emptyG.enter() }
        val failRaw =
            SttyRawMode(
                CommandRunner { cmd, _, _ ->
                    if (cmd == listOf("stty", "-g")) CommandResult(0, "tok") else CommandResult(1, "")
                },
                RecordingHooks(),
            )
        assertThrows(RawModeException::class.java) { failRaw.enter() }

        NoopRawMode.enter()
        NoopRawMode.close()
        SttyRawMode(CommandRunner { _, _, _ -> CommandResult(0, "x") }, RecordingHooks()).close()

        val deviceLog = mutableListOf<List<String>>()
        val deviceRunner =
            CommandRunner { cmd, inherit, _ ->
                deviceLog.add(cmd)
                assertFalse(inherit)
                when {
                    cmd.contains("-g") -> CommandResult(0, "saved-state\n")
                    cmd.contains("-echo") -> CommandResult(0, "")
                    cmd.contains("saved-state") -> CommandResult(0, "")
                    else -> CommandResult(1, "")
                }
            }
        val deviceMode = SttyRawMode(deviceRunner, RecordingHooks(), ttyDevice = "/dev/tty", osName = "Linux")
        deviceMode.enter()
        deviceMode.close()
        assertTrue(deviceLog.any { it == listOf("stty", "-F", "/dev/tty", "-g") })
        assertTrue(deviceLog.any { it.contains("-icanon") && it.contains("/dev/tty") })
        assertTrue(deviceLog.any { it == listOf("stty", "-F", "/dev/tty", "saved-state") })

        val macLog = mutableListOf<List<String>>()
        val macMode =
            SttyRawMode(
                CommandRunner { cmd, _, _ ->
                    macLog.add(cmd)
                    if (cmd.contains("-g")) CommandResult(0, "tok") else CommandResult(0, "")
                },
                RecordingHooks(),
                ttyDevice = "/dev/tty",
                osName = "Mac OS X",
            )
        macMode.enter()
        macMode.close()
        assertTrue(macLog.any { it.contains("-f") && it.contains("/dev/tty") })
    }
}

private class RecordingHooks : ShutdownHooks {
    val added = mutableListOf<Thread>()
    val removed = mutableListOf<Thread>()

    override fun add(hook: Thread) {
        added.add(hook)
    }

    override fun remove(hook: Thread) {
        removed.add(hook)
    }
}
