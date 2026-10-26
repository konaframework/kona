package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.core.ServiceProvider
import org.konaframework.schedule.Schedule
import org.konaframework.schedule.ScheduleServiceProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.Reader
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.script.AbstractScriptEngine
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.ScriptException
import javax.script.SimpleBindings

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
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("assets:vite"))
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("ide:lsp"))
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("route:list"))
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("route:cache"))
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("route:clear"))
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("deploy:systemd"))
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("gradle:build"))
        assertTrue(capture { GrindApplication().run(arrayOf("list")) }.second.contains("gradle:install"))
        assertEquals("", capture { GrindApplication().run(arrayOf("list", "-q")) }.second.trim())
        assertEquals("", capture { GrindApplication().run(arrayOf("list", "--quiet")) }.second.trim())
        assertEquals(Verbosity.VERBOSE, GrindIO.fromOptions(mapOf("verbose" to "true")).verbosity)
        assertEquals(Verbosity.VERBOSE, GrindIO.fromOptions(mapOf("v" to "true")).verbosity)
        assertEquals(Verbosity.QUIET, GrindIO.fromOptions(mapOf("q" to "true")).verbosity)

        val vite = capture { GrindApplication().run(arrayOf("assets:vite")) }
        assertEquals(0, vite.first)
        assertTrue(vite.second.contains("Vite asset bundling"))
        assertTrue(vite.second.contains("manifest.json"))
        assertEquals(0, AssetsViteCommand().run(emptyList()))

        val lsp = capture { GrindApplication().run(arrayOf("ide:lsp")) }
        assertEquals(0, lsp.first)
        assertTrue(lsp.second.contains("kona-lsp"))
        assertTrue(lsp.second.contains("java -jar"))
        assertEquals(0, IdeLspCommand().run(emptyList()))

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
        assertTrue(out.toString().contains("┌"))
        assertTrue(out.toString().contains("›") || out.toString().contains("Name"))
        assertTrue(Prompts(ByteArrayInputStream("yes\n".toByteArray()), PrintStream(out)).confirm("Ok?"))
        assertTrue(Prompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(out)).confirm("Ok?", true))
        assertTrue(Prompts(ByteArrayInputStream("1\n".toByteArray()), PrintStream(out)).confirm("Ok?", false))
        assertFalse(Prompts(ByteArrayInputStream("2\n".toByteArray()), PrintStream(out)).confirm("Ok?", true))
        assertFalse(Prompts(ByteArrayInputStream("maybe\n".toByteArray()), PrintStream(out)).confirm("Ok?", false))
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
        Files.writeString(root.resolve("META-INF/kona/providers"), "org.konaframework.grind.DemoPackageProvider\n#c\n")
        val loader = URLClassLoader(arrayOf(root.toUri().toURL()), GrindM8Test::class.java.classLoader)
        assertEquals(listOf("org.konaframework.grind.DemoPackageProvider"), PackageDiscovery.providerNames(loader))
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

    @Test
    fun appLoaderAutoRegistersPackagesWhenProvidersPresent() {
        DemoPackageProvider.registered = false
        val withPkg = fixtureProject(temp.resolve("auto-pkg"))
        Files.createDirectories(withPkg.resolve("build/resources/main/META-INF/kona"))
        Files.writeString(
            withPkg.resolve("build/resources/main/META-INF/kona/providers"),
            "org.konaframework.grind.DemoPackageProvider\n",
        )
        val loader = AppLoader(withPkg, FixtureApp::class.java.name)
        val app = loader.create(arrayOf("--base=$withPkg"))
        assertTrue(DemoPackageProvider.registered)
        assertNotNull(app.getProvider(DemoPackageProvider::class.java))

        DemoPackageProvider.registered = false
        val bare = fixtureProject(temp.resolve("no-pkg"))
        val bareLoader = AppLoader(bare, FixtureApp::class.java.name)
        bareLoader.create(arrayOf("--base=$bare"))
        assertFalse(DemoPackageProvider.registered)
        assertTrue(
            bareLoader.discoverPackages(Application(temp.resolve("empty-disc")), bareLoader.classLoader!!).isEmpty(),
        )
    }

    private fun assertFalseConfirm(out: ByteArrayOutputStream) {
        assertEquals(false, Prompts(ByteArrayInputStream("n\n".toByteArray()), PrintStream(out)).confirm("Ok?", true))
    }

    @Test
    fun scheduleAndSteamCommands() {
        val project = fixtureProject(temp.resolve("sched"))
        val boot = "--bootstrap=${ScheduleFixtureApp::class.java.name}"
        val emptyBoot = "--bootstrap=${EmptyScheduleApp::class.java.name}"
        val listed = capture { GrindApplication().run(arrayOf("schedule:list", "--base=$project", boot)) }
        assertEquals(0, listed.first)
        assertTrue(listed.second.contains("tick"))
        assertTrue(listed.second.contains("no-overlap"))
        assertTrue(listed.second.contains("one-server"))
        assertTrue(listed.second.contains("background"))
        assertTrue(
            capture { GrindApplication().run(arrayOf("schedule:list", "--base=$project", emptyBoot)) }
                .second.contains("No scheduled"),
        )
        assertEquals(0, capture { GrindApplication().run(arrayOf("schedule:run", "--base=$project", boot, "-v")) }.first)
        assertTrue(
            capture { GrindApplication().run(arrayOf("schedule:run", "--base=$project", emptyBoot)) }
                .second.contains("No scheduled tasks"),
        )
        assertEquals(
            0,
            capture {
                GrindApplication().run(arrayOf("schedule:work", "--base=$project", boot, "--max=2", "--sleep=1", "-v"))
            }.first,
        )
        assertEquals(Int.MAX_VALUE, ScheduleRunCommand.workMaxTicks(emptyMap()))
        assertEquals(3, ScheduleRunCommand.workMaxTicks(mapOf("max" to "3")))
        assertEquals(1, ScheduleRunCommand.workMaxTicks(mapOf("max" to "0")))
        assertEquals(Int.MAX_VALUE, ScheduleRunCommand.workMaxTicks(mapOf("max" to "nope")))
        assertEquals(60L, ScheduleRunCommand.workSleepSeconds(emptyMap()))
        assertEquals(60L, ScheduleRunCommand.workSleepSeconds(mapOf("sleep" to "bad")))
        assertEquals(1L, ScheduleRunCommand.workSleepSeconds(mapOf("sleep" to "0")))
        assertEquals(2L, ScheduleRunCommand.workSleepSeconds(mapOf("sleep" to "2")))
        val workThread = Thread {
            GrindApplication().run(arrayOf("schedule:work", "--base=$project", boot, "--sleep=1"))
        }
        workThread.start()
        Thread.sleep(200)
        workThread.interrupt()
        workThread.join(8000)
        assertFalse(workThread.isAlive)
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

    @Test
    fun steamFallbackEnginesAndReplBranches() {
        val project = fixtureProject(temp.resolve("steam-branches"))
        val boot = "--bootstrap=${ScheduleFixtureApp::class.java.name}"
        val kts = StubEngine { "from-kts" }
        val kotlinEngine = StubEngine { "from-kotlin" }
        val js = StubEngine { "from-js" }
        assertEquals("from-kts", selectEngine(kts, kotlinEngine, js)?.eval("x"))
        assertEquals("from-kotlin", selectEngine(null, kotlinEngine, js)?.eval("x"))
        assertEquals("from-js", selectEngine(null, null, js)?.eval("x"))
        assertEquals(null, selectEngine(null, null, null))

        val discovered = steam(project, boot, "1\n")
        assertTrue(discovered.contains("steam>"))
        SteamCommand(ByteArrayInputStream(ByteArray(0)), PrintStream(ByteArrayOutputStream()))
            .createEngine(Thread.currentThread().contextClassLoader)

        val quit = steam(project, boot, " \n:quit\n", StubEngine { "kept" })
        assertFalse(quit.contains("kept"))
        val exitWord = steam(project, boot, ":exit\n", StubEngine { "nope" })
        assertFalse(exitWord.contains("nope"))
        val exitPlain = steam(project, boot, "exit\n", StubEngine { "nope" })
        assertFalse(exitPlain.contains("nope"))

        val values = steam(project, boot, "answer\nnull\n", StubEngine { script ->
            if (script == "null") null else "answer-ok"
        })
        assertTrue(values.contains("answer-ok"))

        val scriptError = steam(project, boot, "bad\n:quit\n", StubEngine {
            throw ScriptException("bad script")
        })
        assertTrue(scriptError.contains("bad script"))
        val scriptSilent = steam(project, boot, "bad\n:quit\n", StubEngine {
            throw ScriptException(null as String?)
        })
        assertTrue(scriptSilent.contains("ScriptException"), scriptSilent)
        val plainError = steam(project, boot, "bad\n:quit\n", StubEngine {
            throw IllegalStateException("broken")
        })
        assertTrue(plainError.contains("broken"))
        val plainSilent = steam(project, boot, "bad\n:quit\n", StubEngine {
            throw IllegalStateException(null as String?)
        })
        assertTrue(plainSilent.contains("IllegalStateException"))

        val fallback = steam(
            project,
            boot,
            "config(\"app.name\")\nconfig('app.name')\napp\n1 + 2 - 3 * 4 / 5\nnope\n",
            null,
        )
        assertTrue(fallback.contains("Fixture"))
        assertTrue(fallback.contains("org.konaframework.core.Application"))
        assertTrue(fallback.contains("0"))
        assertTrue(fallback.contains("No scripting engine"))

        val app = Kona.create(arrayOf("--base=$project")).configure { it.set("app.name", "Fixture") }.boot()
        assertEquals("Fixture", evaluateFallback(app, "config(\"app.name\")"))
        assertEquals("Fixture", evaluateFallback(app, "config('app.name')"))
        assertEquals(app, evaluateFallback(app, "app"))
        assertEquals(3L, evaluateFallback(app, "1+2"))
        assertThrows(GrindException::class.java) { evaluateFallback(app, "nope") }
        assertThrows(GrindException::class.java) { simpleMath("") }
        assertEquals(1L, simpleMath("1"))
        assertEquals(3L, simpleMath("4-1"))
        assertEquals(6L, simpleMath("2*3"))
        assertEquals(4L, simpleMath("9/2"))
        assertEquals(0L, simpleMath("1 + 2 - 3 * 4 / 5"))
        Kona.reset()
    }

    private fun steam(
        project: Path,
        boot: String,
        input: String,
        engine: ScriptEngine? = null,
    ): String {
        val out = ByteArrayOutputStream()
        val previousErr = System.err
        System.setErr(PrintStream(out, true))
        try {
            val command = SteamCommand(ByteArrayInputStream(input.toByteArray()), PrintStream(out, true))
            if (engine != null) {
                command.engineFactory = { _ -> engine }
            } else if (input.contains("config(")) {
                command.engineFactory = { _ -> null }
            }
            assertEquals(0, command.run(listOf("--base=$project", boot)))
            return out.toString()
        } finally {
            System.setErr(previousErr)
        }
    }
}

private class StubEngine(private val behavior: (String) -> Any?) : AbstractScriptEngine() {
    override fun eval(script: String, context: ScriptContext): Any? = behavior(script)

    override fun eval(reader: Reader, context: ScriptContext): Any? = eval(reader.readText(), context)

    override fun createBindings(): Bindings = SimpleBindings()

    override fun getFactory() = null
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
        app.resolve(Schedule::class.java).apply {
            everyMinute("tick") { }
            everyMinute("locked") { }.withoutOverlapping()
            everyMinute("server") { }.onOneServer()
            everyMinute("bg") { }.runInBackground()
        }
        return app
    }
}

object EmptyScheduleApp {
    @JvmStatic
    fun create(args: Array<String>): Application =
        Kona.create(args).providers(ScheduleServiceProvider()).boot()
}
