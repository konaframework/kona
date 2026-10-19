package org.konaframework.cadence.migrations

import org.konaframework.cadence.schema.Schema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class MigrationDiscoveryTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun loadsMigrationsFromMetaInfInOrder() {
        val meta = temp.resolve("META-INF/kona")
        Files.createDirectories(meta)
        Files.writeString(
            meta.resolve("migrations"),
            """
            # comment

            ${DemoFirst::class.java.name}
            ${DemoSecond::class.java.name}
            """.trimIndent() + "\n",
        )
        val loader = URLClassLoader(arrayOf(temp.toUri().toURL()), javaClass.classLoader)
        assertEquals(
            listOf(DemoFirst::class.java.name, DemoSecond::class.java.name),
            MigrationDiscovery.classNames(loader),
        )
        val registry = MigrationDiscovery.loadRegistry(loader)
        assertEquals(listOf("DemoFirst", "DemoSecond"), registry.names)
    }

    @Test
    fun rejectsNonMigrationClasses() {
        val meta = temp.resolve("META-INF/kona")
        Files.createDirectories(meta)
        Files.writeString(meta.resolve("migrations"), "java.lang.String\n")
        val loader = URLClassLoader(arrayOf(temp.toUri().toURL()), javaClass.classLoader)
        assertThrows(IllegalArgumentException::class.java) {
            MigrationDiscovery.loadMigrations(loader)
        }
    }

    @Test
    fun emptyWhenMissing() {
        val loader = URLClassLoader(arrayOf(temp.toUri().toURL()), javaClass.classLoader)
        assertTrue(MigrationDiscovery.classNames(loader).isEmpty())
        assertTrue(MigrationDiscovery.loadRegistry(loader).names.isEmpty())
    }

    class DemoFirst : Migration {
        override fun up(schema: Schema) {}
        override fun down(schema: Schema) {}
    }

    class DemoSecond : Migration {
        override fun up(schema: Schema) {}
        override fun down(schema: Schema) {}
    }
}
