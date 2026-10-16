package io.konaframework.grind

import io.konaframework.support.packages.GradleDependencyEditor
import io.konaframework.support.packages.PackageCatalog
import io.konaframework.support.packages.PackageInstaller
import java.nio.file.Path

/**
 * `./grind install <package>` — same as `kona install`, scoped to the app root.
 *
 * Package mutation coverage: `PackageInstallerTest`. This class is a GrindIO façade.
 */
@io.konaframework.support.KoverExclude
class InstallCommand(
    private val basePath: Path = Path.of(".").toAbsolutePath().normalize(),
) {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        if (positional.isEmpty()) {
            io.error("Usage: ./grind install <package>")
            io.info("Packages: ${PackageCatalog.aliases().joinToString(", ")}")
            io.info("Or: group:artifact[:version]")
            return 1
        }
        val root =
            options["base"]?.let { Path.of(it).toAbsolutePath().normalize() } ?: basePath
        val version =
            options["version"]
                ?: GradleDependencyEditor.detectKonaVersion(root)
                ?: "0.1.0-SNAPSHOT"
        return try {
            val result = PackageInstaller.install(root, positional[0], version)
            val dep = result.dependency!!
            when {
                dep.alreadyPresent -> io.info("Dependency already present: ${result.resolved.mavenCoordinate}")
                dep.updated -> io.success("Updated dependency: ${result.resolved.mavenCoordinate}")
                else -> io.success("Added dependency: ${result.resolved.mavenCoordinate}")
            }
            val providers = result.providers
            if (providers != null && providers.added.isNotEmpty()) {
                io.success("Registered providers: ${providers.added.joinToString(", ")}")
            }
            io.warn("Refresh Gradle / rebuild so the dependency is on the classpath.")
            0
        } catch (ex: IllegalArgumentException) {
            io.error(ex.message ?: "install failed")
            1
        } catch (ex: IllegalStateException) {
            io.error(ex.message ?: "install failed")
            1
        }
    }
}
