package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class JdbcDriverInstallerTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun skipsWhenNoEngineIsConfigured() {
        val root = project()
        val outcome = JdbcDriverInstaller.ensure(root, getenv = { null })
        assertEquals(JdbcDriverInstaller.Status.SKIPPED, outcome.status)
        assertNull(outcome.coordinate)
    }

    @Test
    fun skipsWhenOptedOut() {
        val root = project(env = "DB_CONNECTION=pgsql\n")
        writeBuild(root, sqliteOnly())
        val outcome =
            JdbcDriverInstaller.ensure(
                root,
                mapOf(JdbcDriverInstaller.OPT_OUT to "true"),
                getenv = { null },
            )
        assertEquals(JdbcDriverInstaller.Status.SKIPPED, outcome.status)
        assertFalse(Files.readString(root.resolve("build.gradle.kts")).contains("org.postgresql"))
        assertFalse(JdbcDriverInstaller.optedOut(emptyMap()))
        assertFalse(JdbcDriverInstaller.optedOut(mapOf(JdbcDriverInstaller.OPT_OUT to "false")))
        assertTrue(JdbcDriverInstaller.optedOut(mapOf(JdbcDriverInstaller.OPT_OUT to "true")))
        assertTrue(JdbcDriverInstaller.optedOut(mapOf(JdbcDriverInstaller.OPT_OUT to "")))
    }

    @Test
    fun skipsWhenDriverIsAlreadyLoadable() {
        val root = project(env = "DB_CONNECTION=h2\n")
        val outcome = JdbcDriverInstaller.ensure(root, getenv = { null })
        assertEquals(JdbcDriverInstaller.Status.SKIPPED, outcome.status)
        assertEquals("h2", outcome.engine)
        assertEquals("com.h2database:h2:2.3.232", outcome.coordinate)
    }

    @Test
    fun installsPostgresqlRuntimeOnlyAndPrints() {
        val root = project(env = "DB_CONNECTION=pgsql\n")
        writeBuild(root, sqliteOnly())
        val cache = root.resolve("build/kona-runtime.classpath")
        Files.createDirectories(cache.parent)
        Files.writeString(cache, "/tmp/old.jar")
        val buffer = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(buffer), PrintStream(buffer), color = false)
        val outcome = JdbcDriverInstaller.ensure(root, emptyMap(), io, getenv = { null })
        assertEquals(JdbcDriverInstaller.Status.INSTALLED, outcome.status)
        assertEquals("pgsql", outcome.engine)
        val gradle = Files.readString(root.resolve("build.gradle.kts"))
        assertTrue(gradle.contains("""runtimeOnly("org.postgresql:postgresql:42.7.5")"""))
        assertFalse(Files.exists(cache))
        assertTrue(buffer.toString().contains("Added JDBC driver for [pgsql]"))
    }

    @Test
    fun updatesExistingPostgresqlVersion() {
        val root = project(env = "DB_CONNECTION=postgres\n")
        writeBuild(root, """dependencies { runtimeOnly("org.postgresql:postgresql:1.0.0") }""")
        val outcome = JdbcDriverInstaller.ensure(root, getenv = { null })
        assertEquals(JdbcDriverInstaller.Status.INSTALLED, outcome.status)
        assertTrue(Files.readString(root.resolve("build.gradle.kts")).contains("postgresql:42.7.5"))
    }

    @Test
    fun declaredWhenCoordinateAlreadyPresent() {
        val root = project()
        writeBuild(root, """dependencies { runtimeOnly("org.postgresql:postgresql:42.7.5") }""")
        val outcome =
            JdbcDriverInstaller.ensure(
                root,
                getenv = { if (it == "DB_CONNECTION") "pgsql" else null },
            )
        assertEquals(JdbcDriverInstaller.Status.DECLARED, outcome.status)
        assertEquals(root.resolve("build.gradle.kts"), outcome.buildFile)
    }

    @Test
    fun groovyBuildFileAndUrlDetection() {
        val root = temp.resolve("groovy")
        Files.createDirectories(root)
        Files.writeString(
            root.resolve("build.gradle"),
            """
            dependencies {
                runtimeOnly 'org.xerial:sqlite-jdbc:3.49.1.0'
            }
            """.trimIndent(),
        )
        val outcome =
            JdbcDriverInstaller.ensure(
                root,
                getenv = { if (it == "DB_URL") "jdbc:postgresql://127.0.0.1/kona" else null },
            )
        assertEquals(JdbcDriverInstaller.Status.INSTALLED, outcome.status)
        assertTrue(Files.readString(root.resolve("build.gradle")).contains("org.postgresql:postgresql:42.7.5"))
    }

    @Test
    fun noBuildFileAndMissingDependenciesBlock() {
        val missing = temp.resolve("nobuild")
        Files.createDirectories(missing)
        Files.writeString(missing.resolve(".env"), "DB_CONNECTION=mysql\n")
        val none = JdbcDriverInstaller.ensure(missing, getenv = { null })
        assertEquals(JdbcDriverInstaller.Status.NO_BUILD, none.status)
        assertEquals("mysql", none.engine)

        val broken = project(env = "DB_DRIVER=org.postgresql.Driver\n")
        Files.writeString(broken.resolve("build.gradle.kts"), "plugins { java }\n")
        val failed = JdbcDriverInstaller.ensure(broken, getenv = { null })
        assertEquals(JdbcDriverInstaller.Status.NO_BUILD, failed.status)
    }

    @Test
    fun processEnvWinsOverDotenvAndParsesDotenvShape() {
        val root = project(
            env =
                """
                # comment

                export DB_CONNECTION=mysql
                DB_URL=jdbc:mysql://127.0.0.1/kona # trailing
                QUOTED="pgsql"
                SINGLE='h2'
                =novalue
                not-a-pair
                BLANK=
                """.trimIndent(),
        )
        writeBuild(root, sqliteOnly())
        val dotenv = JdbcDriverInstaller.readDotenv(root)
        assertEquals("mysql", dotenv["DB_CONNECTION"])
        assertEquals("jdbc:mysql://127.0.0.1/kona", dotenv["DB_URL"])
        assertEquals("pgsql", dotenv["QUOTED"])
        assertEquals("h2", dotenv["SINGLE"])
        assertFalse(dotenv.containsKey("not-a-pair"))
        assertEquals("", dotenv["BLANK"])
        assertTrue(JdbcDriverInstaller.readDotenv(temp.resolve("missing-env")).isEmpty())

        val outcome =
            JdbcDriverInstaller.ensure(
                root,
                getenv = { key -> if (key == "DB_CONNECTION") "pgsql" else null },
            )
        assertEquals(JdbcDriverInstaller.Status.INSTALLED, outcome.status)
        assertEquals("pgsql", outcome.engine)

        val blankEnv =
            JdbcDriverInstaller.ensure(
                project(env = "DB_CONNECTION=pgsql\n").also { writeBuild(it, sqliteOnly()) },
                getenv = { "" },
            )
        assertEquals(JdbcDriverInstaller.Status.INSTALLED, blankEnv.status)

        val blankDotenv =
            JdbcDriverInstaller.ensure(
                project(env = "DB_CONNECTION=\nDB_DRIVER=\nDB_URL=jdbc:postgresql://127.0.0.1/kona\n")
                    .also { writeBuild(it, sqliteOnly()) },
                getenv = { null },
            )
        assertEquals(JdbcDriverInstaller.Status.INSTALLED, blankDotenv.status)
        assertEquals("pgsql", blankDotenv.engine)
    }

    @Test
    fun forgetClasspathCacheIsIdempotent() {
        val root = project()
        JdbcDriverInstaller.forgetClasspathCache(root)
        val cache = root.resolve("build/kona-runtime.classpath")
        Files.createDirectories(cache.parent)
        Files.writeString(cache, "x")
        JdbcDriverInstaller.forgetClasspathCache(root)
        assertFalse(Files.exists(cache))
    }

    private fun project(env: String = ""): Path {
        val dir = Files.createTempDirectory(temp, "app")
        Files.writeString(dir.resolve(".env"), env)
        return dir
    }

    private fun writeBuild(root: Path, body: String) {
        Files.writeString(root.resolve("build.gradle.kts"), body)
    }

    private fun sqliteOnly(): String =
        """
        plugins { java }
        dependencies {
            runtimeOnly("org.xerial:sqlite-jdbc:3.49.1.0")
        }
        """.trimIndent()
}
