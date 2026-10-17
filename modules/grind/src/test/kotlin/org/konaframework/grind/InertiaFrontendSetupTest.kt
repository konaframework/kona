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

class InertiaFrontendSetupTest {
    @TempDir
    lateinit var root: Path

    private fun setup(
        input: String = "",
        console: Boolean = false,
    ): Pair<InertiaFrontendSetup, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), PrintStream(err), Verbosity.NORMAL, color = false)
        val prompts = Prompts(ByteArrayInputStream(input.toByteArray()), PrintStream(out), color = false)
        return InertiaFrontendSetup(root, io, prompts, consoleAvailable = console) to out
    }

    @Test
    fun resolveStackFromFlag() {
        val (s, _) = setup()
        assertEquals(InertiaFrontendSetup.Stack.REACT, s.resolveStack("react", noInteractive = true))
        assertEquals(InertiaFrontendSetup.Stack.NONE, s.resolveStack("none", noInteractive = true))
        assertThrows(IllegalStateException::class.java) {
            s.resolveStack("angular", noInteractive = true)
        }
    }

    @Test
    fun nonInteractiveDefaultsToVue() {
        val (s, _) = setup(console = false)
        assertEquals(InertiaFrontendSetup.Stack.VUE, s.resolveStack(null, noInteractive = false))
    }

    @Test
    fun interactiveChoiceSelectsReact() {
        val (s, _) = setup(input = "2\n", console = true)
        assertEquals(InertiaFrontendSetup.Stack.REACT, s.resolveStack(null, noInteractive = false))
    }

    @Test
    fun writeVueScaffold() {
        val (s, _) = setup()
        val files = s.writeScaffold(InertiaFrontendSetup.Stack.VUE)
        assertTrue(files.contains("package.json"))
        assertTrue(Files.readString(root.resolve("package.json")).contains("@inertiajs/vue3"))
        assertTrue(Files.readString(root.resolve("vite.config.js")).contains("plugin-vue"))
        assertTrue(Files.isRegularFile(root.resolve("src/main/resources/js/app.js")))
        assertTrue(Files.isRegularFile(root.resolve("src/main/resources/js/Pages/Welcome.vue")))
        assertTrue(Files.isRegularFile(root.resolve("src/main/resources/views/app.crema.html")))
    }

    @Test
    fun writeReactAndSvelteScaffolds() {
        val (s, _) = setup()
        s.writeScaffold(InertiaFrontendSetup.Stack.REACT)
        assertTrue(Files.readString(root.resolve("package.json")).contains("@inertiajs/react"))
        assertTrue(Files.isRegularFile(root.resolve("src/main/resources/js/app.jsx")))
        assertTrue(Files.isRegularFile(root.resolve("src/main/resources/js/Pages/Welcome.jsx")))

        // second stack into same root overwrites package.json
        s.writeScaffold(InertiaFrontendSetup.Stack.SVELTE)
        assertTrue(Files.readString(root.resolve("package.json")).contains("@inertiajs/svelte"))
        assertTrue(Files.isRegularFile(root.resolve("src/main/resources/js/Pages/Welcome.svelte")))
    }

    @Test
    fun doesNotOverwriteExistingRootView() {
        val relative = "src/main/resources/views/app.crema.html"
        Files.createDirectories(root.resolve(relative).parent)
        Files.writeString(root.resolve(relative), "KEEP\n")
        val (s, _) = setup()
        val files = s.writeScaffold(InertiaFrontendSetup.Stack.VUE)
        assertFalse(files.contains(relative))
        assertEquals("KEEP\n", Files.readString(root.resolve(relative)))
    }

    @Test
    fun runNoneSkipsFiles() {
        val (s, out) = setup()
        assertEquals(InertiaFrontendSetup.Stack.NONE, s.run(stackFlag = "none"))
        assertFalse(Files.exists(root.resolve("package.json")))
        assertTrue(out.toString().contains("inertiajs.com"))
    }

    @Test
    fun runVueWritesAndMentionsNpm() {
        val (s, out) = setup()
        assertEquals(InertiaFrontendSetup.Stack.VUE, s.run(stackFlag = "vue", noInteractive = true))
        assertTrue(Files.exists(root.resolve("package.json")))
        assertTrue(out.toString().contains("npm install"))
    }

    @Test
    fun runReactWarnsAboutViteEntries() {
        val (s, out) = setup()
        s.run(stackFlag = "react", noInteractive = true)
        assertTrue(out.toString().contains("app.jsx"))
    }

    @Test
    fun stackFromId() {
        assertEquals(InertiaFrontendSetup.Stack.SVELTE, InertiaFrontendSetup.Stack.fromId("Svelte"))
        assertEquals(null, InertiaFrontendSetup.Stack.fromId("nope"))
    }

    @Test
    fun writeScaffoldRejectsNone() {
        val (s, _) = setup()
        assertThrows(IllegalArgumentException::class.java) {
            s.writeScaffold(InertiaFrontendSetup.Stack.NONE)
        }
    }

    @Test
    fun secondaryConstructorUsesDefaults() {
        val out = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), PrintStream(out), Verbosity.NORMAL, color = false)
        val s = InertiaFrontendSetup(root, io)
        assertEquals(InertiaFrontendSetup.Stack.VUE, s.run(stackFlag = "vue"))
    }

    @Test
    fun noInteractiveWinsEvenWhenConsoleAvailable() {
        val (s, _) = setup(console = true)
        assertEquals(InertiaFrontendSetup.Stack.VUE, s.resolveStack(null, noInteractive = true))
    }

    @Test
    fun runDefaultArgsNonInteractive() {
        val (s, _) = setup(console = false)
        assertEquals(InertiaFrontendSetup.Stack.VUE, s.run())
    }
}
