package io.konaframework.grind

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
    fun grindInstallAddsDependency() {
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """
            plugins { java }
            dependencies {
            }
            """.trimIndent(),
        )
        assertEquals(
            0,
            InstallCommand(root).run(listOf("drip", "--version=1.2.3")),
        )
        assertTrue(Files.readString(root.resolve("build.gradle.kts")).contains("kona-drip:1.2.3"))
    }

    @Test
    fun grindInstallUsageWhenMissingPackage() {
        assertEquals(1, InstallCommand(root).run(emptyList()))
    }

    @Test
    fun grindInstallUnknownFails() {
        Files.writeString(
            root.resolve("build.gradle.kts"),
            "dependencies {\n}\n",
        )
        assertEquals(1, InstallCommand(root).run(listOf("nope")))
    }

    @Test
    fun grindApplicationDispatchesInstall() {
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """
            plugins { java }
            dependencies {
                implementation("io.konaframework:kona-http:1.0.0")
            }
            """.trimIndent(),
        )
        assertEquals(
            0,
            GrindApplication(basePath = root).run(arrayOf("install", "drip", "--base=$root")),
        )
    }

    @Test
    fun grindInstallReportsAlreadyPresentAndProviders() {
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """
            plugins { java }
            dependencies {
            }
            """.trimIndent(),
        )
        assertEquals(0, InstallCommand(root).run(listOf("crema", "--version=1.0.0")))
        assertEquals(0, InstallCommand(root).run(listOf("crema", "--version=1.0.0")))
        assertEquals(0, InstallCommand(root).run(listOf("crema", "--version=2.0.0")))
    }

    @Test
    fun grindInstallMissingBuildFails() {
        assertEquals(1, InstallCommand(root).run(listOf("drip")))
    }
}
