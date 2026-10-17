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
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class KeyGenerateCommandTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun generateAppKeyFormat() {
        val key = KeyGenerateCommand.generateAppKey()
        assertTrue(key.startsWith("base64:"))
        val decoded = Base64.getDecoder().decode(key.removePrefix("base64:"))
        assertEquals(32, decoded.size)
    }

    @Test
    fun showPrintsWithoutWriting() {
        val base = project("show-proj")
        Files.writeString(base.resolve(".env"), "APP_KEY=\n")
        val out = ByteArrayOutputStream()
        val code = capture(out) {
            KeyGenerateCommand(workingDirectory = base).run(listOf("--show", "--base=$base"))
        }
        assertEquals(0, code)
        val printed = out.toString().trim()
        assertTrue(printed.startsWith("base64:"))
        assertEquals("APP_KEY=\n", Files.readString(base.resolve(".env")))
    }

    @Test
    fun writesEmptyKey() {
        val base = project("empty-key")
        Files.writeString(base.resolve(".env"), "APP_NAME=Demo\nAPP_KEY=\nHOST=127.0.0.1\n")
        assertEquals(0, KeyGenerateCommand(workingDirectory = base).run(listOf("--base=$base")))
        val env = Files.readString(base.resolve(".env"))
        assertTrue(Regex("""(?m)^APP_KEY=base64:.+$""").containsMatchIn(env))
        assertTrue(env.contains("APP_NAME=Demo"))
        assertTrue(env.contains("HOST=127.0.0.1"))
    }

    @Test
    fun createsEnvFromExample() {
        val base = project("from-example")
        Files.writeString(base.resolve(".env.example"), "APP_ENV=local\nAPP_KEY=\n")
        assertEquals(0, KeyGenerateCommand(workingDirectory = base).run(listOf("--base=$base")))
        assertTrue(Files.isRegularFile(base.resolve(".env")))
        assertTrue(Files.readString(base.resolve(".env")).contains("APP_KEY=base64:"))
    }

    @Test
    fun overwritesExistingKeyOutsideProduction() {
        val base = project("local-overwrite")
        Files.writeString(base.resolve(".env"), "APP_ENV=local\nAPP_KEY=base64:oldkeyoldkeyoldkeyoldkeyoldkeyold=\n")
        assertEquals(0, KeyGenerateCommand(workingDirectory = base).run(listOf("--base=$base")))
        val key = ServePorts.readDotEnv(base.resolve(".env"))["APP_KEY"]!!
        assertTrue(key.startsWith("base64:"))
        assertFalse(key.contains("oldkey"))
    }

    @Test
    fun productionWithoutForceCancels() {
        val base = project("prod-cancel")
        Files.writeString(base.resolve(".env"), "APP_ENV=production\nAPP_KEY=base64:existingexistingexistingexistin=\n")
        val prompts = Prompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(ByteArrayOutputStream()))
        val code = KeyGenerateCommand(
            workingDirectory = base,
            prompts = prompts,
            environment = emptyMap(),
        ).run(listOf("--base=$base"))
        assertEquals(1, code)
        assertTrue(Files.readString(base.resolve(".env")).contains("base64:existingexistingexistingexistin="))
    }

    @Test
    fun productionWithForceOverwrites() {
        val base = project("prod-force")
        Files.writeString(base.resolve(".env"), "APP_ENV=production\nAPP_KEY=base64:existingexistingexistingexistin=\n")
        assertEquals(
            0,
            KeyGenerateCommand(workingDirectory = base, environment = emptyMap())
                .run(listOf("--base=$base", "--force")),
        )
        val key = ServePorts.readDotEnv(base.resolve(".env"))["APP_KEY"]!!
        assertTrue(key.startsWith("base64:"))
        assertFalse(key.contains("existing"))
    }

    @Test
    fun productionConfirmYesOverwrites() {
        val base = project("prod-yes")
        Files.writeString(base.resolve(".env"), "APP_ENV=production\nAPP_KEY=base64:existingexistingexistingexistin=\n")
        val prompts = Prompts(ByteArrayInputStream("y\n".toByteArray()), PrintStream(ByteArrayOutputStream()))
        assertEquals(
            0,
            KeyGenerateCommand(workingDirectory = base, prompts = prompts, environment = emptyMap())
                .run(listOf("--base=$base")),
        )
        assertFalse(Files.readString(base.resolve(".env")).contains("existing"))
    }

    @Test
    fun processEnvProductionWins() {
        val base = project("env-prod")
        Files.writeString(base.resolve(".env"), "APP_ENV=local\nAPP_KEY=base64:existingexistingexistingexistin=\n")
        val prompts = Prompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(ByteArrayOutputStream()))
        val code = KeyGenerateCommand(
            workingDirectory = base,
            prompts = prompts,
            environment = mapOf("APP_ENV" to "production"),
        ).run(listOf("--base=$base"))
        assertEquals(1, code)
    }

    @Test
    fun missingEnvFails() {
        val base = project("no-env")
        assertThrows(GrindException::class.java) {
            KeyGenerateCommand(workingDirectory = base).run(listOf("--base=$base"))
        }
    }

    @Test
    fun missingAppKeyLineFails() {
        val base = project("no-line")
        Files.writeString(base.resolve(".env"), "APP_NAME=Demo\n")
        val err = ByteArrayOutputStream()
        val previous = System.err
        val code = try {
            System.setErr(PrintStream(err))
            KeyGenerateCommand(workingDirectory = base).run(listOf("--base=$base"))
        } finally {
            System.setErr(previous)
        }
        assertEquals(1, code)
        assertTrue(err.toString().contains("No APP_KEY variable"))
    }

    @Test
    fun noProjectRootFails() {
        val bare = temp.resolve("bare-dir")
        Files.createDirectories(bare)
        assertThrows(GrindException::class.java) {
            KeyGenerateCommand(workingDirectory = bare).run(listOf("--base=$bare"))
        }
    }

    @Test
    fun walksUpToProjectRoot() {
        val base = project("nested-root")
        Files.writeString(base.resolve(".env"), "APP_KEY=\n")
        val nested = base.resolve("src/main/kotlin")
        Files.createDirectories(nested)
        assertEquals(0, KeyGenerateCommand(workingDirectory = nested).run(listOf("--base=$nested")))
        assertTrue(Files.readString(base.resolve(".env")).contains("APP_KEY=base64:"))
    }

    @Test
    fun usesWorkingDirectoryWhenBaseOmitted() {
        val base = project("cwd-base")
        Files.writeString(base.resolve(".env"), "APP_KEY=\n")
        assertEquals(0, KeyGenerateCommand(workingDirectory = base).run(emptyList()))
        assertTrue(Files.readString(base.resolve(".env")).contains("APP_KEY=base64:"))
    }

    @Test
    fun wiredThroughGrindApplication() {
        val base = project("wired")
        Files.writeString(base.resolve(".env"), "APP_KEY=\n")
        assertEquals(0, GrindApplication().run(arrayOf("key:generate", "--base=$base")))
        assertTrue(Files.readString(base.resolve(".env")).contains("APP_KEY=base64:"))
        assertTrue(ListCommand.COMMANDS.any { it.first == "key:generate" })
        assertEquals(0, GrindApplication().run(arrayOf("key:generate", "--show")))
    }

    @Test
    fun emptyProductionKeyWritesWithoutConfirm() {
        val base = project("prod-empty")
        Files.writeString(base.resolve(".env"), "APP_ENV=production\nAPP_KEY=\n")
        assertEquals(
            0,
            KeyGenerateCommand(workingDirectory = base, environment = emptyMap())
                .run(listOf("--base=$base")),
        )
        assertTrue(Files.readString(base.resolve(".env")).contains("APP_KEY=base64:"))
    }

    private fun project(name: String): Path {
        val base = temp.resolve(name)
        Files.createDirectories(base)
        Files.writeString(base.resolve("settings.gradle.kts"), "rootProject.name = \"$name\"\n")
        return base
    }

    private fun capture(out: ByteArrayOutputStream, block: () -> Int): Int {
        val previous = System.out
        return try {
            System.setOut(PrintStream(out))
            block()
        } finally {
            System.setOut(previous)
        }
    }
}
