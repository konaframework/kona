package org.konaframework.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.Panel
import org.konaframework.firefly.Resource
import org.konaframework.firefly.widgets.Page
import org.konaframework.firefly.widgets.Widget
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Path
import java.util.jar.JarFile

class FireflyCatalogTest {
    @Test
    fun catalogMatchesShippedTypes() {
        val generated = reflectShipped()
        val loaded = FireflyCatalog.types()
        assertEquals(generated.map { it.name }, loaded.map { it.name })
        for (type in generated) {
            val match = loaded.first { it.name == type.name }
            assertEquals(type.packageName, match.packageName)
            assertEquals(type.kind, match.kind)
            assertEquals(type.methods, match.methods)
        }
        assertTrue(loaded.any { it.name == "Panel" && it.kind == "panel" })
        assertTrue(loaded.any { it.name == "Resource" && it.kind == "resource" })
        assertTrue(loaded.any { it.name == "Page" && it.kind == "page" })
        assertTrue(loaded.any { it.name == "ManagePage" && it.kind == "page" })
        assertTrue(loaded.any { it.name == "ChartWidget" && it.kind == "widget" })
        assertTrue(loaded.any { it.name == "Dashboard" && it.kind == "page" })
    }

    @Test
    fun readRejectsEmptyShapes() {
        assertTrue(FireflyCatalog.read(null).isEmpty())
        assertTrue(FireflyCatalog.read(ByteArrayInputStream("{}".toByteArray())).isEmpty())
        assertTrue(FireflyCatalog.read(ByteArrayInputStream("{\"types\":{}}".toByteArray())).isEmpty())
        val skipped =
            FireflyCatalog.read(
                ByteArrayInputStream(
                    "{\"types\":[{\"name\":\"\"},{\"name\":\"Bare\"},{\"name\":\"Note\",\"methods\":{}},{\"name\":\"Desk\",\"methods\":[\"\",\"id\"]}]}".toByteArray(),
                ),
            )
        assertEquals(listOf("Bare", "Note", "Desk"), skipped.map { it.name })
        assertTrue(skipped[0].methods.isEmpty())
        assertTrue(skipped[1].methods.isEmpty())
        assertEquals(listOf("id"), skipped[2].methods)
    }

    @Test
    fun completesTypesAndMethods() {
        assertTrue(FireflyCatalog.completions("Panel", "crema", 0, 5).isEmpty())
        val types = FireflyCatalog.completions("Pan", "kotlin", 0, 3)
        assertTrue(types.any { it.label == "Panel" })
        assertTrue(FireflyCatalog.completions("zzz", "java", 0, 3).isEmpty())
        assertTrue(FireflyCatalog.completions("", "kotlinscript", 0, 0).isEmpty())
        val methods = FireflyCatalog.completions("Panel().pa", "kotlin", 0, "Panel().pa".length)
        assertTrue(methods.any { it.label == "path" })
        assertTrue(methods.all { it.detail.contains("Panel.") })
        val all = FireflyCatalog.completions("ChartWidget.", "java", 0, "ChartWidget.".length)
        assertTrue(all.any { it.label == "engine" })
        assertTrue(FireflyCatalog.completions("ChartWidget.zzz", "java", 0, "ChartWidget.zzz".length).isEmpty())
        assertTrue(FireflyCatalog.completions("Missing.", "kotlin", 0, "Missing.".length).isEmpty())
        assertTrue(FireflyCatalog.completions("Panel).", "kotlin", 0, "Panel).".length).isEmpty())
        assertTrue(FireflyCatalog.completions("Panel((x)).", "java", 0, "Panel((x)).".length).any { it.label == "id" })
        assertTrue(FireflyCatalog.completions(" .", "kotlin", 0, 2).isEmpty())
        assertTrue(FireflyCatalog.completions("{\tPanel\n ", "java", 0, 1).isEmpty())
        val spaced = FireflyCatalog.completions("Panel()\t\n .", "kotlin", 0, "Panel()\t\n .".length)
        assertTrue(spaced.any { it.label == "id" })
        assertTrue(FireflyCatalog.completions("Panel(x).", "java", 0, "Panel(x).".length).any { it.label == "id" })
        assertTrue(FireflyCatalog.completions("{Panel", "kotlin", 0, "{Panel".length).any { it.label == "Panel" })
        assertTrue(FireflyCatalog.completions(":Panel", "kotlin", 0, ":Panel".length).any { it.label == "Panel" })
        assertTrue(FireflyCatalog.completions("@A1_b", "java", 0, "@A1_b".length).isEmpty())
    }

