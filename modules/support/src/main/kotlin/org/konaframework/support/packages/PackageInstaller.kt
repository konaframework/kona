package org.konaframework.support.packages

import org.konaframework.support.KoverExclude
import java.nio.file.Path

/**
 * Installs a package into an app: Maven `implementation` dependency + optional
 * `META-INF/kona/providers` entries for auto-registration.
 */
object PackageInstaller {
    data class Result(
        val resolved: ResolvedPackage,
        val dependency: GradleDependencyEditor.Result?,
        val providers: ProviderManifestEditor.Result?,
    )

    /**
     * @param projectRoot app root containing `build.gradle(.kts)`
     * @param spec short alias or `group:artifact[:version]`
     * @param defaultVersion used when [spec] omits a version (typically the app's Kona version)
     */
    @JvmStatic
    @JvmOverloads
    @KoverExclude
    fun install(
        projectRoot: Path,
        spec: String,
        defaultVersion: String,
        catalog: Map<String, CatalogEntry> = PackageCatalog.ENTRIES,
    ): Result =
        ResolvedPackage.parse(spec, defaultVersion, catalog).let { resolved ->
            val dependency =
                GradleDependencyEditor.ensureDependency(
                    projectRoot,
                    resolved.group,
                    resolved.artifact,
                    resolved.version,
                )
                    ?: error("No build.gradle or build.gradle.kts under $projectRoot")
            val providers = ProviderManifestEditor.ensureProviders(projectRoot, resolved.providers)
            Result(resolved, dependency, providers)
        }
}
