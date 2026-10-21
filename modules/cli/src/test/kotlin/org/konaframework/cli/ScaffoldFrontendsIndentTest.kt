package org.konaframework.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.support.json.Json
import org.konaframework.support.json.PrettyJson
import java.nio.file.Files
import java.nio.file.Path

class ScaffoldFrontendsIndentTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun packageJsonAndViteAreCanonical() {
        val vue = temp.resolve("vue").also { Files.createDirectories(it) }
        ScaffoldFrontends.apply(vue, "inertia", "tailwind", "vue")
        assertPrettyJson(vue.resolve("package.json"))
        assertViteLayout(Files.readString(vue.resolve("vite.config.js")))
        assertTrue(Files.readString(vue.resolve("vite.config.js")).contains("import tailwindcss"))

        val cssOnly = temp.resolve("css").also { Files.createDirectories(it) }
        ScaffoldFrontends.writeViteCss(cssOnly, "bootstrap")
        assertPrettyJson(cssOnly.resolve("package.json"))
        val cssVite = Files.readString(cssOnly.resolve("vite.config.js"))
        assertViteLayout(cssVite)
        assertFalse(cssVite.contains("plugin-vue"))
        assertTrue(Files.readString(cssOnly.resolve("package.json")).contains("bootstrap"))
    }

    @Test
    fun typescriptTsconfigIsPrettyAndBrewHtmlStaysNested() {
        val react = temp.resolve("react-ts").also { Files.createDirectories(it) }
        ScaffoldFrontends.apply(react, "inertia", "custom", "react", "ts")
        assertPrettyJson(react.resolve("tsconfig.json"))
        assertTrue(Files.readString(react.resolve("tsconfig.json")).contains("react-jsx"))

        val brewTw = temp.resolve("brew-tw").also { Files.createDirectories(it) }
        ScaffoldFrontends.writeCremaViews(brewTw, "tailwind", brew = true)
        val welcomeTw = Files.readString(brewTw.resolve("src/main/resources/views/welcome.crema.html"))
        assertFalse(welcomeTw.lines().any { it.startsWith("<section") })
        assertTrue(welcomeTw.contains("      <section class=\"brew-card"))
        assertFalse(welcomeTw.contains("__BREW__"))

        val brewBs = temp.resolve("brew-bs").also { Files.createDirectories(it) }
        ScaffoldFrontends.writeCremaViews(brewBs, "bootstrap", brew = true)
        val welcomeBs = Files.readString(brewBs.resolve("src/main/resources/views/welcome.crema.html"))
        assertFalse(welcomeBs.lines().any { it.startsWith("<section") })
        assertTrue(welcomeBs.contains("<section class=\"brew-card"))

        val web = temp.resolve("web").also { Files.createDirectories(it) }
        ScaffoldFrontends.writeCremaViews(web, "tailwind", brew = false)
        assertFalse(Files.readString(web.resolve("src/main/resources/views/welcome.crema.html")).contains("brew-card"))
    }

    private fun assertPrettyJson(path: Path) {
        val text = Files.readString(path)
        val parsed = Json.mapper.readValue(text, Any::class.java)
        assertEquals(PrettyJson.stringify(parsed), text)
        assertFalse(text.contains("\" : \""))
    }

    private fun assertViteLayout(js: String) {
        assertTrue(js.startsWith("import "))
        assertTrue(js.contains("\nfunction konaHotFile()"))
        assertTrue(js.contains("\nexport default defineConfig"))
        assertFalse(js.contains("            import "))
        assertFalse(js.contains("            function konaHotFile"))
        js.lines().filter { it.startsWith("import ") || it.startsWith("function ") || it.startsWith("export ") }
            .forEach { line ->
                assertEquals(0, line.takeWhile { ch -> ch == ' ' }.length, line)
            }
    }
}
