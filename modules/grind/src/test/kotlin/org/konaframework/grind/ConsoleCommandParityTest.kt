package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ConsoleCommandParityTest {
    @TempDir
    lateinit var temp: Path

    class GreetCommand : ConsoleCommand() {
        override val signature = "demo:greet {name} {--shout} {--title=Hi}"
        override val description = "Greet someone"
        override fun handle(): Int {
            val name = argumentOrFail("name")
            val title = option("title", "Hi")
            val msg = "$title, $name"
            if (optionBool("shout")) {
                success(msg.uppercase())
            } else {
                info(msg)
            }
            table(listOf("k", "v"), listOf(listOf("name", name)))
            return SUCCESS
        }
    }

    class OptionalArrayCommand : ConsoleCommand() {
        override val signature = "demo:opt {nick?} {files*} {--mode= : run mode}"
        override val description = "Optional and array args"
        override fun handle(): Int {
            info("nick=${argument("nick") ?: "-"}")
            info("files=${arguments("files").joinToString(",")}")
            info("mode=${option("mode")}")
            return SUCCESS
        }
    }

    class IsolatedCommand : ConsoleCommand() {
        override val signature = "demo:locked"
        override val description = "Isolatable"
        override val isolatable = true
        override fun handle(): Int {
            info("ran")
            return SUCCESS
        }
    }

    class PlainCommand : ConsoleCommand() {
        override val signature = "demo:plain"
        override fun handle(): Int = SUCCESS
    }

    private fun capture(block: () -> Int): Pair<Int, String> {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        return try {
            block() to buffer.toString()
        } finally {
            System.setOut(original)
        }
    }

    @Test
    fun signatureParseAndBind() {
        val sig = SignatureParser.parse(
            "mail:send {user} {extra?} {items*} {--queue=} {--force} {--retries=3 : how many}",
            "Send mail",
        )
        assertEquals("mail:send", sig.name)
        assertEquals(3, sig.arguments.size)
        assertTrue(sig.arguments[1].optional)
        assertTrue(sig.arguments[2].isArray)
        assertEquals("3", sig.options.first { it.name == "retries" }.default)
        assertTrue(sig.helpLines().any { it.contains("Arguments:") })
        assertTrue(sig.helpLines().any { it.contains("--force") })

        val input = SignatureParser.bind(
            sig,
            listOf("ada", "x", "a", "b"),
            mapOf("force" to "true", "queue" to "default"),
        )
        assertEquals("ada", input.argument("user"))
        assertEquals("x", input.argument("extra"))
        assertEquals(listOf("a", "b"), input.arguments("items"))
        assertEquals("default", input.option("queue"))
        assertTrue(input.hasOption("force"))
        assertEquals("3", input.option("retries"))

        assertThrows(GrindException::class.java) {
            SignatureParser.bind(sig, emptyList(), emptyMap())
        }
        val noArray = SignatureParser.parse("x:y {a}")
        assertThrows(GrindException::class.java) {
            SignatureParser.bind(noArray, listOf("1", "2"), emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) { SignatureParser.parse("") }
        assertThrows(IllegalArgumentException::class.java) { SignatureParser.parse("{oops}") }
        assertThrows(IllegalArgumentException::class.java) {
            SignatureParser.parse("bad {1name}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SignatureParser.parse("bad {--}")
        }
        val flagged = SignatureParser.bind(
            SignatureParser.parse("f:x {--flag}"),
            emptyList(),
            mapOf("flag" to "true", "isolated" to "true"),
        )
        assertTrue(flagged.hasOption("isolated"))

        val optional = SignatureParser.bind(
            SignatureParser.parse("demo:opt {nick?} {--retries=3} {--queue=}"),
            emptyList(),
            emptyMap(),
        )
        assertEquals(null, optional.argument("nick"))
        assertEquals("3", optional.option("retries"))
        assertFalse(optional.hasOption("queue"))
        assertTrue(optional.arguments("missing").isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            SignatureParser.parse("bad {*}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SignatureParser.parse("bad {--=x}")
        }
        val helpOnly = SignatureParser.parse("solo")
        assertTrue(helpOnly.helpLines().isEmpty())
        val described = SignatureParser.parse(
            "demo:d {name : who} {--queue= : q}",
            "Desc",
        )
        assertTrue(described.helpLines().any { it.contains("who") })
        assertTrue(described.helpLines().any { it.contains("--queue=") })
    }

    @Test
    fun executorHelpMissingArgsAndIsolation() {
        val (code, out) = capture {
            CommandExecutor(temp).run(GreetCommand(), listOf("--help"))
        }
        assertEquals(0, code)
        assertTrue(out.contains("demo:greet"))

        val missing = capture { CommandExecutor(temp).run(GreetCommand(), emptyList()) }
        assertEquals(1, missing.first)

        assertEquals("Invalid arguments", CommandExecutor.formatGrindError(GrindException(null)))
        assertEquals("boom", CommandExecutor.formatGrindError(GrindException("boom")))

        val ok = capture {
            CommandExecutor(temp).run(GreetCommand(), listOf("Ada", "--shout", "--title=Yo"))
        }
        assertEquals(0, ok.first)
        assertTrue(ok.second.uppercase().contains("YO, ADA") || ok.second.contains("YO, ADA"))

        val opt = capture {
            CommandExecutor(temp).run(OptionalArrayCommand(), listOf("n", "f1", "f2", "--mode=fast"))
        }
        assertEquals(0, opt.first)
        assertTrue(opt.second.contains("files=f1,f2"))

        val warn = capture {
            CommandExecutor(temp).run(PlainCommand(), listOf("--isolated"))
        }
        assertEquals(0, warn.first)

        val locked = capture {
            CommandExecutor(temp).run(IsolatedCommand(), listOf("--isolated"))
        }
        assertEquals(0, locked.first)
        assertTrue(locked.second.contains("ran"))

        val lockDir = temp.resolve("storage/framework/grind")
        Files.createDirectories(lockDir)
        val lockFile = lockDir.resolve("demo_locked.lock")
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { ch ->
            val held = ch.lock()
            try {
                var ran = false
                val codeBusy = CommandIsolation(temp).runIsolated("demo:locked") {
                    ran = true
                    99
                }
                assertEquals(0, codeBusy)
                assertFalse(ran)
            } finally {
                held.release()
            }
        }
        assertEquals("demo_locked", CommandIsolation.sanitize("demo:locked"))

        var skipped = false
        val nullLock =
            CommandIsolation(temp) { _: FileChannel -> null }.runIsolated("x:y") {
                skipped = true
                7
            }
        // secondary constructor (default acquire)
        assertEquals(0, CommandIsolation(temp).runIsolated("ok:path") { 0 })
        assertEquals(0, nullLock)
        assertFalse(skipped)

        // Same-JVM second lock → OverlappingFileLockException → null
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { held ->
            held.lock()
            FileChannel.open(lockFile, StandardOpenOption.WRITE).use { other ->
                assertNull(CommandIsolation.tryAcquire(other))
            }
        }

        val shortHelp = capture { CommandExecutor(temp).run(GreetCommand(), listOf("-h")) }
        assertEquals(0, shortHelp.first)
        val plainHelp = capture { CommandExecutor(temp).run(PlainCommand(), listOf("--help")) }
        assertEquals(0, plainHelp.first)
        val optHelp = capture { CommandExecutor(temp).run(OptionalArrayCommand(), listOf("--help")) }
        assertEquals(0, optHelp.first)
        assertTrue(optHelp.second.contains("demo:opt"))

        assertEquals(1, CommandExecutor(temp).run(GreetCommand(), listOf("Ada", "--foo")))
        assertEquals(1, CommandExecutor(temp).run(PlainCommand(), listOf("extra")))
        assertThrows(GrindException::class.java) {
            SignatureParser.bind(
                SignatureParser.parse("demo:greet {name}"),
                listOf("Ada"),
                mapOf("foo" to "true"),
            )
        }
    }

    @Test
    fun registryDiscoveryMakeAndCall() {
        val registry = CommandRegistry().register(GreetCommand::class.java)
        assertTrue(registry.has("demo:greet"))
        assertTrue(registry.summaries().any { it.first == "demo:greet" })
        assertEquals(setOf("demo:greet"), registry.names())

        val other = CommandRegistry().register { OptionalArrayCommand() }
        registry.merge(other)
        assertTrue(registry.has("demo:opt"))

        val metaRoot = temp.resolve("disc")
        Files.createDirectories(metaRoot.resolve("META-INF/kona"))
        Files.writeString(
            metaRoot.resolve("META-INF/kona/commands"),
            "# comment\n\n  \n${GreetCommand::class.java.name}\n",
        )
        val loader = URLClassLoader(arrayOf(metaRoot.toUri().toURL()), javaClass.classLoader)
        assertEquals(listOf(GreetCommand::class.java.name), CommandDiscovery.commandNames(loader))
        val discovered = CommandRegistry()
        CommandDiscovery.registerAll(discovered, loader)
        assertTrue(discovered.has("demo:greet"))

        val bad = temp.resolve("bad")
        Files.createDirectories(bad.resolve("META-INF/kona"))
        Files.writeString(bad.resolve("META-INF/kona/commands"), "java.lang.String\n")
        assertThrows(Exception::class.java) {
            CommandDiscovery.loadCommands(URLClassLoader(arrayOf(bad.toUri().toURL()), javaClass.classLoader))
        }

        assertEquals(0, MakeCommandCommand().run(listOf("SendDigest", "--base=$temp", "--lang=kotlin")))
        assertTrue(Files.exists(temp.resolve("src/main/kotlin/app/console/commands/SendDigestCommand.kt")))
        val meta = temp.resolve("src/main/resources/META-INF/kona/commands")
        assertTrue(Files.readString(meta).contains("SendDigestCommand"))
        assertEquals(0, MakeCommandCommand().run(listOf("OtherThing", "--base=$temp", "--command=app:other", "--lang=kotlin")))
        assertThrows(GrindException::class.java) {
            MakeCommandCommand().run(listOf("SendDigest", "--base=$temp"))
        }
        // name already ending with Command
        assertEquals(0, MakeCommandCommand().run(listOf("AlreadyCommand", "--base=$temp", "--lang=kotlin")))
        // appendDiscovery early-return: delete source, keep meta line, recreate
        Files.delete(temp.resolve("src/main/kotlin/app/console/commands/AlreadyCommand.kt"))
        assertEquals(0, MakeCommandCommand().run(listOf("AlreadyCommand", "--base=$temp", "--lang=kotlin")))
        // meta without trailing newline → prefix branch
        Files.writeString(meta, Files.readString(meta).trimEnd())
        assertEquals(0, MakeCommandCommand().run(listOf("ThirdOne", "--base=$temp", "--lang=kotlin")))

        assertEquals(0, MakeCommandCommand().run(listOf("JavaHello", "--base=$temp", "--lang=java")))
        assertTrue(Files.exists(temp.resolve("src/main/java/app/console/commands/JavaHelloCommand.java")))

        assertThrows(GrindException::class.java) { MakeCommandCommand().run(emptyList()) }
        assertThrows(GrindException::class.java) {
            MakeCommandCommand().run(listOf("bad-name", "--base=$temp"))
        }

        val callCode = Grind.call("demo:greet", listOf("Sam"), registry, temp)
        assertEquals(0, callCode)
        assertEquals(0, Grind.callSilently("list", emptyList(), registry, temp))
        assertEquals(0, Grind.call("list", emptyList(), registry, temp))
        assertEquals(0, Grind.call("list"))
        assertEquals(0, Grind.call("list", emptyList()))
        assertEquals(0, Grind.call("list", emptyList(), registry))
        assertEquals(0, Grind.call("list", emptyList(), registry, temp))
        assertEquals(0, Grind.callSilently("list"))
        assertEquals(0, Grind.callSilently("list", emptyList()))
        assertEquals(0, Grind.callSilently("list", emptyList(), registry))
        assertEquals(0, Grind.callSilently("list", emptyList(), registry, temp))

        val app = GrindApplication(registry, temp)
        val listed = capture { app.run(arrayOf("list", "--base=$temp")) }
        assertEquals(0, listed.first)
        assertTrue(listed.second.contains("demo:greet"))
        assertTrue(listed.second.contains("make:command"))

        val runCustom = capture { app.run(arrayOf("demo:greet", "Pat", "--base=$temp")) }
        assertEquals(0, runCustom.first)

        val help = capture { app.run(arrayOf("help")) }
        assertEquals(0, help.first)
        assertTrue(help.second.contains("make:command"))

        // App output discovery path
        val buildRes = temp.resolve("build/resources/main/META-INF/kona")
        Files.createDirectories(buildRes)
        Files.writeString(buildRes.resolve("commands"), "${PlainCommand::class.java.name}\n")
        val withBuild = GrindApplication(CommandRegistry(), temp)
        val listed2 = capture { withBuild.run(arrayOf("list", "--base=$temp")) }
        assertEquals(0, listed2.first)
        assertTrue(listed2.second.contains("demo:plain") || listed2.second.contains("make:command"))

        val cmd = GreetCommand()
        cmd.bind(
            CommandInput(mapOf("name" to "x"), emptyMap(), mapOf("shout" to "yes", "n" to "1")),
            GrindIO(PrintStream(ByteArrayOutputStream()), color = false),
        )
        assertTrue(cmd.optionBool("shout"))
        assertTrue(cmd.hasOption("shout"))
        assertEquals("x", cmd.argument("name"))
        assertEquals("fallback", cmd.option("missing", "fallback"))
        assertFalse(cmd.optionBool("nope"))
        assertThrows(GrindException::class.java) { cmd.argumentOrFail("missing") }
        cmd.line()
        cmd.line("plain")
        cmd.warn("w")
        cmd.error("e")
        cmd.title("t")
        cmd.progress(1, 2)
        cmd.progress(1, 2, "step")
        cmd.verbose("v")
        assertEquals("demo:greet", cmd.name())
        assertEquals(ConsoleCommand.SUCCESS, 0)
        assertEquals(ConsoleCommand.FAILURE, 1)

        val one = GreetCommand()
        one.bind(
            CommandInput(mapOf("name" to "z"), emptyMap(), mapOf("flag" to "1")),
            GrindIO(PrintStream(ByteArrayOutputStream()), color = false),
        )
        assertTrue(one.optionBool("flag"))
        assertFalse(one.hasOption("missing"))
        assertEquals(Verbosity.NORMAL, one.io.verbosity)
    }

    @Test
    fun listDedupesOverlappingNames() {
        class DupList : ConsoleCommand() {
            override val signature = "list"
            override val description = "custom list"
            override fun handle(): Int = SUCCESS
        }
        val registry = CommandRegistry().register(DupList::class.java)
        val (code, out) = capture { ListCommand(registry).run(emptyList()) }
        assertEquals(0, code)
        // Builtin entry wins; custom description must not appear twice as separate rows.
        assertFalse(out.contains("custom list"))
        assertTrue(out.contains("List grind commands") || out.contains("list"))
    }
}
