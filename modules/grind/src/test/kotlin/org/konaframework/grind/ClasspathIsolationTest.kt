package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ClasspathIsolationTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun prefersExplodedDirsOverBuildLibsJar() {
        val module = temp.resolve("modules/demo")
        val classes = module.resolve("build/classes/kotlin/main")
        val resources = module.resolve("build/resources/main")
        Files.createDirectories(classes)
        Files.createDirectories(resources)
        val jar = module.resolve("build/libs/demo-1.0.jar")
        Files.createDirectories(jar.parent)
        writeJar(jar, "Marker.txt", "from-jar")

        val exploded = ClasspathIsolation.explodedForBuildJar(jar)
        assertEquals(
            listOf(classes.toAbsolutePath().normalize(), resources.toAbsolutePath().normalize()),
            exploded.map { it.toAbsolutePath().normalize() },
        )

        val staged = ClasspathIsolation.materialize(temp.resolve("app"), listOf(jar))
        assertNull(staged.directory)
        assertTrue(staged.entries.none { it.toString().endsWith(".jar") })
        assertTrue(staged.entries.any { it.endsWith(Path.of("classes/kotlin/main")) })
    }

    @Test
    fun copiesMutableBuildJarWhenExplodedMissing() {
        val module = temp.resolve("modules/onlyjar")
        val jar = module.resolve("build/libs/onlyjar-1.0.jar")
        Files.createDirectories(jar.parent)
        writeJar(jar, "hello.txt", "v1")

        val project = temp.resolve("app")
        val staged = ClasspathIsolation.materialize(project, listOf(jar))
        assertTrue(staged.directory != null)
        assertEquals(1, staged.entries.size)
        val copy = staged.entries.single()
        assertTrue(Files.isRegularFile(copy))
        assertNotEquals(jar.toAbsolutePath().normalize(), copy.toAbsolutePath().normalize())
        assertTrue(copy.startsWith(staged.directory))

        // Rewrite the Gradle-managed original while the copy stays readable.
        writeJar(jar, "hello.txt", "corrupted-mid-write")
        Files.write(jar, byteArrayOf(0, 1, 2, 3, 4))
        URLClassLoader(arrayOf(copy.toUri().toURL()), null).use { loader ->
            loader.getResourceAsStream("hello.txt").use { stream ->
                assertEquals("v1", stream!!.readAllBytes().toString(Charsets.UTF_8))
            }
        }
        ClasspathIsolation.deleteQuietly(staged.directory)
        assertTrue(staged.directory == null || Files.notExists(staged.directory))
    }

    @Test
    fun leavesImmutableCacheJarsInPlace() {
        val cacheJar = temp.resolve(".gradle/caches/modules-2/files-2.1/lib.jar")
        Files.createDirectories(cacheJar.parent)
        writeJar(cacheJar, "x.txt", "cache")
        val staged = ClasspathIsolation.materialize(temp.resolve("app"), listOf(cacheJar))
        assertNull(staged.directory)
        assertEquals(listOf(cacheJar.toAbsolutePath().normalize()), staged.entries.map { it.toAbsolutePath().normalize() })
    }

    @Test
    fun resolveHelpersCoverNonJarAndNonLibsPaths() {
        val dir = temp.resolve("classes")
        Files.createDirectories(dir)
        assertEquals(listOf(dir), ClasspathIsolation.resolve(dir, temp.resolve("gen")) {})
        val plain = temp.resolve("readme.txt")
        Files.writeString(plain, "x")
        assertEquals(listOf(plain), ClasspathIsolation.resolve(plain, temp.resolve("gen")) {})
        assertFalse(ClasspathIsolation.isJar(plain))
        assertFalse(ClasspathIsolation.isMutableBuildJar(plain))
        assertTrue(ClasspathIsolation.explodedForBuildJar(plain).isEmpty())

        val notLibs = temp.resolve("dist/app.jar")
        Files.createDirectories(notLibs.parent)
        writeJar(notLibs, "a.txt", "a")
        assertTrue(ClasspathIsolation.explodedForBuildJar(notLibs).isEmpty())
        assertFalse(ClasspathIsolation.isMutableBuildJar(notLibs))

        val libsButNotBuild = temp.resolve("other/libs/app.jar")
        Files.createDirectories(libsButNotBuild.parent)
        writeJar(libsButNotBuild, "a.txt", "a")
        assertTrue(ClasspathIsolation.explodedForBuildJar(libsButNotBuild).isEmpty())
        assertFalse(ClasspathIsolation.isMutableBuildJar(libsButNotBuild))

        // Relative jar with no parent — null-safe branches in exploded / mutable checks.
        val lonely = Path.of("lonely.jar")
        assertTrue(ClasspathIsolation.explodedForBuildJar(lonely).isEmpty())
        assertFalse(ClasspathIsolation.isMutableBuildJar(lonely))
        assertTrue(ClasspathIsolation.isJar(lonely))

        // `libs/x.jar` — libs has no parent.
        val libsOnly = Path.of("libs/x.jar")
        assertTrue(ClasspathIsolation.explodedForBuildJar(libsOnly).isEmpty())
        assertFalse(ClasspathIsolation.isMutableBuildJar(libsOnly))

        // `/x.jar` — parent is filesystem root (null fileName), not "libs".
        val underRoot = Path.of("/x.jar")
        assertTrue(ClasspathIsolation.explodedForBuildJar(underRoot).isEmpty())
        assertFalse(ClasspathIsolation.isMutableBuildJar(underRoot))

        // Absolute `/libs/x.jar` → parent of libs is filesystem root (null fileName).
        val underRootLibs = Path.of("/libs/x.jar")
        assertTrue(ClasspathIsolation.explodedForBuildJar(underRootLibs).isEmpty())
        assertFalse(ClasspathIsolation.isMutableBuildJar(underRootLibs))

        // Filesystem root has a null fileName.
        assertFalse(ClasspathIsolation.isJar(Path.of("/")))
        assertTrue(ClasspathIsolation.isJar(Path.of("Lib.JAR")))

        assertEquals("lib-dep.jar", ClasspathIsolation.uniqueJarName(Path.of("/")))
        assertEquals("lib-lonely.jar", ClasspathIsolation.uniqueJarName(Path.of("lonely.jar")))
        // `/libs/x.jar` → grandparent is filesystem root (null fileName) → "lib" fallback.
        assertEquals("lib-x.jar", ClasspathIsolation.uniqueJarName(Path.of("/libs/x.jar")))
        assertEquals("build-x.jar", ClasspathIsolation.uniqueJarName(Path.of("mod/build/libs/x.jar")))
        assertEquals("lib-x.jar", ClasspathIsolation.uniqueJarName(Path.of("libs/x.jar")))

        ClasspathIsolation.deleteQuietly(null)
        ClasspathIsolation.deleteQuietly(temp.resolve("missing-dir"))
        val fileAsDir = temp.resolve("not-a-dir")
        Files.writeString(fileAsDir, "x")
        ClasspathIsolation.deleteQuietly(fileAsDir)
    }

    @Test
    fun appLoaderCreateUsesIsolatedJarCopies() {
        val base = fixtureProject(temp.resolve("loader-iso"))
        val module = temp.resolve("modules/runtime")
        val jar = module.resolve("build/libs/runtime-1.0.jar")
        Files.createDirectories(jar.parent)
        writeJar(jar, "iso.txt", "ok")

        val loader = AppLoader(base, FixtureApp::class.java.name) { listOf(jar) }
        assertEquals(listOf(jar), loader.classpath().filter { it.fileName.toString().endsWith(".jar") })
        val app = loader.create(arrayOf("--base=$base"))
        val urls = (loader.classLoader as URLClassLoader).urLs.map { Path.of(it.toURI()).toAbsolutePath().normalize() }
        assertTrue(urls.none { it == jar.toAbsolutePath().normalize() }, urls.toString())
        assertTrue(urls.any { it.toString().contains("kona-classpath") && it.toString().endsWith(".jar") }, urls.toString())

        // Corrupt the Gradle jar; the live loader must keep working off its copy.
        Files.write(jar, byteArrayOf(0x50, 0x4b, 0x00, 0x00))
        URLClassLoader(urls.filter { it.toString().endsWith(".jar") }.map { it.toUri().toURL() }.toTypedArray(), null).use { probe ->
            probe.getResourceAsStream("iso.txt").use { stream ->
                assertEquals("ok", stream!!.readAllBytes().toString(Charsets.UTF_8))
            }
        }
        ServeWatch.dispose(app, loader)
        assertNull(loader.classLoader)
    }

    @Test
    fun softReloadClosesPreviousLoaderThenBootsFromFreshJarCopy() {
        val base = fixtureProject(temp.resolve("reload-iso"))
        val module = temp.resolve("modules/firefly-sim")
        val jar = module.resolve("build/libs/kona-firefly-sim.jar")
        Files.createDirectories(jar.parent)
        writeJar(jar, "panel.txt", "gen-1")

        val first = AppLoader(base, FixtureApp::class.java.name) { listOf(jar) }
        val firstApp = first.create(arrayOf("--base=$base"))
        val firstCopy = (first.classLoader as URLClassLoader).urLs
            .map { Path.of(it.toURI()) }
            .single { it.toString().endsWith(".jar") }

        // Simulate includeBuild `:jar` rewriting the artifact while the previous loader is live.
        Files.write(jar, byteArrayOf(0x50, 0x4b, 0x00, 0x00))
        writeJar(jar, "panel.txt", "gen-2")

        // Mirror ServeWatch.reload: close previous Zip handles, then boot a new generation.
        first.close()
        assertTrue(Files.notExists(firstCopy))

        val second = AppLoader(base, FixtureApp::class.java.name) { listOf(jar) }
        val secondApp = second.create(arrayOf("--base=$base"))
        val secondUrls = (second.classLoader as URLClassLoader).urLs
        assertTrue(secondUrls.any { it.path.contains("kona-classpath") })
        Files.write(jar, byteArrayOf(0, 0, 0, 0))
        URLClassLoader(secondUrls.filter { it.path.endsWith(".jar") }.toTypedArray(), null).use { probe ->
            probe.getResourceAsStream("panel.txt").use { stream ->
                assertEquals("gen-2", stream!!.readAllBytes().toString(Charsets.UTF_8))
            }
        }
        ServeWatch.dispose(secondApp, second)
        ServeWatch.dispose(firstApp, first)
    }

    private fun writeJar(jar: Path, entry: String, payload: String) {
        Files.createDirectories(jar.parent)
        ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            zip.putNextEntry(ZipEntry(entry))
            zip.write(payload.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
