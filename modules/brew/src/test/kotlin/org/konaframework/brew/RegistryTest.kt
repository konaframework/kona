package org.konaframework.brew

import org.konaframework.brew.fixtures.Counter
import org.konaframework.brew.fixtures.admin_area.Named
import org.konaframework.brew.fixtures.admin_area.UserList
import org.konaframework.core.Container
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class RegistryTest {
    @Test
    fun `names derive from classes`() {
        assertEquals("counter", ComponentRegistry.nameFor(Counter::class.java, null))
        assertEquals("admin-area.user-list", ComponentRegistry.nameFor(UserList::class.java, "org.konaframework.brew.fixtures"))
        assertEquals("user-list", ComponentRegistry.nameFor(UserList::class.java, "org.konaframework.brew.fixtures.admin_area"))
        assertEquals("user-list", ComponentRegistry.nameFor(UserList::class.java, "other.pkg"))
        assertEquals("admin.custom", ComponentRegistry.nameFor(Named::class.java, null))
        assertEquals("show-html-posts", ComponentRegistry.kebab("ShowHTMLPosts"))
        assertEquals("CreatePost", ComponentRegistry.studly("create-post"))
        assertEquals("CreatePost", ComponentRegistry.studly("create__post"))
    }

    @Test
    fun `explicit registration, factories and suppliers`() {
        val registry = ComponentRegistry()
            .component(Counter::class.java)
            .component("sup", Supplier { Counter() })
            .component("fac") { Counter() }
        assertTrue(registry.has("counter"))
        assertEquals(setOf("counter", "sup", "fac"), registry.names())
        assertEquals("counter", registry.nameOf(Counter::class.java))
        assertEquals("user-list", registry.nameOf(UserList::class.java))
        assertSame(Counter::class.java, registry.classOf("counter"))
        assertNull(registry.classOf("fac"))
        assertTrue(registry.create("sup") is Counter)
        assertTrue(registry.create("fac") is Counter)
        assertThrows<ComponentNotFoundException> { registry.create("missing") }
        assertFalse(registry.has("missing"))
    }

    @Test
    fun `locations and namespaces resolve by convention`() {
        val registry = ComponentRegistry().addLocation("org.konaframework.brew.fixtures").addLocation("org.konaframework.brew.fixtures")
        assertSame(Counter::class.java, registry.classOf("counter"))
        assertSame(UserList::class.java, registry.classOf("admin-area.user-list"))
        assertNull(registry.classOf("kitchen-sink"))
        assertNull(registry.classOf("kitchen-sink"))
        assertNull(registry.classOf("admin-area.not-a-component"))
        registry.addNamespace("admin", "org.konaframework.brew.fixtures.admin_area")
        assertSame(UserList::class.java, registry.classOf("admin::user-list"))
        assertNull(registry.classOf("nope::user-list"))
        registry.component("late") { Counter() }
        assertTrue(registry.has("late"))
        registry.component("kitchen-sink", Counter::class.java)
        assertSame(Counter::class.java, registry.classOf("kitchen-sink"))
    }

    @Test
    fun `discovery registers concrete components`() {
        val registry = ComponentRegistry()
        val found = registry.discover("org.konaframework.brew.fixtures.admin_area")
        assertEquals(setOf("admin.custom", "user-list"), found.toSet())
        val root = ComponentRegistry().discover("org.konaframework.brew.fixtures")
        assertTrue("admin-area.user-list" in root, root.toString())
        assertTrue("counter" in root)
        assertEquals(emptyList<String>(), ComponentRegistry().discover("no.such.pkg"))
    }

    @Test
    fun `discovery scans jars`(@TempDir dir: Path) {
        val jar = dir.resolve("components.jar")
        val entry = "org/konaframework/brew/fixtures/admin_area/UserList.class"
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            out.putNextEntry(JarEntry("org/konaframework/brew/fixtures/admin_area/"))
            out.closeEntry()
            for (name in listOf(entry, "org/konaframework/brew/fixtures/admin_area/Broken.class", "org/konaframework/brew/fixtures/admin_area/X\$1.class", "other/Y.class")) {
                out.putNextEntry(JarEntry(name))
                out.write(if (name == entry) javaClass.classLoader.getResourceAsStream(entry)!!.readAllBytes() else byteArrayOf(1))
                out.closeEntry()
            }
        }
        URLClassLoader(arrayOf(jar.toUri().toURL()), null).use { isolated ->
            val loader = object : ClassLoader(javaClass.classLoader) {
                override fun getResources(name: String) = isolated.getResources(name)
            }
            val found = ComponentRegistry().discover("org.konaframework.brew.fixtures.admin_area", loader)
            assertEquals(listOf("user-list"), found)
        }
    }

    @Test
    fun `container instantiation`() {
        val registry = ComponentRegistry(Container()).component("counter", Counter::class.java)
        assertTrue(registry.create("counter") is Counter)
    }

    @Test
    fun `load tries preferred classLoader and skips broken loaders`() {
        val boom = object : ClassLoader(null) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.startsWith("org.konaframework.brew.fixtures")) throw NoClassDefFoundError(name)
                return super.loadClass(name, resolve)
            }
        }
        val previous = Thread.currentThread().contextClassLoader
        try {
            val viaTccl = ComponentRegistry().addLocation("org.konaframework.brew.fixtures")
            viaTccl.classLoader = boom
            Thread.currentThread().contextClassLoader = javaClass.classLoader
            assertSame(Counter::class.java, viaTccl.classOf("counter"))

            val viaOwn = ComponentRegistry().addLocation("org.konaframework.brew.fixtures")
            viaOwn.classLoader = boom
            Thread.currentThread().contextClassLoader = null
            assertSame(Counter::class.java, viaOwn.classOf("counter"))
        } finally {
            Thread.currentThread().contextClassLoader = previous
        }
        // Non-component class names are ignored by isAssignableFrom.
        val scalars = ComponentRegistry().addLocation("java.lang")
        assertNull(scalars.classOf("string"))
    }
}
