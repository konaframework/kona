package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.core.ServiceProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class AppCommandBootTest {
    @TempDir
    lateinit var temp: Path

    class UsesAppCommand : ConsoleCommand() {
        override val signature = "demo:app-config"
        override val description = "Reads config from the booted app"
        override fun handle(): Int {
            info("name=${app.config.getString("app.name", "missing")}")
            info("booted=${app.isBooted()}")
            info("has=${hasApp()}")
            return SUCCESS
        }
    }

    class WrongHandleReturn : ConsoleCommand() {
        override val signature = "demo:wrong-return"
        fun handle(flag: Boolean): String = "nope"
        override fun handle(): Int = SUCCESS
    }

    class InjectedHandleCommand : ConsoleCommand() {
        override val signature = "demo:inject-handle"
        fun handle(app: Application): Int {
            info("injected=${app.config.getString("app.name", "?")}")
            return SUCCESS
        }
    }

    class CtorInjectedCommand(
        private val application: Application,
    ) : ConsoleCommand() {
        override val signature = "demo:inject-ctor"
        override fun handle(): Int {
            info("ctor=${application.config.getString("app.name", "?")}")
            return SUCCESS
        }
    }

    class SecretCommand : ConsoleCommand() {
        override val signature = "demo:secret"
        override fun handle(): Int {
            val a = secret("Password")
            val b = password("Again")
            info("got=${a.length}:${b.length}")
            return SUCCESS
        }
    }

    class NoHandleDeclared : ConsoleCommand() {
        override val signature = "demo:no-handle-decl"
    }

    class NeedsString(val value: String) : ConsoleCommand() {
        constructor() : this("default")

        override val signature = "demo:needs-string"
        override fun handle(): Int = SUCCESS
    }

    object BootCommandsApp {
        @JvmStatic
        fun create(args: Array<String>): Application =
            Kona.create(args)
                .configure { it.set("app.name", "BootCmd") }
                .providers(
                    object : ServiceProvider {
                        override fun register(app: Application) = Unit

                        override fun boot(app: Application) {
                            app.commands(
                                UsesAppCommand::class.java,
                                InjectedHandleCommand::class.java,
                                CtorInjectedCommand::class.java,
                            )
                        }
                    },
                )
                .boot()
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
    fun withApplicationBootsCustomProviderCommands() {
        val project = fixtureProject(temp.resolve("boot-cmds"))
        val boot = "--bootstrap=${BootCommandsApp::class.java.name}"
        val (code, out) =
            capture {
                GrindApplication().run(arrayOf("demo:app-config", "--base=$project", boot))
            }
        assertEquals(0, code)
        assertTrue(out.contains("name=BootCmd"), out)
        assertTrue(out.contains("booted=true"), out)

        val injected =
            capture {
                GrindApplication().run(arrayOf("demo:inject-handle", "--base=$project", boot))
            }
        assertEquals(0, injected.first)
        assertTrue(injected.second.contains("injected=BootCmd"), injected.second)

        val ctor =
            capture {
                GrindApplication().run(arrayOf("demo:inject-ctor", "--base=$project", boot))
            }
        assertEquals(0, ctor.first)
        assertTrue(ctor.second.contains("ctor=BootCmd"), ctor.second)
    }

    @Test
    fun metaInfCommandsReceiveBoundApp() {
        val project = fixtureProject(temp.resolve("meta-cmds"))
        val meta = project.resolve("build/resources/main/META-INF/kona")
        Files.createDirectories(meta)
        Files.writeString(meta.resolve("commands"), "${UsesAppCommand::class.java.name}\n")
        val boot = "--bootstrap=${FixtureApp::class.java.name}"
        val (code, out) =
            capture {
                GrindApplication().run(arrayOf("demo:app-config", "--base=$project", boot))
            }
        assertEquals(0, code)
        assertTrue(out.contains("booted=true"), out)
    }

    @Test
    fun grindCallWithBootedApp() {
        Kona.reset()
        val app =
            Application.builder(temp.resolve("tester-app"))
                .configure { it.set("app.name", "Tester") }
                .providers(
                    object : ServiceProvider {
                        override fun register(app: Application) = Unit

                        override fun boot(app: Application) {
                            app.commands(UsesAppCommand::class.java)
                        }
                    },
                )
                .build()
        val (code, out) =
            capture {
                Grind.call("demo:app-config", emptyList(), null, app.basePath, javaClass.classLoader, app)
            }
        assertEquals(0, code)
        assertTrue(out.contains("name=Tester"), out)
        assertThrows(GrindException::class.java) {
            AppCommandRunner.runWithApp(app, "demo:missing", emptyList())
        }
    }

    @Test
    fun applicationCommandsApiDedupesAndRejectsNonCommands() {
        val app = Application(temp.resolve("cmds-api"))
        app.commands(UsesAppCommand::class.java, UsesAppCommand::class.java)
        assertEquals(1, app.consoleCommands().size)
        app.commands(String::class.java)
        assertThrows(GrindException::class.java) {
            CommandRegistry().registerApplicationCommands(app)
        }
    }

    @Test
    fun secretHelpersAndSelectInjectableHandle() {
        val out = ByteArrayOutputStream()
        val prompts = Prompts(ByteArrayInputStream("s3cret\nhidden\n".toByteArray()), PrintStream(out), color = false)
        assertEquals("s3cret", prompts.secret("Password"))
        assertEquals("hidden", prompts.password("Again"))
        assertTrue(out.toString().contains("*") || out.toString().contains("Password"))

        val cmd = SecretCommand()
        cmd.bind(
            CommandInput(emptyMap(), emptyMap(), emptyMap()),
            GrindIO(PrintStream(ByteArrayOutputStream()), color = false),
        )
        cmd.bindPrompts(Prompts(ByteArrayInputStream("a\nb\n".toByteArray()), PrintStream(ByteArrayOutputStream())))
        assertEquals(0, cmd.handle())
        assertFalse(cmd.hasApp())
        assertThrows(GrindException::class.java) { cmd.app }

        val handle = CommandExecutor.selectInjectableHandle(InjectedHandleCommand::class.java)!!
        assertEquals(1, handle.parameterCount)
        assertEquals(Application::class.java, handle.parameterTypes[0])

        val plain = CommandExecutor.selectInjectableHandle(UsesAppCommand::class.java)!!
        assertEquals(0, plain.parameterCount)

        assertThrows(GrindException::class.java) {
            object : ConsoleCommand() {
                override val signature = "demo:missing-handle"
            }.handle()
        }
        assertNull(CommandExecutor.selectInjectableHandle(NoHandleDeclared::class.java))
        // Non-Int handle(Boolean) is skipped; zero-arg Int handle wins.
        assertEquals(0, CommandExecutor.selectInjectableHandle(WrongHandleReturn::class.java)!!.parameterCount)
        // Declared handle on ConsoleCommand itself is skipped (declaringClass filter).
        assertNull(CommandExecutor.selectInjectableHandle(ConsoleCommand::class.java))

        class TwoHandles : ConsoleCommand() {
            override val signature = "demo:two-handles"
            fun handle(a: Application): Int = SUCCESS
            fun handle(a: Application, unused: org.konaframework.core.Config): Int = SUCCESS
        }
        assertEquals(2, CommandExecutor.selectInjectableHandle(TwoHandles::class.java)!!.parameterCount)

        class TieHandles : ConsoleCommand() {
            override val signature = "demo:tie-handles"
            fun handle(a: Application): Int = SUCCESS
            fun handle(c: org.konaframework.core.Config): Int = SUCCESS
        }
        // Equal arity keeps the first seen; still returns a 1-arg handle.
        assertEquals(1, CommandExecutor.selectInjectableHandle(TieHandles::class.java)!!.parameterCount)
    }

    @Test
    fun appCommandRunnerWithoutProjectFallsBack() {
        val bare = temp.resolve("no-project")
        Files.createDirectories(bare)
        val reg = CommandRegistry().register {
            object : ConsoleCommand() {
                override val signature = "demo:plain-bootless"
                override fun handle(): Int = SUCCESS
            }
        }
        val io = GrindIO(PrintStream(ByteArrayOutputStream()), color = false)
        assertEquals(
            0,
            AppCommandRunner.run(
                "demo:plain-bootless",
                emptyList(),
                mapOf("base" to bare.toString()),
                reg,
                bare,
                io,
            ),
        )
        // options without base → start = basePath
        assertEquals(
            1,
            AppCommandRunner.run("demo:missing", emptyList(), emptyMap(), reg, bare, io),
        )
    }

    @Test
    fun unknownCustomCommandInProjectPrintsFailure() {
        val project = fixtureProject(temp.resolve("unknown-cmd"))
        val boot = "--bootstrap=${FixtureApp::class.java.name}"
        val (code, _) =
            capture {
                GrindApplication().run(arrayOf("demo:does-not-exist", "--base=$project", boot))
            }
        assertEquals(1, code)
    }

    @Test
    fun commandExecutorPromptsAskConfirmChoiceAndCreateFallback() {
        val prompts =
            Prompts(
                ByteArrayInputStream("Ada\ny\n2\n".toByteArray()),
                PrintStream(ByteArrayOutputStream()),
                color = false,
            )
        class Interactive : ConsoleCommand() {
            override val signature = "demo:interactive"
            override fun handle(): Int {
                ask("Name")
                confirm("Ok?")
                choice("Pick", listOf("a", "b"))
                return SUCCESS
            }
        }
        assertEquals(0, CommandExecutor(temp, prompts = prompts).run(Interactive(), emptyList()))

        // prompts() fallback when bindPrompts was never called
        val originalIn = System.`in`
        try {
            System.setIn(ByteArrayInputStream("fallback\n".toByteArray()))
            class AskFallback : ConsoleCommand() {
                override val signature = "demo:ask-fallback"
                override fun handle(): Int {
                    ask("Q")
                    return SUCCESS
                }
            }
            assertEquals(0, CommandExecutor(temp).run(AskFallback(), emptyList()))
        } finally {
            System.setIn(originalIn)
        }

        val app =
            Application.builder(temp.resolve("make-app"))
                .configure { it.set("app.name", "X") }
                .build()
        assertTrue(CommandExecutor.createCommand(UsesAppCommand::class.java, app) is UsesAppCommand)
        assertTrue(CommandExecutor.createCommand(UsesAppCommand::class.java, null) is UsesAppCommand)
        // BindingException on String ctor → fall back to no-arg
        assertEquals("default", (CommandExecutor.createCommand(NeedsString::class.java, app) as NeedsString).value)

        assertEquals(
            UsesAppCommand::class.java,
            CommandRegistry().register(UsesAppCommand::class.java).type("demo:app-config"),
        )

        assertEquals(
            0,
            CommandExecutor(temp, app).run(InjectedHandleCommand(), emptyList()).also {
                // injectable handle(Application) path
            },
        )
        assertThrows(GrindException::class.java) {
            CommandExecutor(temp, app).run(NoHandleDeclared(), emptyList())
        }

        // Cover Grind.call 5-arg overload that passes null app
        assertEquals(0, Grind.call("list", emptyList(), null, temp, javaClass.classLoader))

        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = null
        try {
            Kona.reset()
            val booted =
                Application.builder(temp.resolve("ctx-app"))
                    .configure { it.set("app.name", "Ctx") }
                    .providers(
                        object : ServiceProvider {
                            override fun register(app: Application) = Unit

                            override fun boot(app: Application) {
                                app.commands(UsesAppCommand::class.java)
                            }
                        },
                    )
                    .build()
            assertEquals(0, AppCommandRunner.runWithApp(booted, "demo:app-config", emptyList()))
        } finally {
            Thread.currentThread().contextClassLoader = previous
        }
    }
}
