package org.konaframework.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class InstallCommandTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun installAddsDependencyViaKonaCli() {
        writeBuild()
        val code =
            InstallCommand().run(
                listOf("filesystem", "--base=$root", "--version=8.8.8"),
            )
        assertEquals(0, code)
        assertTrue(
            Files.readString(root.resolve("build.gradle.kts"))
                .contains("org.konaframework:kona-filesystem:8.8.8"),
        )
        assertTrue(
            Files.readString(root.resolve("src/main/resources/META-INF/kona/providers"))
                .contains("FilesystemServiceProvider"),
        )
    }

    @Test
    fun installWithoutPackagePrintsUsage() {
        assertEquals(1, InstallCommand().run(emptyList()))
    }

    @Test
    fun konaApplicationDispatchesInstall() {
        writeBuild("""implementation("org.konaframework:kona-http:1.0.0")""")
        assertEquals(
            0,
            KonaApplication().run(arrayOf("install", "drip", "--base=$root")),
        )
    }

    @Test
    fun installUsesDetectedVersionWhenFlagMissing() {
        writeBuild("""implementation("org.konaframework:kona-http:3.3.3")""")
        assertEquals(0, InstallCommand().run(listOf("drip", "--base=$root")))
        assertTrue(Files.readString(root.resolve("build.gradle.kts")).contains("kona-drip:3.3.3"))
    }

    @Test
    fun installFallsBackToBundledVersion() {
        writeBuild()
        assertEquals(0, InstallCommand().run(listOf("drip", "--base=$root")))
        assertTrue(Files.readString(root.resolve("build.gradle.kts")).contains("kona-drip:"))
    }

    @Test
    fun installReportsAlreadyPresent() {
        writeBuild("""implementation("org.konaframework:kona-drip:1.0.0")""")
        assertEquals(0, InstallCommand().run(listOf("drip", "--base=$root", "--version=1.0.0")))
        assertEquals(0, InstallCommand().run(listOf("drip", "--base=$root", "--version=1.0.0")))
    }

    @Test
    fun installReportsUpdatedDependency() {
        writeBuild("""implementation("org.konaframework:kona-drip:1.0.0")""")
        assertEquals(0, InstallCommand().run(listOf("drip", "--base=$root", "--version=2.0.0")))
    }

    @Test
    fun installReportsProvidersAlreadyRegistered() {
        writeBuild()
        assertEquals(0, InstallCommand().run(listOf("crema", "--base=$root", "--version=1.0.0")))
        assertEquals(0, InstallCommand().run(listOf("crema", "--base=$root", "--version=1.0.0")))
    }

    @Test
    fun installUnknownPackageFails() {
        writeBuild()
        assertEquals(1, InstallCommand().run(listOf("not-a-real-package", "--base=$root")))
    }

    @Test
    fun installMissingBuildFails() {
        assertEquals(1, InstallCommand().run(listOf("drip", "--base=$root")))
    }

    @Test
    fun installRejectsNonDirectoryBase() {
        val file = root.resolve("not-dir")
        Files.writeString(file, "x")
        assertEquals(1, InstallCommand().run(listOf("drip", "--base=$file")))
    }

    @Test
    fun findGradleRootWalksParentsAndFailsWhenMissing() {
        writeBuild()
        val nested = root.resolve("nested/deep")
        Files.createDirectories(nested)
        assertEquals(root, InstallCommand.findGradleRoot(nested))
        val orphan = Files.createTempDirectory("kona-install-orphan")
        try {
            org.junit.jupiter.api.Assertions.assertThrows(KonaCliException::class.java) {
                InstallCommand.findGradleRoot(orphan)
            }
        } finally {
            orphan.toFile().deleteRecursively()
        }
    }

    @Test
    fun findGradleRootAcceptsGroovyBuildFile() {
        Files.writeString(root.resolve("build.gradle"), "dependencies {}\n")
        assertEquals(root, InstallCommand.findGradleRoot(root))
    }

    private fun writeBuild(extraDep: String = "") {
        val deps =
            if (extraDep.isEmpty()) {
                "dependencies {\n}"
            } else {
                "dependencies {\n    $extraDep\n}"
            }
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """
            plugins { java }
            $deps
            """.trimIndent(),
        )
    }
}
