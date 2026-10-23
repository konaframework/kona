package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class GradleAliasCommandTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun tasksForNamedAndGenericAliases() {
        assertEquals(listOf("build"), GradleAliasCommand.tasksFor("gradle:build"))
        assertEquals(listOf("installDist"), GradleAliasCommand.tasksFor("gradle:install"))
        assertEquals(listOf("installDist"), GradleAliasCommand.tasksFor("gradle:installDist"))
        assertEquals(listOf("test"), GradleAliasCommand.tasksFor("gradle:test"))
        assertEquals(listOf("check"), GradleAliasCommand.tasksFor("gradle:check"))
        assertEquals(listOf("classes"), GradleAliasCommand.tasksFor("gradle:classes"))
        assertEquals(listOf("clean"), GradleAliasCommand.tasksFor("gradle:clean"))
        assertEquals(listOf("run"), GradleAliasCommand.tasksFor("gradle:run"))
        assertEquals(emptyList<String>(), GradleAliasCommand.tasksFor("gradle"))
        assertEquals(emptyList<String>(), GradleAliasCommand.tasksFor("gradle:"))
        assertEquals(listOf("assemble"), GradleAliasCommand.tasksFor("gradle:assemble"))
        assertEquals(listOf("foo:bar"), GradleAliasCommand.tasksFor("gradle:foo:bar"))
        assertEquals(emptyList<String>(), GradleAliasCommand.tasksFor("serve"))
        assertEquals(emptyList<String>(), GradleAliasCommand.tasksFor("gradlew"))
        assertTrue(GradleAliasCommand.isGradleToken("gradle"))
        assertTrue(GradleAliasCommand.isGradleToken("gradle:build"))
        assertTrue(GradleAliasCommand.isGradleToken("gradle:"))
        assertFalse(GradleAliasCommand.isGradleToken("serve"))
        assertTrue(GradleAliasCommand.ALIASES.keys.containsAll(listOf("gradle:build", "gradle:install", "gradle")))
        assertTrue(GradleAliasCommand.LIST_ENTRIES.any { it.first == "gradle:build" })
        assertTrue(ListCommand.BUILTIN.any { it.first == "gradle:build" })
        assertTrue(ListCommand.BUILTIN.any { it.first == "gradle:install" })
        assertTrue(ListCommand.BUILTIN.any { it.first == "gradle" })
    }

    @Test
    fun helpDoesNotNeedAProject() {
        val out = capture {
            assertEquals(0, GradleAliasCommand.fromToken("gradle:build").run(listOf("--help")))
        }
        assertTrue(out.contains("grind gradle:build"))
        assertTrue(out.contains("installDist"))
        assertTrue(out.contains("./gradlew build"))
        val h = capture {
            assertEquals(0, GradleAliasCommand.fromToken("gradle").run(listOf("--h")))
        }
        assertTrue(h.contains("grind gradle"))
    }

    @Test
    fun missingProjectThrows() {
        val orphan = temp.resolve("orphan")
        Files.createDirectories(orphan)
        val ex = assertThrows(GrindException::class.java) {
            GradleAliasCommand.fromToken("gradle:build").run(listOf("--base=$orphan"))
        }
        assertTrue(ex.message!!.contains("No Kona application found"))
    }

    @Test
    fun gradlePassthroughRequiresTasks() {
        val root = project("empty")
        val out = capture {
            assertEquals(
                1,
                GradleAliasCommand.fromToken("gradle", root, neverRun(), false)
                    .run(listOf("--base=$root")),
            )
        }
        assertTrue(out.contains("Usage: ./grind gradle"))
        val colon = capture {
            assertEquals(
                1,
                GradleAliasCommand.fromToken("gradle:", root, neverRun(), false)
                    .run(emptyList()),
            )
        }
        assertTrue(colon.contains("Usage: ./grind gradle"))
    }

    @Test
    fun buildAliasRunsWrapper() {
        val root = project("app")
        var seen: List<String>? = null
        var dir: Path? = null
        val runner = ProcessRunner { command, workingDir, env ->
            seen = command
            dir = workingDir
            assertTrue(env.isEmpty())
            0
        }
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle:build", root, runner, false)
                .run(listOf("--base=$root")),
        )
        assertEquals(root.resolve("gradlew").toString(), seen!!.first())
        assertEquals(listOf("build"), seen!!.drop(1))
        assertFalse(seen!!.contains("--quiet"))
        assertEquals(root, dir)
    }

    @Test
    fun installMapsToInstallDist() {
        val root = project("inst")
        var seen: List<String>? = null
        val runner = ProcessRunner { command, _, _ ->
            seen = command
            0
        }
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle:install", root, runner, false)
                .run(listOf("--base=$root")),
        )
        assertEquals(listOf("installDist"), seen!!.drop(1))
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle:installDist", root, runner, false)
                .run(listOf("--base=$root")),
        )
        assertEquals(listOf("installDist"), seen!!.drop(1))
    }

    @Test
    fun genericTaskAndPassthroughForwardFlags() {
        val root = project("flags")
        var seen: List<String>? = null
        val runner = ProcessRunner { command, _, _ ->
            seen = command
            0
        }
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle:assemble", root, runner, false).run(
                listOf("--base=$root", "--offline", "--max-workers=2", "extra"),
            ),
        )
        assertEquals(
            listOf(root.resolve("gradlew").toString(), "assemble", "extra", "--offline", "--max-workers=2"),
            seen,
        )
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle", root, runner, false).run(
                listOf("test", "--base=$root", "--offline"),
            ),
        )
        assertEquals(listOf(root.resolve("gradlew").toString(), "test", "--offline"), seen)
    }

    @Test
    fun grindOwnedFlagsAreNotForwarded() {
        val root = project("owned")
        var seen: List<String>? = null
        val runner = ProcessRunner { command, _, _ ->
            seen = command
            0
        }
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle:classes", root, runner, false).run(
                listOf("--base=$root", "--verbose", "--quiet"),
            ),
        )
        assertEquals(listOf(root.resolve("gradlew").toString(), "classes"), seen)
    }

    @Test
    fun windowsWrapperAndMissingWrapper() {
        val win = project("win")
        Files.writeString(win.resolve("gradlew.bat"), "@echo off\n")
        var seen: List<String>? = null
        val runner = ProcessRunner { command, _, _ ->
            seen = command
            0
        }
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle:clean", win, runner, true)
                .run(listOf("--base=$win")),
        )
        assertEquals(win.resolve("gradlew.bat").toString(), seen!!.first())

        val nowrap = project("nowrap")
        Files.deleteIfExists(nowrap.resolve("gradlew"))
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle:run", nowrap, runner, false)
                .run(listOf("--base=$nowrap")),
        )
        assertEquals("gradle", seen!!.first())
        assertEquals(listOf("run"), seen!!.drop(1))
    }

    @Test
    fun nonZeroExitIsPropagated() {
        val root = project("fail")
        val runner = ProcessRunner { _, _, _ -> 7 }
        assertEquals(
            7,
            GradleAliasCommand.fromToken("gradle:test", root, runner, false)
                .run(listOf("--base=$root")),
        )
    }

    @Test
    fun walksUpToProjectRootWithoutBase() {
        val root = project("walk")
        val nested = root.resolve("src/main/kotlin")
        Files.createDirectories(nested)
        var dir: Path? = null
        val runner = ProcessRunner { _, workingDir, _ ->
            dir = workingDir
            0
        }
        assertEquals(
            0,
            GradleAliasCommand.fromToken("gradle:build", nested, runner, false).run(emptyList()),
        )
        assertEquals(root, dir)
    }

    @Test
    fun grindApplicationDispatchesGradleTokens() {
        val orphan = temp.resolve("no-app")
        Files.createDirectories(orphan)
        assertEquals(0, GrindApplication().run(arrayOf("gradle:build", "--help")))
        assertEquals(0, GrindApplication().run(arrayOf("gradle", "--help")))
        assertEquals(0, GrindApplication().run(arrayOf("gradle:assemble", "--help")))
        assertEquals(1, GrindApplication().run(arrayOf("gradle:build", "--base=$orphan")))
        val listed = capture {
            assertEquals(0, GrindApplication().run(arrayOf("list")))
        }
        assertTrue(listed.contains("gradle:build"))
        assertTrue(listed.contains("gradle:install"))
    }

    @Test
    fun fromTokenOverloads() {
        val root = project("over")
        val runner = ProcessRunner { _, _, _ -> 0 }
        assertEquals(0, GradleAliasCommand.fromToken("gradle:build", root).run(listOf("--help")))
        assertEquals(0, GradleAliasCommand.fromToken("gradle:check", root, runner).run(listOf("--base=$root")))
        assertEquals(
            0,
            GradleAliasCommand("gradle:build", listOf("build"), root, runner, false)
                .run(listOf("--base=$root")),
        )
    }

    private fun project(name: String): Path {
        val root = temp.resolve(name)
        Files.createDirectories(root)
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"$name\"\n")
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\n")
        return root
    }

    private fun neverRun(): ProcessRunner = ProcessRunner { _, _, _ ->
        throw AssertionError("Gradle should not run")
    }

    private fun capture(block: () -> Unit): String {
        val original = System.out
        val errOriginal = System.err
        val buffer = ByteArrayOutputStream()
        val stream = PrintStream(buffer, true)
        System.setOut(stream)
        System.setErr(stream)
        try {
            block()
            return buffer.toString()
        } finally {
            System.setOut(original)
            System.setErr(errOriginal)
        }
    }
}
