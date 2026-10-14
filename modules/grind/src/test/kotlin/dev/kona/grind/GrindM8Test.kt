package dev.kona.grind

import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.core.ServiceProvider
import dev.kona.schedule.Schedule
import dev.kona.schedule.ScheduleServiceProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class GrindM8Test {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() {
        Kona.reset()
        DemoPackageProvider.registered = false
    }

    private fun capture(block: () -> Int): Pair<Int, String> {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        try {
            return block() to buffer.toString()
        } finally {
            System.setOut(original)
        }
    }

    @Test
    fun listVerboseQuietAndIo() {
        assertEquals(0, capture { GrindApplication().run(arrayOf("list")) }.first)
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("schedule:run"))
        assertEquals("", capture { GrindApplication().run(arrayOf("list", "-q")) }.second.trim())
        assertEquals("", capture { GrindApplication().run(arrayOf("list", "--quiet")) }.second.trim())
        assertEquals(Verbosity.VERBOSE, GrindIO.fromOptions(mapOf("verbose" to "true")).verbosity)
        assertEquals(Verbosity.VERBOSE, GrindIO.fromOptions(mapOf("v" to "true")).verbosity)
        assertEquals(Verbosity.QUIET, GrindIO.fromOptions(mapOf("q" to "true")).verbosity)

        val buf = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val quiet = GrindIO(PrintStream(buf), PrintStream(err), Verbosity.QUIET)
        quiet.writeln("hidden")
        quiet.write("x")
        quiet.verbose("v")
        quiet.error("e")
        assertEquals("", buf.toString())
        assertTrue(err.toString().contains("e"))
        val verbose = GrindIO(PrintStream(buf), verbosity = Verbosity.VERBOSE)
        verbose.verbose("seen")
        verbose.writeln("line")
        verbose.write("w")
        assertTrue(buf.toString().contains("seen"))
        val normal = GrindIO(PrintStream(buf), verbosity = Verbosity.NORMAL)
        normal.write("n")
        normal.writeln()
        assertEquals(ListCommand.COMMANDS.size, ListCommand.COMMANDS.size)
    }

    @Test
    fun promptsAndPackageDiscovery() {
        val out = ByteArrayOutputStream()
        assertEquals("Ada", Prompts(ByteArrayInputStream("Ada\n".toByteArray()), PrintStream(out)).ask("Name"))
        assertEquals("def", Prompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(out)).ask("Q", "def"))
        assertTrue(Prompts(ByteArrayInputStream("yes\n".toByteArray()), PrintStream(out)).confirm("Ok?"))
        assertTrue(Prompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(out)).confirm("Ok?", true))
        assertFalseConfirm(out)
        assertEquals("b", Prompts(ByteArrayInputStream("2\n".toByteArray()), PrintStream(out)).choice("P", listOf("a", "b")))
        assertEquals("a", Prompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(out)).choice("P", listOf("a", "b")))
        assertEquals("b", Prompts(ByteArrayInputStream("b\n".toByteArray()), PrintStream(out)).choice("P", listOf("a", "b")))
        assertEquals("a", Prompts(ByteArrayInputStream("zzz\n".toByteArray()), PrintStream(out)).choice("P", listOf("a", "b")))
        assertThrows(IllegalArgumentException::class.java) {
            Prompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(out)).choice("P", emptyList())
        }

        val root = temp.resolve("pkg")
        Files.createDirectories(root.resolve("META-INF/kona"))
        Files.writeString(root.resolve("META-INF/kona/providers"), "dev.kona.grind.DemoPackageProvider\n#c\n")
        val loader = URLClassLoader(arrayOf(root.toUri().toURL()), GrindM8Test::class.java.classLoader)
        assertEquals(listOf("dev.kona.grind.DemoPackageProvider"), PackageDiscovery.providerNames(loader))
        val app = Application(temp.resolve("app"))
        PackageDiscovery.registerAll(app, loader)
        assertTrue(DemoPackageProvider.registered)
        assertThrows(Exception::class.java) {
            val bad = temp.resolve("bad")
            Files.createDirectories(bad.resolve("META-INF/kona"))
            Files.writeString(bad.resolve("META-INF/kona/providers"), "java.lang.String\n")
            PackageDiscovery.loadProviders(URLClassLoader(arrayOf(bad.toUri().toURL()), GrindM8Test::class.java.classLoader))
        }
    }

    private fun assertFalseConfirm(out: ByteArrayOutputStream) {
        assertEquals(false, Prompts(ByteArrayInputStream("n\n".toByteArray()), PrintStream(out)).confirm("Ok?", true))
    }

    @Test
    fun scheduleAndSteamCommands() {
        val project = fixtureProject(temp.resolve("sched"))
        val boot = "--bootstrap=${ScheduleFixtureApp::class.java.name}"
        val emptyBoot = "--bootstrap=${EmptyScheduleApp::class.java.name}"
        assertEquals(0, capture { GrindApplication().run(arrayOf("schedule:list", "--base=$project", boot)) }.first)
        assertTrue(capture { GrindApplication().run(arrayOf("schedule:list", "--base=$project", boot)) }.second.contains("tick"))
        assertTrue(
            capture { GrindApplication().run(arrayOf("schedule:list", "--base=$project", emptyBoot)) }
                .second.contains("No scheduled"),
        )
        assertEquals(0, capture { GrindApplication().run(arrayOf("schedule:run", "--base=$project", boot, "-v")) }.first)
        assertTrue(
            capture { GrindApplication().run(arrayOf("schedule:run", "--base=$project", emptyBoot)) }
                .second.contains("No scheduled tasks"),
        )
        assertEquals(1, GrindApplication().run(arrayOf("schedule:list", "--base=$project", "--bootstrap=${NoQueueApp::class.java.name}")))

        val steamIn = ByteArrayInputStream("config(\"app.name\")\n1+2\napp\nbad!!\n:quit\n".toByteArray())
        val steamOut = ByteArrayOutputStream()
        assertEquals(0, SteamCommand(steamIn, PrintStream(steamOut)).run(listOf("--base=$project", boot)))
        assertTrue(steamOut.toString().contains("steam"))

        assertEquals(0, GrindApplication().run(arrayOf("--help")))
        assertEquals(0, GrindApplication().run(arrayOf("-h")))
        assertEquals(1, GrindApplication().run(arrayOf("new", "x")))
        assertEquals(1, GrindApplication().run(arrayOf("nope")))
    }
}

class DemoPackageProvider : ServiceProvider {
    override fun register(app: Application) {
        registered = true
    }

    companion object {
        var registered = false
    }
}

object ScheduleFixtureApp {
    @JvmStatic
    fun create(args: Array<String>): Application {
        val app = Kona.create(args)
            .configure { it.set("app.name", "Fixture") }
            .providers(ScheduleServiceProvider())
            .boot()
        app.resolve(Schedule::class.java).everyMinute("tick") { }
        return app
    }
}

object EmptyScheduleApp {
    @JvmStatic
    fun create(args: Array<String>): Application =
        Kona.create(args).providers(ScheduleServiceProvider()).boot()
}
