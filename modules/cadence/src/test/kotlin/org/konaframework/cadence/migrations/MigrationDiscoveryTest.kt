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
    fun sortsTimestampedClassNamesRegardlessOfFileOrder() {
        val meta = temp.resolve("META-INF/kona")
        Files.createDirectories(meta)
        Files.writeString(
            meta.resolve("migrations"),
            """
            ${M2026_10_20_120000_Later::class.java.name}
            ${DemoFirst::class.java.name}
            ${M2026_10_20_110000_Earlier::class.java.name}
            """.trimIndent() + "\n",
        )
        val loader = URLClassLoader(arrayOf(temp.toUri().toURL()), javaClass.classLoader)
        assertEquals(
            listOf(
                DemoFirst::class.java.name,
                M2026_10_20_110000_Earlier::class.java.name,
                M2026_10_20_120000_Later::class.java.name,
            ),
            MigrationDiscovery.classNames(loader),
        )
        assertEquals(
            listOf("DemoFirst", "M2026_10_20_110000_Earlier", "M2026_10_20_120000_Later"),
            MigrationDiscovery.loadRegistry(loader).names,
        )
    }

    @Test
    fun sortsAnnotatedFileStemsAndLoadsPackagePrivateJava() {
        val meta = temp.resolve("META-INF/kona")
        Files.createDirectories(meta)
        Files.writeString(
            meta.resolve("migrations"),
            """
            ${AnnotatedLater::class.java.name}
            org.konaframework.cadence.migrations.hidden.PackagePrivateMigration
            ${AnnotatedEarlier::class.java.name}
            """.trimIndent() + "\n",
        )
        val loader = URLClassLoader(arrayOf(temp.toUri().toURL()), javaClass.classLoader)
        assertEquals(
            listOf(
                "2026_10_20_090000_package_private",
                "2026_10_20_110000_create_flights_table",
                "2026_10_20_120000_create_posts_table",
            ),
            MigrationDiscovery.loadRegistry(loader).names,
        )
        val loaded = MigrationDiscovery.loadMigrations(loader)
        assertEquals(
            listOf(
                "2026_10_20_090000_package_private",
                "2026_10_20_110000_create_flights_table",
                "2026_10_20_120000_create_posts_table",
            ),
            loaded.map { MigrationOrder.recordedName(it) },
        )
        val javaMigration = MigrationDiscovery.instantiate(
            "org.konaframework.cadence.migrations.hidden.PackagePrivateMigration",
            javaClass.classLoader,
        )
        assertEquals("2026_10_20_090000_package_private", MigrationOrder.recordedName(javaMigration))
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

    class M2026_10_20_120000_Later : Migration {
        override fun up(schema: Schema) {}
        override fun down(schema: Schema) {}
    }

    class M2026_10_20_110000_Earlier : Migration {
        override fun up(schema: Schema) {}
        override fun down(schema: Schema) {}
    }

    @MigrationName("2026_10_20_120000_create_posts_table")
    class AnnotatedLater : Migration {
        override fun up(schema: Schema) {}
        override fun down(schema: Schema) {}
    }

    @MigrationName("2026_10_20_110000_create_flights_table")
    class AnnotatedEarlier : Migration {
        override fun up(schema: Schema) {}
        override fun down(schema: Schema) {}
    }
}
