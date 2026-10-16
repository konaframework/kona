package io.konaframework.support.packages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PackageInstallerTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun installAliasAddsDependencyAndProviders() {
        writeBuildKts()
        val result = PackageInstaller.install(root, "inertia", "1.2.3")
        assertEquals("io.konaframework:kona-inertia:1.2.3", result.resolved.mavenCoordinate)
        assertTrue(result.dependency!!.added)
        val build = Files.readString(root.resolve("build.gradle.kts"))
        assertTrue(build.contains("""implementation("io.konaframework:kona-inertia:1.2.3")"""))
        val manifest = Files.readString(root.resolve(ProviderManifestEditor.RELATIVE))
        assertTrue(manifest.contains("io.konaframework.inertia.InertiaServiceProvider"))
        assertEquals(listOf("io.konaframework.inertia.InertiaServiceProvider"), result.providers!!.added)
    }

    @Test
    fun installIsIdempotent() {
        writeBuildKts()
        PackageInstaller.install(root, "drip", "9.9.9")
        val second = PackageInstaller.install(root, "drip", "9.9.9")
        assertTrue(second.dependency!!.alreadyPresent)
        assertEquals(null, second.providers) // drip has no providers
    }

    @Test
    fun installUpdatesVersion() {
        writeBuildKts("""implementation("io.konaframework:kona-drip:1.0.0")""")
        val result = PackageInstaller.install(root, "drip", "2.0.0")
        assertTrue(result.dependency!!.updated)
        assertTrue(Files.readString(root.resolve("build.gradle.kts")).contains("kona-drip:2.0.0"))
    }

    @Test
    fun installCoordinateWithExplicitVersion() {
        writeBuildKts()
        val result = PackageInstaller.install(root, "com.example:widget:0.4.0", "ignored")
        assertEquals("com.example:widget:0.4.0", result.resolved.mavenCoordinate)
        assertTrue(result.dependency!!.added)
    }

    @Test
    fun installCoordinateWithoutVersionUsesDefault() {
        writeBuildKts()
        val result = PackageInstaller.install(root, "com.example:widget", "3.1.0")
        assertEquals("com.example:widget:3.1.0", result.resolved.mavenCoordinate)
    }

    @Test
    fun unknownAliasThrows() {
        writeBuildKts()
        assertThrows(IllegalArgumentException::class.java) {
            PackageInstaller.install(root, "nope", "1.0.0")
        }
    }

    @Test
    fun emptySpecThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            ResolvedPackage.parse("  ", "1.0.0")
        }
    }

    @Test
    fun missingBuildFileThrows() {
        assertThrows(IllegalStateException::class.java) {
            PackageInstaller.install(root, "drip", "1.0.0")
        }
    }

    @Test
    fun groovyBuildFileSupported() {
        Files.writeString(
            root.resolve("build.gradle"),
            """
            plugins { id 'java' }
            dependencies {
            }
            """.trimIndent(),
        )
        val result = PackageInstaller.install(root, "testing", "1.0.0")
        assertTrue(result.dependency!!.added)
        assertTrue(Files.readString(root.resolve("build.gradle")).contains("kona-testing:1.0.0"))
    }

    @Test
    fun providersAlreadyPresentReported() {
        writeBuildKts()
        PackageInstaller.install(root, "crema", "1.0.0")
        val again = PackageInstaller.install(root, "crema", "1.0.0")
        assertTrue(again.dependency!!.alreadyPresent)
        assertTrue(again.providers!!.added.isEmpty())
        assertFalse(again.providers!!.alreadyPresent.isEmpty())
    }

    @Test
    fun catalogAliasesNonEmpty() {
        assertTrue(PackageCatalog.aliases().contains("brew"))
        assertEquals("io.konaframework", PackageCatalog.find("inertia")!!.group)
        assertEquals(null, PackageCatalog.find("missing"))
    }

    @Test
    fun detectKonaVersionFromBuildFile() {
        writeBuildKts("""implementation("io.konaframework:kona-http:4.5.6")""")
        assertEquals("4.5.6", GradleDependencyEditor.detectKonaVersion(root))
        assertEquals(null, GradleDependencyEditor.detectKonaVersion(Files.createTempDirectory("empty")))
    }

    @Test
    fun coordinateMatchingCatalogCarriesProviders() {
        val resolved = ResolvedPackage.parse("io.konaframework:kona-brew", "1.0.0")
        assertEquals(listOf("io.konaframework.brew.BrewServiceProvider"), resolved.providers)
        assertEquals("brew", resolved.alias)
        assertEquals("io.konaframework:kona-brew", PackageCatalog.find("brew")!!.coordinate)
    }

    @Test
    fun groovyUpdatesExistingDependencyVersion() {
        Files.writeString(
            root.resolve("build.gradle"),
            """
            dependencies {
                implementation 'io.konaframework:kona-drip:1.0.0'
            }
            """.trimIndent(),
        )
        val result = PackageInstaller.install(root, "drip", "2.0.0")
        assertTrue(result.dependency!!.updated)
        assertTrue(Files.readString(root.resolve("build.gradle")).contains("kona-drip:2.0.0"))
    }

    @Test
    fun groovyAlreadyPresentSameVersion() {
        Files.writeString(
            root.resolve("build.gradle"),
            """
            dependencies {
                implementation 'io.konaframework:kona-drip:2.0.0'
            }
            """.trimIndent(),
        )
        val result = PackageInstaller.install(root, "drip", "2.0.0")
        assertTrue(result.dependency!!.alreadyPresent)
    }

    @Test
    fun missingDependenciesBlockErrors() {
        Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n")
        assertThrows(IllegalStateException::class.java) {
            PackageInstaller.install(root, "drip", "1.0.0")
        }
    }

    @Test
    fun insertHandlesNestedBracesAndMissingNewline() {
        Files.writeString(
            root.resolve("build.gradle.kts"),
            "dependencies { configurations { } }",
        )
        val result = PackageInstaller.install(root, "drip", "1.0.0")
        assertTrue(result.dependency!!.added)
        assertTrue(Files.readString(root.resolve("build.gradle.kts")).contains("kona-drip:1.0.0"))
    }

    @Test
    fun providerManifestCreatesFileAndSkipsDuplicates() {
        writeBuildKts()
        val first = ProviderManifestEditor.ensureProviders(
            root,
            listOf("dev.example.One", "dev.example.Two"),
        )!!
        assertEquals(listOf("dev.example.One", "dev.example.Two"), first.added)
        val second = ProviderManifestEditor.ensureProviders(
            root,
            listOf("dev.example.One", "dev.example.Three"),
        )!!
        assertEquals(listOf("dev.example.Three"), second.added)
        assertEquals(listOf("dev.example.One"), second.alreadyPresent)
        val third = ProviderManifestEditor.ensureProviders(
            root,
            listOf("dev.example.One"),
        )!!
        assertTrue(third.added.isEmpty())
        assertEquals(listOf("dev.example.One"), third.alreadyPresent)
        assertEquals(null, ProviderManifestEditor.ensureProviders(root, emptyList()))
    }

    @Test
    fun insertReturnsNullWithoutDependenciesKeyword() {
        assertEquals(
            null,
            GradleDependencyEditor.insertIntoDependenciesBlock("nope", "    implementation(\"x\")"),
        )
    }

    @Test
    fun insertReturnsNullWhenDependenciesBlockUnclosed() {
        assertEquals(
            null,
            GradleDependencyEditor.insertIntoDependenciesBlock(
                "dependencies { implementation(\"x:y:1\")",
                "    implementation(\"a:b:1\")",
            ),
        )
    }

    @Test
    fun detectVersionFromGroovyBuildFile() {
        Files.writeString(
            root.resolve("build.gradle"),
            """implementation 'io.konaframework:kona-http:7.7.7'""",
        )
        assertEquals("7.7.7", GradleDependencyEditor.detectKonaVersion(root))
    }

    @Test
    fun detectVersionSkipsBuildFileWithoutKonaCoordinate() {
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """dependencies { implementation("com.example:lib:1.0.0") }""",
        )
        assertEquals(null, GradleDependencyEditor.detectKonaVersion(root))
    }

    @Test
    fun editKotlinDslImplementationWithoutParensStyle() {
        // Groovy-style single-quote line inside .gradle already covered; ensure kts update path
        writeBuildKts("""implementation("io.konaframework:kona-crema:0.1.0")""")
        val result = PackageInstaller.install(root, "crema", "0.2.0")
        assertTrue(result.dependency!!.updated)
    }

    private fun writeBuildKts(extraDep: String = "") {
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
