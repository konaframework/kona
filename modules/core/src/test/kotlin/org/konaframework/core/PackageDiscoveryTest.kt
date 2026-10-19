package org.konaframework.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class PackageDiscoveryTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun readsProvidersSkipsCommentsAndRegistersOnBoot() {
        DemoPackageProvider.registered = false
        DemoPackageProvider.booted = false
        val root = temp.resolve("pkg")
        Files.createDirectories(root.resolve("META-INF/kona"))
        Files.writeString(
            root.resolve("META-INF/kona/providers"),
            "${DemoPackageProvider::class.java.name}\n# comment\n\n",
        )
        val loader = URLClassLoader(arrayOf(root.toUri().toURL()), PackageDiscoveryTest::class.java.classLoader)
        assertEquals(listOf(DemoPackageProvider::class.java.name), PackageDiscovery.providerNames(loader))

        val app = Application(temp.resolve("app"))
        val prev = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = loader
            app.boot()
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
        assertTrue(DemoPackageProvider.registered)
        assertTrue(DemoPackageProvider.booted)
        assertEquals(1, app.providers().size)
    }

    @Test
    fun registerAllIdempotentWithExplicitProvider() {
        DemoPackageProvider.registered = false
        DemoPackageProvider.booted = false
        val root = temp.resolve("pkg2")
        Files.createDirectories(root.resolve("META-INF/kona"))
        Files.writeString(root.resolve("META-INF/kona/providers"), DemoPackageProvider::class.java.name)
        val loader = URLClassLoader(arrayOf(root.toUri().toURL()), PackageDiscoveryTest::class.java.classLoader)
        val app = Application(temp.resolve("app2"))
        app.register(DemoPackageProvider())
        PackageDiscovery.registerAll(app, loader)
        assertEquals(1, app.providers().size)
        app.boot()
        assertTrue(DemoPackageProvider.booted)
    }

    @Test
    fun emptyManifestReturnsEmpty() {
        val root = temp.resolve("empty")
        Files.createDirectories(root.resolve("META-INF/kona"))
        Files.writeString(root.resolve("META-INF/kona/providers"), "# only comments\n")
        val loader = URLClassLoader(arrayOf(root.toUri().toURL()), PackageDiscoveryTest::class.java.classLoader)
        assertTrue(PackageDiscovery.providerNames(loader).isEmpty())
        assertTrue(PackageDiscovery.registerAll(Application(temp.resolve("a")), loader).isEmpty())
    }

    @Test
    fun rejectsNonServiceProvider() {
        val root = temp.resolve("bad")
        Files.createDirectories(root.resolve("META-INF/kona"))
        Files.writeString(root.resolve("META-INF/kona/providers"), "java.lang.String\n")
        val loader = URLClassLoader(arrayOf(root.toUri().toURL()), PackageDiscoveryTest::class.java.classLoader)
        assertThrows(IllegalArgumentException::class.java) {
            PackageDiscovery.loadProviders(loader)
        }
    }

    @Test
    fun registerAllDefaultLoaderUsesContextClassLoader() {
        DemoPackageProvider.registered = false
        val root = temp.resolve("default-loader")
        Files.createDirectories(root.resolve("META-INF/kona"))
        Files.writeString(root.resolve("META-INF/kona/providers"), DemoPackageProvider::class.java.name)
        val loader = URLClassLoader(arrayOf(root.toUri().toURL()), PackageDiscoveryTest::class.java.classLoader)
        val app = Application(temp.resolve("app-default"))
        val prev = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = loader
            assertEquals(listOf(DemoPackageProvider::class.java.name), PackageDiscovery.registerAll(app))
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
        assertTrue(DemoPackageProvider.registered)
    }

    @Test
    fun registerAllFallsBackWhenContextClassLoaderNull() {
        val app = Application(temp.resolve("app-null-cl"))
        val prev = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = null
            // No META-INF on the app classloader → empty; exercises `?: app.javaClass.classLoader`.
            assertTrue(PackageDiscovery.registerAll(app).isEmpty())
            app.boot()
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
        assertTrue(app.isBooted())
    }

    class DemoPackageProvider : ServiceProvider {
        override fun register(app: Application) {
            registered = true
        }

        override fun boot(app: Application) {
            booted = true
        }

        companion object {
            @JvmField
            var registered = false

            @JvmField
            var booted = false
        }
    }
}