    @Test
    fun hoverNamesTheTypeOrMethod() {
        assertNull(FireflyCatalog.hover("Panel", "", 0, 0))
        assertNull(FireflyCatalog.hover("", "kotlin", 0, 0))
        assertNull(FireflyCatalog.hover("Panel.", "kotlin", 0, 6))
        assertEquals("org.konaframework.firefly.Panel", FireflyCatalog.hover("Panel x", "kotlin", 0, 0))
        assertEquals(
            "org.konaframework.firefly.Panel.path()",
            FireflyCatalog.hover("Panel().path", "java", 0, "Panel().pa".length),
        )
        assertNull(FireflyCatalog.hover("Panel().missing", "kotlin", 0, "Panel().mi".length))
        assertNull(FireflyCatalog.hover("Missing.nope", "kotlin", 0, "Missing.no".length))
        assertNull(FireflyCatalog.hover("other", "kotlinscript", 0, 1))
        assertEquals(
            "org.konaframework.firefly.widgets.ChartWidget",
            FireflyCatalog.hover("x ChartWidget", "java", 0, 4),
        )
    }

    private fun reflectShipped(): List<FireflyCatalog.TypeRecord> {
        val loader = Panel::class.java.classLoader
        val roots = listOf("org.konaframework.firefly", "org.konaframework.firefly.widgets")
        val found = LinkedHashMap<String, Class<*>>()
        for (root in roots) {
            val prefix = root.replace('.', '/') + "/"
            val resources = loader.getResources(root.replace('.', '/'))
            while (resources.hasMoreElements()) {
                val url = resources.nextElement()
                if (url.protocol != "jar") {
                    continue
                }
                val jarPath = Path.of(URI.create(url.path.substringBefore("!")))
                JarFile(jarPath.toFile()).use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (!name.startsWith(prefix) || name.contains('$') || !name.endsWith(".class")) {
                            continue
                        }
                        val rest = name.removePrefix(prefix)
                        if (rest.contains('/')) {
                            continue
                        }
                        val className = root + "." + rest.removeSuffix(".class")
                        val cls = Class.forName(className, false, loader)
                        if (wanted(cls)) {
                            found[cls.name] = cls
                        }
                    }
                }
            }
        }
        return found.values.sortedBy { it.simpleName }.map { record(it) }
    }

    private fun wanted(cls: Class<*>): Boolean {
        if (!java.lang.reflect.Modifier.isPublic(cls.modifiers)) {
            return false
        }
        if (cls.isInterface || cls.isEnum || cls.isAnnotation) {
            return false
        }
        if (Panel::class.java.isAssignableFrom(cls)) {
            return true
        }
        if (Resource::class.java.isAssignableFrom(cls)) {
            return true
        }
        if (Page::class.java.isAssignableFrom(cls)) {
            return true
        }
        return Widget::class.java.isAssignableFrom(cls)
    }

    private fun record(cls: Class<*>): FireflyCatalog.TypeRecord {
        val kind =
            if (Panel::class.java.isAssignableFrom(cls)) {
                "panel"
            } else if (Resource::class.java.isAssignableFrom(cls)) {
                "resource"
            } else if (Page::class.java.isAssignableFrom(cls)) {
                "page"
            } else {
                "widget"
            }
        return FireflyCatalog.TypeRecord(cls.simpleName, cls.packageName, kind, methods(cls))
    }

    private fun methods(cls: Class<*>): List<String> {
        val names = LinkedHashSet<String>()
        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            for (method in current.declaredMethods) {
                if (method.isSynthetic || java.lang.reflect.Modifier.isStatic(method.modifiers)) {
                    continue
                }
                if (!java.lang.reflect.Modifier.isPublic(method.modifiers)) {
                    continue
                }
                if (method.name.contains('$')) {
                    continue
                }
                names.add(method.name)
            }
            current = current.superclass
        }
        return names.sorted()
    }
}
