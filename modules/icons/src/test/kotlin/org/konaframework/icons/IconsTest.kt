package org.konaframework.icons

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class IconsTest {
    @AfterEach
    fun reset() {
        Icons.reset()
    }

    @Test
    fun heroiconsResolveByPrefixAndStyle() {
        val outline = Icons.svg("heroicon-o-home")
        assertTrue(outline.toHtml().contains("viewBox=\"0 0 24 24\""))
        assertTrue(outline.toHtml().contains("stroke=\"currentColor\""))
        assertEquals("heroicon-o-home", outline.name())
        assertTrue(outline.contents().contains("<svg"))

        val solid = Icons.svg("heroicon-s-check")
        assertTrue(solid.toHtml().contains("fill=\"currentColor\""))

        val mini = Icons.svg("heroicon-m-bell")
        assertTrue(mini.toHtml().contains("viewBox=\"0 0 20 20\"") || mini.toHtml().contains("viewBox=\"0 0 20 20\"".replace(" ", " ")))
        assertTrue(mini.toHtml().contains("20"))

        assertTrue(Icons.has("heroicon-o-user"))
        assertFalse(Icons.has("heroicon-o-definitely-missing-icon-xyz"))
        assertEquals("heroicons", Icons.defaultSet())
        assertTrue(Icons.all().containsKey("heroicons"))
    }

    @Test
    fun defaultSetResolvesBareIconFileName() {
        val html = Icons.svg("o-home").className("w-5").toHtml()
        assertTrue(html.contains("class=\"w-5\"") || html.contains("w-5"))
        assertTrue(html.contains("o-home") || html.contains("viewBox"))
    }

    @Test
    fun attributesMergeClassAndEscape() {
        val html = Icons.svg("heroicon-o-home", "ff-icon", mapOf("data-x" to "a&b\"c"))
            .attr("aria-hidden", "true")
            .attr("class", "more")
            .className("extra")
            .attributes(mapOf("role" to "img"))
            .toHtml()
        assertTrue(html.contains("ff-icon"))
        assertTrue(html.contains("more"))
        assertTrue(html.contains("extra"))
        assertTrue(html.contains("data-x=\"a&amp;b&quot;c\""))
        assertTrue(html.contains("role=\"img\""))
        assertEquals(
            html,
            Icons.svg("heroicon-o-home", "ff-icon", mapOf("data-x" to "a&b\"c"))
                .attr("aria-hidden", "true")
                .attr("Class", "more")
                .className("extra")
                .attributes(mapOf("role" to "img"))
                .toString(),
        )
    }

    @Test
    fun classpathCustomSetWithFallback() {
        Icons.add(
            "custom",
            IconSetOptions(
                prefix = "custom",
                classpath = "icons/custom",
                className = "icon",
                attributes = mapOf("data-set" to "custom"),
                fallback = "star",
            ),
        )
        Icons.add(
            "custom-blank-fallback",
            IconSetOptions(
                prefix = "cbf",
                classpath = "icons/custom",
                fallback = "   ",
            ),
        )
        Icons.setDefault("custom")
        val star = Icons.svg("custom-star").toHtml()
        assertTrue(star.contains("class=\"base icon\"") || star.contains("icon"))
        assertTrue(star.contains("data-set=\"custom\""))
        assertTrue(Icons.svg("custom-missing").toHtml().contains("star") || Icons.svg("custom-missing").contents().contains("M12 2"))
        assertThrows(CannotRegisterIconSetException::class.java) {
            Icons.add("other", IconSetOptions(prefix = "custom", classpath = "icons/custom"))
        }
        assertThrows(CannotRegisterIconSetException::class.java) {
            Icons.add("empty", IconSetOptions(prefix = "empty"))
        }
        assertThrows(CannotRegisterIconSetException::class.java) {
            Icons.add("blank", IconSetOptions(prefix = "  ", classpath = "icons/custom"))
        }
        assertThrows(CannotRegisterIconSetException::class.java) {
            Icons.add("bad-path", IconSetOptions(prefix = "badpath", path = Files.createTempDirectory("kona-icons").resolve("missing")))
        }
    }

    @Test
    fun filesystemSetAndGlobalFallback() {
        val dir = Files.createTempDirectory("kona-icons-fs")
        Files.writeString(
            dir.resolve("dot.svg"),
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8" class='mark'><circle cx="4" cy="4" r="3"/></svg>""",
        )
        Icons.add("fs", IconSetOptions(prefix = "fs", path = dir, paths = listOf(dir)))
        val html = Icons.svg("fs-dot").className("extra").toHtml()
        assertTrue(html.contains("mark"))
        assertTrue(html.contains("extra"))

        Icons.setFallback("fs-dot")
        assertEquals("fs-dot", Icons.fallback())
        assertTrue(Icons.svg("heroicon-o-no-such-icon-zzz").toHtml().contains("circle"))
        Icons.setFallback("heroicon-o-no-such-icon-zzz")
        assertThrows(IconNotFoundException::class.java) { Icons.svg("heroicon-o-no-such-icon-zzz") }
        Icons.setFallback("  ")
        assertEquals(null, Icons.fallback())
        Icons.setFallback("")
        assertEquals(null, Icons.fallback())
        Icons.setFallback(null)
        assertThrows(IconNotFoundException::class.java) { Icons.svg("heroicon-o-no-such-icon-zzz") }
        assertThrows(IconNotFoundException::class.java) { Icons.svg("   ") }
        assertThrows(IconNotFoundException::class.java) { Icons.svg("heroicon") }
        assertThrows(CannotRegisterIconSetException::class.java) { Icons.setDefault("missing-set") }
        assertTrue(Icons.svg("fs-dot.svg").toHtml().contains("circle"))
        Icons.add("rootish", IconSetOptions(prefix = "rootish", path = Path.of("/"), fallback = "nope"))
        assertThrows(IconNotFoundException::class.java) { Icons.svg("rootish-missing") }
    }

    @Test
    fun brokenSvgAndMergeEdgeCases() {
        Icons.add("custom", IconSetOptions(prefix = "custom", classpath = "icons/custom"))
        assertThrows(IconNotFoundException::class.java) { Icons.svg("custom-broken").toHtml() }
        assertThrows(IconNotFoundException::class.java) {
            mergeSvgAttributes("not-svg", emptyMap())
        }
        assertThrows(IconNotFoundException::class.java) {
            mergeSvgAttributes("<svg", emptyMap())
        }
        val emptyAttrs = mergeSvgAttributes("<svg></svg>", emptyMap())
        assertEquals("<svg></svg>", emptyAttrs)
        assertTrue(escapeHtmlAttr("a<b>c").contains("&lt;"))
        assertTrue(escapeHtmlAttr("a>b").contains("&gt;"))
        val parsed = parseSvgOpenAttributes("""<svg foo='bar' xmlns="http://www.w3.org/2000/svg">""")
        assertEquals("bar", parsed["foo"])
        assertEquals("http://www.w3.org/2000/svg", parsed["xmlns"])
    }

    @Test
    fun ensureDefaultsIdempotentAndReAddSamePrefix() {
        Icons.ensureDefaults()
        Icons.ensureDefaults()
        Icons.add(
            "heroicons",
            IconSetOptions(prefix = "heroicon", classpath = "org/konaframework/icons/heroicons"),
        )
        assertNotNull(Icons.svg("heroicon-o-home"))
        Icons.svg("o-bell", "")
        Icons.svg("heroicon-o-home", "", emptyMap())
        Icons.reset()
        Icons.add(
            "heroicons",
            IconSetOptions(prefix = "heroicon", classpath = "org/konaframework/icons/heroicons"),
        )
        Icons.ensureDefaults() // already registered via add
        assertNotNull(Icons.svg("heroicon-o-home"))
    }
}
