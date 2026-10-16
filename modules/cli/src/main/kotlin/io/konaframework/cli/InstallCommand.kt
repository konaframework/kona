package io.konaframework.cli

import io.konaframework.support.console.ConsoleIO
import io.konaframework.support.packages.GradleDependencyEditor
import io.konaframework.support.packages.PackageCatalog
import io.konaframework.support.packages.PackageInstaller
import java.nio.file.Files
import java.nio.file.Path

/**
 * `kona install <package>` — add a Maven dependency and register service providers.
 *
 * Coverage of package mutation logic lives in `PackageInstallerTest` (`kona-support`).
 * This command is a ConsoleIO façade; Kover excludes it to avoid Kotlin epilogue noise.
 */
@io.konaframework.support.KoverExclude
class InstallCommand {
    fun run(args: List<String>): Int {
        val io = ConsoleIO()
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            io.error("Usage: kona install <package>")
            io.info("Packages: ${PackageCatalog.aliases().joinToString(", ")}")
            io.info("Or: group:artifact[:version]")
            return 1
        }
        return try {
            val projectRoot = resolveProjectRoot(options["base"])
            val version =
                options["version"]
                    ?: GradleDependencyEditor.detectKonaVersion(projectRoot)
                    ?: KonaVersions.load().kona
            val result = PackageInstaller.install(projectRoot, positional[0], version)
            val dep = result.dependency!!
            when {
                dep.alreadyPresent -> io.info("Dependency already present: ${result.resolved.mavenCoordinate}")
                dep.updated -> io.success("Updated dependency: ${result.resolved.mavenCoordinate}")
                else -> io.success("Added dependency: ${result.resolved.mavenCoordinate}")
            }
            io.info("Build file: ${dep.path}")
            val providers = result.providers
            if (providers != null) {
                if (providers.added.isNotEmpty()) {
                    io.success("Registered providers: ${providers.added.joinToString(", ")}")
                    io.info("Manifest: ${providers.path}")
                } else if (providers.alreadyPresent.isNotEmpty()) {
                    io.info("Providers already registered: ${providers.alreadyPresent.joinToString(", ")}")
                }
            } else if (result.resolved.providers.isEmpty()) {
                io.info("No service providers for this package — add bindings manually if needed.")
            }
            io.warn("Refresh Gradle / rebuild so the dependency is on the classpath.")
            0
        } catch (ex: KonaCliException) {
            io.error(ex.message ?: "install failed")
            1
        } catch (ex: IllegalArgumentException) {
            io.error(ex.message ?: "install failed")
            1
        } catch (ex: IllegalStateException) {
            io.error(ex.message ?: "install failed")
            1
        }
    }

    private fun resolveProjectRoot(base: String?): Path {
        if (base != null) {
            val path = Path.of(base).toAbsolutePath().normalize()
            require(Files.isDirectory(path)) { "Base path is not a directory: $path" }
            return path
        }
        return findGradleRoot(Path.of(".").toAbsolutePath().normalize())
    }

    companion object {
        /** Walks [start] and parents for a Gradle build file. */
        @JvmStatic
        fun findGradleRoot(start: Path): Path {
            var dir = start
            while (true) {
                if (Files.isRegularFile(dir.resolve("build.gradle.kts")) ||
                    Files.isRegularFile(dir.resolve("build.gradle"))
                ) {
                    return dir
                }
                val parent = dir.parent ?: break
                if (parent == dir) break
                dir = parent
            }
            throw KonaCliException(
                "No Gradle project found. Run kona install from an app created with kona new, " +
                    "or pass --base=/path/to/app",
            )
        }
    }
}
