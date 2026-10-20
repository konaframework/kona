package org.konaframework.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.konaframework.support.console.ConsoleIO
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class NewCommandInteractiveTest {
    @TempDir
    lateinit var temp: Path

    private fun command(
        input: String,
        windows: Boolean = false,
        runner: GrindProcessRunner = GrindProcessRunner { _, _ -> 0 },
        versions: KonaVersions = KonaVersions("0.0.0-test", "2.0.0"),
        prompts: InstallerPrompts? = null,
    ): Pair<NewCommand, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        val stream = PrintStream(out)
        val io = ConsoleIO(stream, stream, color = false)
        val used = prompts ?: InstallerPrompts(ByteArrayInputStream(input.toByteArray()), stream, color = false)
        val cmd = NewCommand(versions, io, used, runner, windows)
        return cmd to out
    }

    @Test
    fun helpersParseFlagsAndTitleCase() {
        assertEquals("Kona", titleCaseFolder(""))
        assertEquals("My App", titleCaseFolder("my-app"))
        assertEquals("Hello World", titleCaseFolder("hello.world"))
        assertEquals("Demo", titleCaseFolder("demo"))
        assertEquals("HTML Kit", titleCaseFolder("HTML-kit"))
        assertFalse(NewCommand.defaultWindows("Linux"))
        assertTrue(NewCommand.defaultWindows("Windows 11"))
        assertFalse(NewCommand.defaultWindows(null))
        NewCommand.defaultWindows()
        assertFalse(wantsInteraction(mapOf("no-interaction" to "true")))
        assertFalse(wantsInteraction(mapOf("no-interactive" to "true")))
        // CI / missing TTY must not imply --no-interaction (Cursor, direnv, and some shells export CI).
        assertTrue(wantsInteraction(emptyMap()))
        assertEquals(null, triState(emptyMap(), "migrate"))
        assertEquals(false, triState(mapOf("no-migrate" to "true"), "migrate"))
        assertEquals(false, triState(mapOf("migrate" to "false"), "migrate"))
        assertEquals(false, triState(mapOf("migrate" to "0"), "migrate"))
        assertEquals(false, triState(mapOf("migrate" to "no"), "migrate"))
        assertEquals(true, triState(mapOf("migrate" to "true"), "migrate"))
        assertEquals("tailwind", parseCssFlag("tw"))
        assertEquals("bootstrap", parseCssFlag("bs"))
        assertEquals("custom", parseCssFlag("none"))
        assertEquals("custom", parseCssFlag("vanilla"))
        assertEquals("custom", parseCssFlag("plain"))
        assertEquals("vue", parseFrontendFlag("Vue"))
        assertEquals("crema", parseKitFlag("crema"))
        assertEquals("java", parseLangFlag("JAVA"))
        assertEquals("api", NewStacks.kitId(NewStacks.API))
        assertEquals("brew", NewStacks.kitId(NewStacks.BREW))
        assertEquals("inertia", NewStacks.kitId(NewStacks.INERTIA))
        assertEquals("web", NewStacks.kitId(NewStacks.CREMA))
        assertEquals("tailwind", NewStacks.cssId(NewStacks.CSS_TAILWIND))
        assertEquals("bootstrap", NewStacks.cssId(NewStacks.CSS_BOOTSTRAP))
        assertEquals("custom", NewStacks.cssId(NewStacks.CSS_CUSTOM))
        assertEquals("react", NewStacks.frontendId(NewStacks.FE_REACT))
        assertEquals("svelte", NewStacks.frontendId(NewStacks.FE_SVELTE))
        assertEquals("vue", NewStacks.frontendId(NewStacks.FE_VUE))
        assertEquals("java", NewStacks.langId(NewStacks.LANG_JAVA))
        assertEquals("kotlin", NewStacks.langId(NewStacks.LANG_KOTLIN))
        assertEquals("ts", NewStacks.scriptId(NewStacks.SCRIPT_TS))
        assertEquals("js", NewStacks.scriptId(NewStacks.SCRIPT_JS))
        assertEquals("js", NewStacks.scriptId("other"))
        assertEquals(null, frontendScriptChoice(emptyMap()))
        assertEquals(null, frontendScriptChoice(mapOf("kit" to "inertia")))
        assertEquals("ts", frontendScriptChoice(mapOf("typescript" to "true")))
        assertEquals("ts", frontendScriptChoice(mapOf("ts" to "yes")))
        assertEquals("js", frontendScriptChoice(mapOf("javascript" to "true")))
        assertEquals("js", frontendScriptChoice(mapOf("js" to "true")))
        assertEquals("js", frontendScriptChoice(mapOf("typescript" to "false")))
        assertEquals("js", frontendScriptChoice(mapOf("ts" to "0")))
        assertEquals("js", frontendScriptChoice(mapOf("typescript" to "no")))
        assertEquals("ts", frontendScriptChoice(mapOf("javascript" to "false")))
        assertEquals("ts", frontendScriptChoice(mapOf("js" to "NO")))
        assertEquals("ts", frontendScriptChoice(linkedMapOf("javascript" to "true", "typescript" to "true")))
        assertEquals("js", frontendScriptChoice(linkedMapOf("ts" to "true", "js" to "true")))
        assertEquals(
            "ts",
            frontendScriptChoice(linkedMapOf("typescript" to "false", "javascript" to "false")),
        )
        assertTrue(flagOn("true"))
        assertTrue(flagOn("TRUE"))
        assertFalse(flagOn("false"))
        assertFalse(flagOn("0"))
        assertFalse(flagOn("no"))
        assertFalse(flagOn("NO"))
        assertThrows<KonaCliException> { parseCssFlag("bulma") }
        assertThrows<KonaCliException> { parseFrontendFlag("angular") }
        assertThrows<KonaCliException> { parseKitFlag("rails") }
        assertThrows<KonaCliException> { parseLangFlag("ruby") }
    }

    @Test
    fun stackLabelsCoverKits() {
        val db = DatabaseEngines.require("sqlite")
        assertEquals("API only", NewPlan(temp, "A", "kotlin", "api", "custom", "", db, false, false, false).stackLabel)
        assertEquals(
            "Brew · Tailwind CSS",
            NewPlan(temp, "A", "kotlin", "brew", "tailwind", "", db, false, false, false).stackLabel,
        )
        assertEquals(
            "Inertia · React · Bootstrap",
            NewPlan(temp, "A", "kotlin", "inertia", "bootstrap", "react", db, false, false, false).stackLabel,
        )
        assertEquals(
            "Inertia · React · Bootstrap · TypeScript",
            NewPlan(temp, "A", "kotlin", "inertia", "bootstrap", "react", db, false, false, false, "ts").stackLabel,
        )
        assertEquals(
            "Inertia · custom CSS",
            NewPlan(temp, "A", "kotlin", "inertia", "custom", "", db, false, false, false).stackLabel,
        )
        assertEquals(
            "Crema · custom CSS",
            NewPlan(temp, "A", "kotlin", "web", "custom", "", db, false, false, false).stackLabel,
        )
    }

    @Test
    fun noInteractionWithoutNameThrowsUsage() {
        val (cmd, _) = command("")
        val ex = assertThrows<KonaCliException> { cmd.run(listOf("--no-interaction")) }
        assertTrue(ex.message!!.contains("Usage: kona new"))
        assertTrue(ex.message!!.contains("--no-interaction"))
        val out = ByteArrayOutputStream()
        val stream = PrintStream(out)
        val io = ConsoleIO(stream, stream, color = false)
        val prompts = InstallerPrompts(ByteArrayInputStream(ByteArray(0)), stream, color = false)
        val planEx =
            assertThrows<KonaCliException> {
                resolveNewPlan(emptyList(), mapOf("no-interaction" to "true"), false, prompts, io)
            }
        assertTrue(planEx.message!!.contains("Usage: kona new"))
    }

    @Test
    fun noInteractionFlagSkipsPromptsOnTty() {
        val app = temp.resolve("flagged")
        val (cmd, out) = command("this-would-hang\n")
        assertEquals(0, cmd.run(listOf(app.toString(), "--no-interaction")))
        assertTrue(Files.isRegularFile(app.resolve("build.gradle.kts")))
        assertTrue(Files.readString(app.resolve(".env")).contains("APP_NAME=flagged"))
        assertFalse(out.toString().contains("Where should we create"))
        val app2 = temp.resolve("flagged2")
        assertEquals(0, command("").first.run(listOf(app2.toString(), "--no-interactive")))
        assertTrue(Files.isRegularFile(app2.resolve("grind")))
    }

    @Test
    fun positionalNameStillPromptsRemainingSteps() {
        val dest = temp.resolve("app-seven")
        val recording = RecordingPrompts("\n".repeat(16))
        val (cmd, out) = command("", prompts = recording)
        // argv `new app-seven` (absolute in tests so we do not write into the repo).
        assertEquals(
            0,
            KonaApplication(ConsoleIO(PrintStream(out), PrintStream(out), false), cmd)
                .run(arrayOf("new", dest.toString())),
        )
        assertEquals("app-seven", dest.fileName.toString())
        assertFalse(recording.asked.any { it.contains("Where should we create") })
        assertTrue(recording.asked.any { it == "App name" })
        assertTrue(recording.choices.contains("Language"))
        assertTrue(recording.choices.contains("What are we brewing?"))
        assertTrue(recording.choices.contains("CSS"))
        assertTrue(recording.choices.contains("Database"))
        assertTrue(recording.confirms.any { it.startsWith("Install dependencies") })
        assertTrue(recording.confirms.any { it.startsWith("Run initial migrations") })
        assertTrue(recording.confirms.any { it.startsWith("Run database seeders") })
        assertTrue(Files.isRegularFile(dest.resolve("build.gradle.kts")))
        val text = out.toString()
        assertTrue(text.contains("Let's brew an app"))
        assertFalse(text.contains("Where should we create the app?"))
    }

    @Test
    fun konaNewWithNoArgsIsInteractive() {
        val dest = temp.resolve("zero-arg-app")
        val recording = RecordingPrompts(dest.toString() + "\n" + "\n".repeat(16))
        val (cmd, out) = command("", prompts = recording)
        assertEquals(0, KonaApplication(ConsoleIO(PrintStream(out), PrintStream(out), false), cmd).run(arrayOf("new")))
        assertTrue(recording.asked.any { it.contains("Where should we create") })
        assertTrue(recording.choices.contains("Language"))
        assertTrue(recording.choices.contains("What are we brewing?"))
        assertTrue(recording.choices.contains("CSS"))
        assertTrue(recording.choices.contains("Database"))
        assertTrue(recording.confirms.any { it.startsWith("Install dependencies") })
        assertTrue(Files.isRegularFile(dest.resolve("settings.gradle.kts")))
        assertFalse(out.toString().contains("Usage: kona new <name>"))
        assertFalse(out.toString().contains("Usage: kona new"))
    }

    @Test
    fun ciEnvDoesNotSkipPrompts() {
        val previous = System.getenv("CI")
        assertTrue(wantsInteraction(emptyMap()), "CI=$previous must not change skip rules")
        val dest = temp.resolve("from-ci")
        val recording = RecordingPrompts("\n".repeat(16))
        val (cmd, _) = command("", prompts = recording)
        assertEquals(0, cmd.run(listOf(dest.toString())))
        assertTrue(recording.choices.contains("Language"))
        assertTrue(recording.choices.contains("What are we brewing?"))
    }

    @Test
    fun unreadableStdinErrorsInsteadOfScaffolding() {
        val dest = temp.resolve("eof-app")
        val (cmd, _) = command("")
        val ex = assertThrows<KonaCliException> { cmd.run(listOf(dest.toString())) }
        assertEquals(STDIN_UNREADABLE, ex.message)
        assertFalse(Files.exists(dest))
        val boom =
            object : java.io.InputStream() {
                override fun read(): Int = throw java.io.IOException("closed")
            }
        val ioOut = ByteArrayOutputStream()
        val stream = PrintStream(ioOut)
        val failing =
            NewCommand(
                KonaVersions("0.0.0-test", "2.0.0"),
                ConsoleIO(stream, stream, false),
                InstallerPrompts(boom, stream, false),
            )
        val ex2 = assertThrows<KonaCliException> { failing.run(listOf(temp.resolve("io-app").toString())) }
        assertEquals(STDIN_UNREADABLE, ex2.message)
        assertFalse(Files.exists(temp.resolve("io-app")))
    }

    @Test
    fun newHelpPrintsVersionAndSkipRules() {
        val out = ByteArrayOutputStream()
        val stream = PrintStream(out)
        val io = ConsoleIO(stream, stream, color = false)
        val versions = KonaVersions("0.2.0", "2.2.0", "v0.1.1-9-gabcdef")
        assertEquals(0, NewCommand(versions, io).run(listOf("--help")))
        val text = out.toString()
        assertTrue(text.contains("kona 0.2.0 (v0.1.1-9-gabcdef)"))
        assertTrue(text.contains(NEW_USAGE))
        assertTrue(text.contains("--no-interaction"))
        assertTrue(text.contains("Directory set"))
        assertFalse(text.contains("kona new <name>"))
        assertEquals(0, NewCommand(versions, io).run(listOf("-h")))
        assertEquals(0, NewCommand(versions, io).run(listOf("--h")))
        printNewHelp(io)
        assertEquals("kona 0.2.0", KonaVersions("0.2.0", "2.2.0").display())
        assertEquals("kona 0.2.0", KonaVersions("0.2.0", "2.2.0", "").display())
        assertEquals("kona 0.2.0", KonaVersions("0.2.0", "2.2.0", "0.2.0").display())
        val loaded = KonaVersions.load()
        assertTrue(loaded.display().startsWith("kona ${loaded.kona}"))
        assertEquals("1.2.3", KonaVersions.load("org/konaframework/cli/kona-no-revision.properties").revision)
        assertEquals("1.2.3", KonaVersions.load("org/konaframework/cli/kona-blank-revision.properties").revision)
    }

    @Test
    fun interactiveDefaultsCremaTailwindAndPostCreate() {
        val app = temp.resolve("my-app")
        val seen = mutableListOf<List<String>>()
        val runner = GrindProcessRunner { command, dir ->
            seen += command
            assertEquals(app, dir)
            0
        }
        val (cmd, out) = command("\n".repeat(16), runner = runner)
        assertEquals(0, cmd.run(listOf(app.toString())))
        assertTrue(Files.readString(app.resolve(".env")).contains("APP_NAME=My App"))
        assertTrue(Files.isRegularFile(app.resolve("package.json")))
        assertTrue(Files.readString(app.resolve("src/main/resources/css/app.css")).contains("tailwindcss"))
        assertTrue(Files.readString(app.resolve("src/main/resources/views/welcome.crema.html")).contains("@vite"))
        val text = out.toString()
        assertTrue(text.contains("Let's brew an app"))
        assertTrue(text.contains("Ready to write"))
        assertTrue(text.contains("Application created successfully."))
        assertTrue(text.contains("Gradle wrapper ready."))
        assertTrue(text.contains("Pour a cup"))
        assertTrue(seen.any { it.any { part -> part.endsWith("gradlew") } })
        assertTrue(seen.any { it.contains("npm") })
        assertTrue(seen.any { it.contains("migrate") })
        assertTrue(seen.any { it.contains("db:seed") })
        assertFalse(text.contains("./grind migrate --seed"))
    }

    @Test
    fun interactiveAbortWritesNothing() {
        val app = temp.resolve("nope-app")
        val (cmd, out) = command("\n".repeat(8) + "n\n")
        assertEquals(1, cmd.run(listOf(app.toString())))
        assertFalse(Files.exists(app))
        assertTrue(out.toString().contains("Nothing was written."))
    }

    @Test
    fun interactiveAsksDirectoryWhenNameOmitted() {
        val dest = temp.resolve("brewed")
        val answers = dest.toString() + "\n" + "\n".repeat(16)
        val (cmd, _) = command(answers, runner = GrindProcessRunner { _, _ -> 1 })
        assertEquals(0, cmd.run(emptyList()))
        assertTrue(Files.isRegularFile(dest.resolve("settings.gradle.kts")))
    }

    @Test
    fun flagsSkipMatchingPrompts() {
        val app = temp.resolve("java-inertia")
        val (cmd, out) = command("this-is-not-read\n")
        assertEquals(
            0,
            cmd.run(
                listOf(
                    app.toString(),
                    "--lang=java",
                    "--kit=inertia",
                    "--frontend=react",
                    "--javascript",
                    "--css=bootstrap",
                    "--name=Hello Cafe",
                    "--database=sqlite",
                    "--no-install-deps",
                    "--no-migrate",
                    "--no-seed",
                ),
            ),
        )
        assertTrue(Files.readString(app.resolve(".env")).contains("APP_NAME=Hello Cafe"))
        assertTrue(Files.isRegularFile(app.resolve("src/main/java/app/http/middleware/HandleInertia.java")))
        assertTrue(Files.isRegularFile(app.resolve("src/main/resources/js/app.jsx")))
        assertTrue(Files.readString(app.resolve("package.json")).contains("@inertiajs/react"))
        assertTrue(Files.readString(app.resolve("package.json")).contains("bootstrap"))
        assertTrue(Files.readString(app.resolve("src/main/java/bootstrap/App.java")).contains("app.jsx"))
        assertFalse(out.toString().contains("What are we brewing?"))
        assertFalse(out.toString().contains("Inertia adapter"))
        assertFalse(out.toString().contains("Frontend language"))
        assertTrue(out.toString().contains("Frontend   JavaScript"))
        assertTrue(out.toString().contains("Ready to write"))
        assertTrue(out.toString().contains("npm install && npm run dev"))
        assertTrue(out.toString().contains("./grind migrate --seed"))
    }

    @Test
    fun apiKitSkipsCssAndFrontend() {
        val app = temp.resolve("api-only")
        val (cmd, _) = command("\n".repeat(12), runner = GrindProcessRunner { _, _ -> 0 })
        assertEquals(0, cmd.run(listOf(app.toString(), "--kit=api", "--no-install-deps", "--no-migrate", "--no-seed")))
        assertFalse(Files.exists(app.resolve("src/main/resources/views/welcome.crema.html")))
        assertFalse(Files.exists(app.resolve("package.json")))
    }

    @Test
    fun cssAndFrontendScaffoldsAreReal() {
        val cremaBs = temp.resolve("crema-bs")
        assertEquals(
            0,
            NewCommand().run(listOf(cremaBs.toString(), "--kit=web", "--css=bootstrap", "--no-interaction")),
        )
        assertTrue(Files.readString(cremaBs.resolve("package.json")).contains("bootstrap"))
        val cremaLogin = Files.readString(cremaBs.resolve("src/main/resources/views/login.crema.html"))
        assertTrue(cremaLogin.contains("form-control"))
        assertTrue(cremaLogin.contains("url('/branding/kona-logo.svg')"), cremaLogin)
        assertTrue(cremaLogin.contains("url('/login')"), cremaLogin)
        val cremaDash = Files.readString(cremaBs.resolve("src/main/resources/views/dashboard.crema.html"))
        assertTrue(cremaDash.contains("card-body"))
        assertTrue(cremaDash.contains("url('/')"), cremaDash)

        val brewTw = temp.resolve("brew-tw")
        assertEquals(
            0,
            NewCommand().run(listOf(brewTw.toString(), "--kit=brew", "--css=tailwind", "--no-interaction")),
        )
        val brewWelcome = Files.readString(brewTw.resolve("src/main/resources/views/welcome.crema.html"))
        assertTrue(brewWelcome.contains("<brew:counter"))
        assertTrue(brewWelcome.contains("@vite"))
        assertTrue(brewWelcome.contains("url('/branding/kona-logo.svg')"), brewWelcome)
        assertTrue(Files.readString(brewTw.resolve("src/main/resources/js/app.js")).contains("app.css"))

        val svelte = temp.resolve("svelte-app")
        assertEquals(
            0,
            NewCommand().run(
                listOf(svelte.toString(), "--kit=inertia", "--frontend=svelte", "--css=custom", "--no-interaction"),
            ),
        )
        assertTrue(Files.isRegularFile(svelte.resolve("src/main/resources/js/Pages/Welcome.svelte")))
        assertTrue(Files.readString(svelte.resolve("package.json")).contains("@inertiajs/svelte"))

        val vueTw = temp.resolve("vue-tw")
        assertEquals(
            0,
            NewCommand().run(
                listOf(vueTw.toString(), "--kit=inertia", "--frontend=vue", "--css=tailwind", "--no-interaction"),
            ),
        )
        assertTrue(Files.readString(vueTw.resolve("src/main/resources/js/Pages/Welcome.vue")).contains("bg-foam"))
        assertTrue(Files.readString(vueTw.resolve("package.json")).contains("tailwindcss"))

        val reactCustom = temp.resolve("react-custom")
        assertEquals(
            0,
            NewCommand().run(
                listOf(reactCustom.toString(), "--preset=inertia", "--frontend=react", "--css=custom", "--no-interaction"),
            ),
        )
        assertTrue(Files.readString(reactCustom.resolve("src/main/resources/js/Pages/Welcome.jsx")).contains("system-ui"))

        val vueBs = temp.resolve("vue-bs")
        assertEquals(
            0,
            NewCommand().run(
                listOf(vueBs.toString(), "--kit=inertia", "--frontend=vue", "--css=bootstrap", "--no-interaction"),
            ),
        )
        assertTrue(Files.readString(vueBs.resolve("src/main/resources/js/Pages/Welcome.vue")).contains("container py-5"))

        val reactTw = temp.resolve("react-tw")
        assertEquals(
            0,
            NewCommand().run(
                listOf(reactTw.toString(), "--kit=inertia", "--frontend=react", "--css=tailwind", "--no-interaction"),
            ),
        )
        assertTrue(Files.readString(reactTw.resolve("src/main/resources/js/Pages/Welcome.jsx")).contains("bg-foam"))

        val svelteTw = temp.resolve("svelte-tw")
        assertEquals(
            0,
            NewCommand().run(
                listOf(svelteTw.toString(), "--kit=inertia", "--frontend=svelte", "--css=tailwind", "--no-interaction"),
            ),
        )
        assertTrue(Files.readString(svelteTw.resolve("src/main/resources/js/Pages/Welcome.svelte")).contains("bg-foam"))

        val svelteBs = temp.resolve("svelte-bs")
        assertEquals(
            0,
            NewCommand().run(
                listOf(svelteBs.toString(), "--kit=inertia", "--frontend=svelte", "--css=bootstrap", "--no-interaction"),
            ),
        )
        assertTrue(Files.readString(svelteBs.resolve("src/main/resources/js/Pages/Welcome.svelte")).contains("display-6"))

        ScaffoldFrontends.apply(temp.resolve("api-skip").also { Files.createDirectories(it) }, "api", "tailwind", "vue")
        assertFalse(Files.exists(temp.resolve("api-skip/package.json")))
        val inertiaEmptyFe = temp.resolve("inertia-empty-fe").also { Files.createDirectories(it) }
        ScaffoldFrontends.apply(inertiaEmptyFe, "inertia", "custom", "")
        assertTrue(Files.isRegularFile(inertiaEmptyFe.resolve("src/main/resources/js/Pages/Welcome.vue")))
        assertEquals("src/main/resources/js/app.js", ScaffoldFrontends.viteEntry("vue"))
        assertEquals("src/main/resources/js/app.jsx", ScaffoldFrontends.viteEntry("react"))
        assertEquals("src/main/resources/js/app.js", ScaffoldFrontends.viteEntry(""))
        assertEquals("src/main/resources/js/app.ts", ScaffoldFrontends.viteEntry("vue", "ts"))
        assertEquals("src/main/resources/js/app.ts", ScaffoldFrontends.viteEntry("", "ts"))
        assertEquals("src/main/resources/js/app.tsx", ScaffoldFrontends.viteEntry("react", "ts"))
        assertFalse(Files.readString(vueTw.resolve("package.json")).contains("typescript"))
    }

    @Test
    fun postCreateCoversWindowsPnpmFailuresAndExceptions() {
        val app = temp.resolve("post")
        Files.createDirectories(app)
        Files.writeString(app.resolve("package.json"), "{}\n")
        Files.writeString(app.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'\n")
        val ioOut = ByteArrayOutputStream()
        val stream = PrintStream(ioOut)
        val io = ConsoleIO(stream, stream, color = false)
        val fail = NewCommand(
            io = io,
            runner = GrindProcessRunner { _, _ -> 7 },
            windows = true,
        )
        val db = DatabaseEngines.require("sqlite")
        fail.runPostCreate(
            app,
            NewPlan(app, "Post", "kotlin", "web", "custom", "", db, true, true, true),
        )
        val text = ioOut.toString()
        assertTrue(text.contains("Gradle dependencies skipped"))
        assertTrue(text.contains("Frontend install skipped"))
        assertTrue(text.contains("Migrations skipped"))
        assertTrue(text.contains("Seeders skipped"))
        assertEquals(listOf("pnpm", "install"), fail.jsInstallCommand(app))

        val okOut = ByteArrayOutputStream()
        val okStream = PrintStream(okOut)
        val winOk = NewCommand(
            io = ConsoleIO(okStream, okStream, color = false),
            runner = GrindProcessRunner { command, _ ->
                assertTrue(command[0].endsWith("gradlew.bat") || command[0].endsWith("grind.bat") || command[0] == "pnpm")
                0
            },
            windows = true,
        )
        winOk.runPostCreate(
            app,
            NewPlan(app, "Post", "kotlin", "web", "custom", "", db, true, true, true),
        )
        assertTrue(okOut.toString().contains("Gradle wrapper ready."))
        assertTrue(okOut.toString().contains("Frontend packages installed (pnpm)."))
        assertTrue(okOut.toString().contains("Migrations applied."))
        assertTrue(okOut.toString().contains("Database seeded."))

        val throwsCli = NewCommand(
            io = ConsoleIO(PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()), false),
            runner = GrindProcessRunner { _, _ -> throw KonaCliException("boom") },
        )
        assertEquals(-1, throwsCli.runQuiet(listOf("nope"), app))
        val throwsOther = NewCommand(
            io = ConsoleIO(PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()), false),
            runner = GrindProcessRunner { _, _ -> throw IllegalStateException("nope") },
        )
        assertEquals(-1, throwsOther.runQuiet(listOf("nope"), app))
    }

    @Test
    fun installWithoutPackageJsonSkipsNpm() {
        val app = temp.resolve("api-install")
        val seen = mutableListOf<List<String>>()
        val (cmd, out) = command(
            "",
            runner = GrindProcessRunner { command, _ ->
                seen += command
                0
            },
        )
        assertEquals(
            0,
            cmd.run(listOf(app.toString(), "--kit=api", "--install-deps", "--migrate", "--seed", "--no-interaction")),
        )
        assertTrue(seen.any { it.any { part -> part.endsWith("gradlew") } })
        assertFalse(seen.any { it.contains("npm") })
        assertFalse(out.toString().contains("npm install && npm run dev"))
        assertFalse(out.toString().contains("./grind migrate --seed"))
    }

    @Test
    fun appNameFlagAndTriStateEquals() {
        val app = temp.resolve("named")
        assertEquals(
            0,
            NewCommand().run(
                listOf(
                    app.toString(),
                    "--app-name=Cafe",
                    "--install-deps=false",
                    "--migrate=no",
                    "--seed=0",
                    "--no-interaction",
                ),
            ),
        )
        assertTrue(Files.readString(app.resolve(".env")).contains("APP_NAME=Cafe"))
    }

    @Test
    fun invalidFrontendWithInertia() {
        assertThrows<KonaCliException> {
            NewCommand().run(listOf(temp.resolve("bad-fe").toString(), "--kit=inertia", "--frontend=ember", "--no-interaction"))
        }
        assertThrows<KonaCliException> {
            NewCommand().run(listOf(temp.resolve("bad-css").toString(), "--css=bulma", "--no-interaction"))
        }
        assertEquals(
            0,
            NewCommand().run(
                listOf(temp.resolve("api-css-ignored").toString(), "--kit=api", "--css=tailwind", "--frontend=vue", "--no-interaction"),
            ),
        )
        assertFalse(Files.exists(temp.resolve("api-css-ignored/package.json")))
    }

    @Test
    fun interactiveInertiaFrontendPrompt() {
        val app = temp.resolve("in-fe")
        // language, adapter 3=Svelte, script default JavaScript, css, db, install/migrate/seed no, confirm
        val input = "\n3\n\n\n\nn\nn\nn\n\n"
        val (cmd, out) = command(input)
        assertEquals(0, cmd.run(listOf(app.toString(), "--kit=inertia", "--name=Spa")))
        assertTrue(Files.isRegularFile(app.resolve("src/main/resources/js/Pages/Welcome.svelte")))
        assertTrue(Files.isRegularFile(app.resolve("src/main/resources/js/app.js")))
        assertFalse(Files.exists(app.resolve("tsconfig.json")))
        assertTrue(out.toString().contains("Frontend language"))
        assertTrue(out.toString().contains("Frontend   JavaScript"))
        assertTrue(Files.readString(app.resolve(".env")).contains("APP_NAME=Spa"))
    }

    @Test
    fun interactiveInertiaTypescriptPrompt() {
        val app = temp.resolve("in-ts")
        // language default, script 2=TypeScript, confirm. Adapter, css, db, and post-steps are flagged.
        val input = "\n2\n\n"
        val (cmd, out) = command(input)
        assertEquals(
            0,
            cmd.run(
                listOf(
                    app.toString(),
                    "--kit=inertia",
                    "--frontend=vue",
                    "--css=tailwind",
                    "--name=Typed",
                    "--database=sqlite",
                    "--no-install-deps",
                    "--no-migrate",
                    "--no-seed",
                ),
            ),
        )
        val text = out.toString()
        assertTrue(text.contains("Frontend language"))
        assertFalse(text.contains("Inertia adapter"))
        assertTrue(text.contains("Frontend   TypeScript"))
        assertTrue(text.contains("TypeScript"))
        assertTrue(Files.readString(app.resolve("src/main/resources/js/app.ts")).contains("DefineComponent"))
        assertTrue(Files.readString(app.resolve("src/main/resources/js/app.ts")).contains("app.css"))
        assertFalse(Files.exists(app.resolve("src/main/resources/js/app.js")))
        assertTrue(Files.readString(app.resolve("src/main/resources/js/Pages/Welcome.vue")).contains("lang=\"ts\""))
        assertTrue(Files.readString(app.resolve("src/main/resources/js/Pages/Welcome.vue")).contains("withDefaults"))
        assertTrue(Files.readString(app.resolve("package.json")).contains("vue-tsc"))
        assertTrue(Files.readString(app.resolve("package.json")).contains("\"typecheck\": \"vue-tsc --noEmit\""))
        assertTrue(Files.readString(app.resolve("vite.config.js")).contains("app.ts"))
        assertTrue(Files.readString(app.resolve("vite.config.js")).contains("public/build"))
        assertTrue(Files.readString(app.resolve("tsconfig.json")).contains("env.d.ts"))
        assertTrue(Files.readString(app.resolve("env.d.ts")).contains("*.vue"))
        assertTrue(Files.readString(app.resolve("src/main/resources/views/welcome.crema.html")).contains("app.ts"))
        assertTrue(Files.readString(app.resolve("src/main/kotlin/bootstrap/App.kt")).contains("src/main/resources/js/app.ts"))
        assertTrue(Files.isRegularFile(app.resolve("public/branding/kona-logo.svg")))
        assertFalse(Files.exists(app.resolve("src/main/resources/public")))
    }

    @Test
    fun typescriptFlagWritesRealEntriesAndLastWins() {
        val vueBs = temp.resolve("vue-bs-ts")
        assertEquals(
            0,
            NewCommand().run(
                listOf(vueBs.toString(), "--kit=inertia", "--frontend=vue", "--css=bootstrap", "--typescript", "--no-interaction"),
            ),
        )
        assertTrue(Files.readString(vueBs.resolve("src/main/resources/js/Pages/Welcome.vue")).contains("container py-5"))
        assertTrue(Files.readString(vueBs.resolve("src/main/resources/js/Pages/Welcome.vue")).contains("lang=\"ts\""))
        assertTrue(Files.readString(vueBs.resolve("src/main/kotlin/bootstrap/App.kt")).contains("app.ts"))

        val reactCustom = temp.resolve("react-custom-ts")
        assertEquals(
            0,
            NewCommand().run(
                listOf(
                    reactCustom.toString(),
                    "--kit=inertia",
                    "--frontend=react",
                    "--css=custom",
                    "--js",
                    "--ts",
                    "--no-interaction",
                ),
            ),
        )
        val reactApp = Files.readString(reactCustom.resolve("src/main/resources/js/app.tsx"))
        assertTrue(reactApp.contains("Pages/**/*.tsx"))
        assertFalse(reactApp.contains("app.css"))
        assertTrue(Files.readString(reactCustom.resolve("src/main/resources/js/Pages/Welcome.tsx")).contains("WelcomeProps"))
        assertTrue(Files.readString(reactCustom.resolve("src/main/resources/js/Pages/Welcome.tsx")).contains("system-ui"))
        assertTrue(Files.readString(reactCustom.resolve("package.json")).contains("@types/react"))
        assertTrue(Files.readString(reactCustom.resolve("package.json")).contains("\"typecheck\": \"tsc --noEmit\""))
        assertTrue(Files.readString(reactCustom.resolve("vite-env.d.ts")).contains("vite/client"))
        assertTrue(Files.readString(reactCustom.resolve("tsconfig.json")).contains("react-jsx"))
        assertFalse(Files.exists(reactCustom.resolve("src/main/resources/js/app.js")))
        assertFalse(Files.exists(reactCustom.resolve("src/main/resources/js/app.jsx")))
        assertTrue(
            Files.readString(reactCustom.resolve("src/main/kotlin/bootstrap/App.kt")).contains("src/main/resources/js/app.tsx"),
        )

        val reactTw = temp.resolve("react-tw-ts")
        assertEquals(
            0,
            NewCommand().run(
                listOf(
                    reactTw.toString(),
                    "--lang=java",
                    "--kit=inertia",
                    "--frontend=react",
                    "--css=tailwind",
                    "--typescript=false",
                    "--javascript=false",
                    "--no-interaction",
                ),
            ),
        )
        assertTrue(Files.readString(reactTw.resolve("src/main/resources/js/Pages/Welcome.tsx")).contains("bg-foam"))
        assertTrue(Files.readString(reactTw.resolve("src/main/resources/js/app.tsx")).contains("app.css"))
        assertTrue(Files.readString(reactTw.resolve("src/main/resources/views/welcome.crema.html")).contains("app.tsx"))
        assertTrue(Files.readString(reactTw.resolve("src/main/java/bootstrap/App.java")).contains("src/main/resources/js/app.tsx"))

        val svelteBs = temp.resolve("svelte-bs-ts")
        assertEquals(
            0,
            NewCommand().run(
                listOf(svelteBs.toString(), "--kit=inertia", "--frontend=svelte", "--css=bootstrap", "--ts", "--no-interaction"),
            ),
        )
        val sveltePage = Files.readString(svelteBs.resolve("src/main/resources/js/Pages/Welcome.svelte"))
        assertTrue(sveltePage.contains("lang=\"ts\""))
        assertTrue(sveltePage.contains("display-6"))
        assertTrue(sveltePage.contains("title?: string"))
        assertTrue(Files.readString(svelteBs.resolve("src/main/resources/js/app.ts")).contains("app.css"))
        assertTrue(Files.readString(svelteBs.resolve("svelte.config.js")).contains("vitePreprocess"))
        assertTrue(Files.readString(svelteBs.resolve("tsconfig.json")).contains("svelte"))
        assertFalse(Files.exists(svelteBs.resolve("src/main/resources/js/app.js")))

        val svelteCustom = temp.resolve("svelte-custom-ts")
        assertEquals(
            0,
            NewCommand().run(
                listOf(
                    svelteCustom.toString(),
                    "--kit=inertia",
                    "--frontend=svelte",
                    "--css=custom",
                    "--typescript",
                    "--javascript",
                    "--no-interaction",
                ),
            ),
        )
        assertTrue(Files.isRegularFile(svelteCustom.resolve("src/main/resources/js/app.js")))
        assertFalse(Files.exists(svelteCustom.resolve("tsconfig.json")))
        assertFalse(Files.readString(svelteCustom.resolve("src/main/resources/js/Pages/Welcome.svelte")).contains("lang=\"ts\""))

        val api = temp.resolve("api-ignores-ts")
        assertEquals(
            0,
            NewCommand().run(listOf(api.toString(), "--kit=api", "--typescript", "--no-interaction")),
        )
        assertFalse(Files.exists(api.resolve("tsconfig.json")))
        assertFalse(Files.exists(api.resolve("package.json")))

        val brew = temp.resolve("brew-ignores-ts")
        assertEquals(
            0,
            NewCommand().run(listOf(brew.toString(), "--kit=brew", "--css=tailwind", "--ts", "--no-interaction")),
        )
        assertTrue(Files.readString(brew.resolve("src/main/resources/js/app.js")).contains("app.css"))
        assertFalse(Files.exists(brew.resolve("tsconfig.json")))
        assertTrue(Files.readString(brew.resolve("src/main/resources/views/welcome.crema.html")).contains("app.js"))

        val web = temp.resolve("web-ignores-ts")
        assertEquals(
            0,
            NewCommand().run(listOf(web.toString(), "--kit=web", "--css=custom", "--typescript", "--no-interaction")),
        )
        assertFalse(Files.exists(web.resolve("package.json")))
        assertFalse(Files.exists(web.resolve("tsconfig.json")))

        val seeded = temp.resolve("seeded-react").also { Files.createDirectories(it.resolve("src/main/resources/js")) }
        Files.writeString(seeded.resolve("src/main/resources/js/app.js"), "old")
        Files.writeString(seeded.resolve("src/main/resources/js/app.jsx"), "old")
        ScaffoldFrontends.apply(seeded, "inertia", "custom", "react", "ts")
        assertFalse(Files.exists(seeded.resolve("src/main/resources/js/app.js")))
        assertFalse(Files.exists(seeded.resolve("src/main/resources/js/app.jsx")))
        assertTrue(Files.isRegularFile(seeded.resolve("src/main/resources/js/app.tsx")))

        val fresh = temp.resolve("fresh-vue-ts").also { Files.createDirectories(it) }
        ScaffoldFrontends.apply(fresh, "inertia", "tailwind", "vue", "ts")
        assertTrue(Files.isRegularFile(fresh.resolve("src/main/resources/js/app.ts")))
        assertFalse(Files.exists(fresh.resolve("src/main/resources/js/app.js")))

        val vueCustom = temp.resolve("vue-custom-direct").also { Files.createDirectories(it) }
        ScaffoldFrontends.apply(vueCustom, "inertia", "custom", "vue", "ts")
        assertFalse(Files.readString(vueCustom.resolve("src/main/resources/js/app.ts")).contains("app.css"))
        assertTrue(Files.readString(vueCustom.resolve("src/main/resources/js/Pages/Welcome.vue")).contains("lang=\"ts\""))

        val svelteCustomDirect = temp.resolve("svelte-custom-direct").also { Files.createDirectories(it) }
        ScaffoldFrontends.apply(svelteCustomDirect, "inertia", "custom", "svelte", "ts")
        assertFalse(Files.readString(svelteCustomDirect.resolve("src/main/resources/js/app.ts")).contains("app.css"))
        assertTrue(Files.readString(svelteCustomDirect.resolve("src/main/resources/js/Pages/Welcome.svelte")).contains("lang=\"ts\""))

        val svelteTw = temp.resolve("svelte-tw-direct").also { Files.createDirectories(it) }
        ScaffoldFrontends.apply(svelteTw, "inertia", "tailwind", "svelte", "ts")
        assertTrue(Files.readString(svelteTw.resolve("src/main/resources/js/Pages/Welcome.svelte")).contains("bg-foam"))
        assertTrue(Files.readString(svelteTw.resolve("src/main/resources/js/app.ts")).contains("app.css"))

        val reactBs = temp.resolve("react-bs-direct").also { Files.createDirectories(it) }
        ScaffoldFrontends.apply(reactBs, "inertia", "bootstrap", "react", "ts")
        assertTrue(Files.readString(reactBs.resolve("src/main/resources/js/Pages/Welcome.tsx")).contains("display-6"))
        assertTrue(Files.readString(reactBs.resolve("src/main/resources/js/app.tsx")).contains("app.css"))
    }

    @Test
    fun interactiveChoicePicksJavaBrewBootstrap() {
        val app = temp.resolve("jb")
        // app name default, language 2=Java, stack 3=Brew, css 2=Bootstrap, db default, install/migrate/seed no, confirm yes
        val input = "\n2\n3\n2\n\nn\nn\nn\n\n"
        val (cmd, _) = command(input)
        assertEquals(0, cmd.run(listOf(app.toString())))
        assertTrue(Files.isRegularFile(app.resolve("src/main/java/app/brew/Counter.java")))
        assertTrue(Files.readString(app.resolve("package.json")).contains("bootstrap"))
        assertTrue(Files.readString(app.resolve("src/main/resources/views/welcome.crema.html")).contains("<brew:counter"))
    }
}

private class RecordingPrompts(input: String) :
    InstallerPrompts(ByteArrayInputStream(input.toByteArray()), PrintStream(ByteArrayOutputStream()), false) {
    val asked = mutableListOf<String>()
    val choices = mutableListOf<String>()
    val confirms = mutableListOf<String>()

    override fun ask(question: String, default: String?): String {
        asked += question
        return super.ask(question, default)
    }

    override fun choice(question: String, options: List<String>, defaultIndex: Int): String {
        choices += question
        return super.choice(question, options, defaultIndex)
    }

    override fun confirm(question: String, default: Boolean): Boolean {
        confirms += question
        return super.confirm(question, default)
    }
}
