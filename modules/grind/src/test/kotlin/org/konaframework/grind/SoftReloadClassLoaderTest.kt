package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider

class SoftReloadClassLoaderTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun preferChildClassMatrix() {
        assertFalse(SoftReloadClassLoader.preferChildClass("org.konaframework.firefly.Firefly"))
        assertFalse(SoftReloadClassLoader.preferChildClass("org.konaframework.grind.AppLoader"))
        assertFalse(SoftReloadClassLoader.preferChildClass("java.lang.String"))
        assertFalse(SoftReloadClassLoader.preferChildClass("javax.tools.ToolProvider"))
        assertFalse(SoftReloadClassLoader.preferChildClass("jakarta.inject.Inject"))
        assertFalse(SoftReloadClassLoader.preferChildClass("jdk.internal.misc.Unsafe"))
        assertFalse(SoftReloadClassLoader.preferChildClass("sun.misc.Unsafe"))
        assertFalse(SoftReloadClassLoader.preferChildClass("com.sun.nio.zipfs.ZipFileSystem"))
        assertFalse(SoftReloadClassLoader.preferChildClass("kotlin.Unit"))
        assertFalse(SoftReloadClassLoader.preferChildClass("kotlinx.coroutines.Job"))
        assertFalse(SoftReloadClassLoader.preferChildClass("org.jetbrains.annotations.NotNull"))
        assertTrue(SoftReloadClassLoader.preferChildClass("app.panels.admin.Panel"))
        assertTrue(SoftReloadClassLoader.preferChildClass("bootstrap.App"))
        assertTrue(SoftReloadClassLoader.preferChildClass("config.App"))
        assertTrue(SoftReloadClassLoader.preferChildClass("routes.WebRoutes"))
    }

    @Test
    fun childWinsOverStaleParentAppClassAndResources() {
        val parentDir = temp.resolve("parent")
        val childDir = temp.resolve("child")
        Files.createDirectories(parentDir)
        Files.createDirectories(childDir)
        compileMarker(parentDir, "v1")
        compileMarker(childDir, "v2")
        Files.writeString(parentDir.resolve("note.txt"), "parent-note")
        Files.writeString(childDir.resolve("note.txt"), "child-note")

        URLClassLoader(arrayOf(parentDir.toUri().toURL()), null).use { parent ->
            val stale = parent.loadClass("hot.Marker")
            assertEquals("v1", stale.getMethod("token").invoke(null))

            SoftReloadClassLoader(arrayOf(childDir.toUri().toURL()), parent).use { child ->
                val fresh = child.loadClass("hot.Marker")
                assertNotSame(stale, fresh)
                assertEquals("v2", fresh.getMethod("token").invoke(null))
                // Second load hits findLoadedClass then resolveClass.
                assertSame(fresh, child.loadClass("hot.Marker"))
                // Framework-style names still delegate to parent (and stay identity-stable).
                assertSame(String::class.java, child.loadClass("java.lang.String"))
                assertEquals("child-note", child.getResourceAsStream("note.txt")!!.reader().readText())
                val notes = child.getResources("note.txt").toList().map { it.openStream().reader().readText() }
                assertEquals(listOf("child-note", "parent-note"), notes)
            }
        }
    }

    @Test
    fun fallsBackToParentWhenChildLacksClassAndMergesResources() {
        val parentDir = temp.resolve("only-parent")
        val childDir = temp.resolve("empty-child")
        Files.createDirectories(parentDir)
        Files.createDirectories(childDir)
        compileMarker(parentDir, "only-parent")
        Files.writeString(parentDir.resolve("shared.txt"), "from-parent")

        URLClassLoader(arrayOf(parentDir.toUri().toURL()), null).use { parent ->
            SoftReloadClassLoader(arrayOf(childDir.toUri().toURL()), parent).use { child ->
                assertEquals("only-parent", child.loadClass("hot.Marker").getMethod("token").invoke(null))
                assertEquals("from-parent", child.getResourceAsStream("shared.txt")!!.reader().readText())
                assertTrue(child.getResources("missing-resource").toList().isEmpty())
            }
        }

        SoftReloadClassLoader(arrayOf(childDir.toUri().toURL()), null).use { orphan ->
            assertTrue(orphan.getResources("shared.txt").toList().isEmpty())
            assertEquals(null, orphan.getResource("shared.txt"))
        }

        val childOnly = temp.resolve("child-only")
        Files.createDirectories(childOnly)
        Files.writeString(childOnly.resolve("only-child.txt"), "mine")
        SoftReloadClassLoader(arrayOf(childOnly.toUri().toURL()), null).use { loader ->
            assertEquals(listOf("mine"), loader.getResources("only-child.txt").toList().map { it.openStream().reader().readText() })
        }

        // Same resource URL on child and parent exercises the merge de-dupe branch.
        val shared = temp.resolve("shared-root")
        Files.createDirectories(shared)
        Files.writeString(shared.resolve("dup.txt"), "once")
        URLClassLoader(arrayOf(shared.toUri().toURL()), null).use { parentShared ->
            SoftReloadClassLoader(arrayOf(shared.toUri().toURL()), parentShared).use { both ->
                assertEquals(1, both.getResources("dup.txt").toList().size)
            }
        }
    }

    private fun compileMarker(output: Path, token: String) {
        val srcRoot = temp.resolve("src-${output.fileName}")
        val pkg = srcRoot.resolve("hot")
        Files.createDirectories(pkg)
        val src = pkg.resolve("Marker.java")
        Files.writeString(
            src,
            """
            package hot;
            public class Marker {
              public static String token() { return "$token"; }
            }
            """.trimIndent(),
        )
        val javac = ToolProvider.getSystemJavaCompiler()
        assertTrue(javac != null, "JDK compiler required for SoftReloadClassLoaderTest")
        val ok = javac!!.run(null, null, null, "-d", output.toString(), src.toString())
        assertEquals(0, ok, "javac failed for Marker token=$token")
    }
}
