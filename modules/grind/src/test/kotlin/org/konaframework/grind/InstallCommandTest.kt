package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
                implementation("org.konaframework:kona-http:1.0.0")
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

    @Test
    fun grindInstallFireflyWritesConfigProviderAndBootstrap() {
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """
            dependencies {
            }
            """.trimIndent(),
        )
        Files.createDirectories(root.resolve("src/main/kotlin/bootstrap"))
        Files.writeString(
            root.resolve("src/main/kotlin/bootstrap/App.kt"),
            """
            package bootstrap
            import config.Hash
            class App {
                fun boot() = listOf(
                    Http, Paths,
                )
            }
            """.trimIndent(),
        )
        assertEquals(0, InstallCommand(root).run(listOf("firefly", "--version=1.2.3")))
        val build = Files.readString(root.resolve("build.gradle.kts"))
        assertTrue(build.contains("kona-firefly:1.2.3"))
        val providers = Files.readString(root.resolve("src/main/resources/META-INF/kona/providers"))
        assertTrue(providers.contains("org.konaframework.firefly.FireflyServiceProvider"))
        assertTrue(providers.contains("app.providers.FireflyServiceProvider"))
        assertTrue(Files.readString(root.resolve("src/main/kotlin/config/Firefly.kt")).contains("discovery"))
        assertTrue(Files.readString(root.resolve("src/main/kotlin/app/providers/FireflyServiceProvider.kt")).contains("Firefly.register"))
        val app = Files.readString(root.resolve("src/main/kotlin/bootstrap/App.kt"))
        assertTrue(app.contains("import config.Firefly"))
        assertTrue(app.contains("Firefly,"))
        assertEquals(0, InstallCommand(root).run(listOf("firefly", "--version=1.2.3")))
    }

    @Test
    fun grindInstallFireflyJavaPatchesBootstrap() {
        Files.writeString(root.resolve("build.gradle.kts"), "dependencies {\n}\n")
        Files.createDirectories(root.resolve("src/main/java/bootstrap"))
        Files.writeString(
            root.resolve("src/main/java/bootstrap/App.java"),
            """
            package bootstrap;
            class App {
                void boot() {
                    configs(
                        new Paths()
                    );
                }
            }
            """.trimIndent(),
        )
        assertEquals(0, InstallCommand(root).run(listOf("firefly", "--lang=java", "--version=1.2.3")))
        assertTrue(Files.readString(root.resolve("src/main/java/config/Firefly.java")).contains("discovery"))
        assertTrue(Files.readString(root.resolve("src/main/java/app/providers/FireflyServiceProvider.java")).contains("register"))
        assertTrue(Files.readString(root.resolve("src/main/java/bootstrap/App.java")).contains("new config.Firefly()"))
        assertEquals(0, InstallCommand(root).run(listOf("firefly", "--lang=java", "--version=1.2.3")))
    }

    @Test
    fun fireflyInstallLeavesBootstrapAloneWhenItDoesNotMatch() {
        Files.writeString(root.resolve("build.gradle.kts"), "dependencies {\n}\n")
        Files.createDirectories(root.resolve("src/main/kotlin/bootstrap"))
        Files.writeString(root.resolve("src/main/kotlin/bootstrap/App.kt"), "package bootstrap\nclass App\n")
        FireflyProjectInstall.install(root, "kotlin")
        assertEquals("package bootstrap\nclass App\n", Files.readString(root.resolve("src/main/kotlin/bootstrap/App.kt")))
        val javaRoot = root.resolve("java-app")
        Files.createDirectories(javaRoot.resolve("src/main/java/bootstrap"))
        Files.writeString(javaRoot.resolve("src/main/java/bootstrap/App.java"), "package bootstrap;\nclass App {}\n")
        Files.writeString(javaRoot.resolve("build.gradle.kts"), "dependencies {\n}\n")
        FireflyProjectInstall.install(javaRoot, "java")
        assertFalse(Files.readString(javaRoot.resolve("src/main/java/bootstrap/App.java")).contains("Firefly"))
    }

    @Test
    fun fireflyInstallPatchesKotlinThatAlreadyImportsFireflyOrSkipsHash() {
        Files.createDirectories(root.resolve("src/main/kotlin/bootstrap"))
        Files.writeString(
            root.resolve("src/main/kotlin/bootstrap/App.kt"),
            "import config.Firefly\nclass App {\n    val configs = listOf(Http, Paths,)\n}\n",
        )
        FireflyProjectInstall.install(root, "kotlin")
        val imported = Files.readString(root.resolve("src/main/kotlin/bootstrap/App.kt"))
        assertEquals(1, imported.split("import config.Firefly").size - 1)
        assertTrue(imported.contains("Firefly,"))
        val plain = root.resolve("plain")
        Files.createDirectories(plain.resolve("src/main/kotlin/bootstrap"))
        Files.writeString(
            plain.resolve("src/main/kotlin/bootstrap/App.kt"),
            "class App {\n    val configs = listOf(Http, Paths,)\n}\n",
        )
        FireflyProjectInstall.install(plain, "kotlin")
        val withoutHash = Files.readString(plain.resolve("src/main/kotlin/bootstrap/App.kt"))
        assertFalse(withoutHash.contains("import config.Firefly"))
        assertTrue(withoutHash.contains("Firefly,"))
    }

    @Test
    fun fireflyInstallSkipsMissingBootstrap() {
        FireflyProjectInstall.install(root, "kotlin")
        assertTrue(Files.exists(root.resolve("src/main/kotlin/config/Firefly.kt")))
        assertFalse(Files.exists(root.resolve("src/main/kotlin/bootstrap/App.kt")))
    }
}
