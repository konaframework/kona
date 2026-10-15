package dev.kona.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GrindCommandTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun helpDoesNotSpawn() {
        var spawned = false
        val cmd =
            GrindCommand(
                runner = GrindProcessRunner { _, _ ->
                    spawned = true
                    0
                },
                startDir = temp,
            )
        assertEquals(0, cmd.run(emptyList()))
        assertEquals(0, cmd.run(listOf("--help")))
        assertEquals(0, cmd.run(listOf("-h")))
        assertEquals(0, cmd.run(listOf("help")))
        assertEquals(0, KonaApplication().run(arrayOf("grind")))
        assertEquals(0, KonaApplication().run(arrayOf("grind", "--help")))
        assertEquals(0, KonaApplication().run(arrayOf("grind", "-h")))
        assertFalse(spawned)
    }

    @Test
    fun findProjectRootWalksUp() {
        val empty = temp.resolve("empty").also { Files.createDirectories(it) }
        assertNull(GrindCommand.findProjectRoot(empty))

        val root = temp.resolve("root").also { Files.createDirectories(it) }
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"app\"\n")
        val nested = root.resolve("src/main").also { Files.createDirectories(it) }
        assertEquals(root, GrindCommand.findProjectRoot(nested))

        listOf("settings.gradle", "build.gradle.kts", "build.gradle").forEach { marker ->
            val dir = temp.resolve("m-$marker").also { Files.createDirectories(it) }
            Files.writeString(dir.resolve(marker), "x\n")
            assertEquals(dir, GrindCommand.findProjectRoot(dir))
        }
    }

    @Test
    fun missingAppThrows() {
        val empty = temp.resolve("nowhere").also { Files.createDirectories(it) }
        val ex =
            assertThrows(KonaCliException::class.java) {
                GrindCommand(startDir = empty).run(listOf("serve"))
            }
        assertTrue(ex.message!!.contains("No Kona application found"))
        assertTrue(ex.message!!.contains("kona new"))
    }

    @Test
    fun gradleWithoutGrindThrows() {
        val root = temp.resolve("bare").also { Files.createDirectories(it) }
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"bare\"\n")
        val ex =
            assertThrows(KonaCliException::class.java) {
                GrindCommand(startDir = root).run(listOf("serve"))
            }
        assertTrue(ex.message!!.contains("no grind"))
        assertTrue(ex.message!!.contains("kona new"))

        val win =
            assertThrows(KonaCliException::class.java) {
                GrindCommand(windows = true, startDir = root).run(listOf("serve"))
            }
        assertTrue(win.message!!.contains("no grind.bat"))
    }

    @Test
    fun proxiesArgsAndExitCode() {
        val root = temp.resolve("app").also { Files.createDirectories(it) }
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"app\"\n")
        Files.writeString(root.resolve("grind"), "#!/bin/sh\n")
        root.resolve("grind").toFile().setExecutable(true)

        var seenCommand: List<String>? = null
        var seenDir: Path? = null
        val cmd =
            GrindCommand(
                runner = GrindProcessRunner { command, workingDir ->
                    seenCommand = command
                    seenDir = workingDir
                    7
                },
                startDir = root.resolve("nested").also { Files.createDirectories(it) },
            )
        assertEquals(7, cmd.run(listOf("migrate", "--step=1")))
        assertEquals(listOf(root.resolve("grind").toString(), "migrate", "--step=1"), seenCommand)
        assertEquals(root, seenDir)
    }

    @Test
    fun windowsUsesGrindBat() {
        val root = temp.resolve("winapp").also { Files.createDirectories(it) }
        Files.writeString(root.resolve("build.gradle.kts"), "plugins {}\n")
        Files.writeString(root.resolve("grind.bat"), "@echo off\n")

        var seenCommand: List<String>? = null
        val cmd =
            GrindCommand(
                runner = GrindProcessRunner { command, _ ->
                    seenCommand = command
                    0
                },
                windows = true,
                startDir = root,
            )
        assertEquals(0, cmd.run(listOf("serve")))
        assertEquals(listOf(root.resolve("grind.bat").toString(), "serve"), seenCommand)
    }

    @Test
    fun systemRunnerPropagatesExitCode() {
        assertEquals(
            3,
            GrindProcessRunner.SYSTEM.run(listOf("sh", "-c", "exit 3"), temp),
        )
    }

    @Test
    fun systemRunnerStartFailure() {
        val ex =
            assertThrows(KonaCliException::class.java) {
                GrindProcessRunner.SYSTEM.run(listOf("kona-definitely-missing-binary"), temp)
            }
        assertTrue(ex.message!!.contains("Failed to start"))
    }

    @Test
    fun jvmOverloadsConstructors() {
        // Hit generated @JvmOverloads ctors (3-arg and fewer) for kover.
        assertEquals(0, GrindCommand(GrindProcessRunner.SYSTEM, false, temp).run(listOf("--help")))
        assertEquals(0, GrindCommand(GrindProcessRunner.SYSTEM, false).run(listOf("-h")))
        assertEquals(0, GrindCommand(GrindProcessRunner.SYSTEM).run(emptyList()))
    }
}
