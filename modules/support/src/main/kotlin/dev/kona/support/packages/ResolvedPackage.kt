package dev.kona.support.packages

import dev.kona.support.KoverExclude

/**
 * A package resolved from a short alias or `group:artifact[:version]` coordinate.
 */
data class ResolvedPackage(
    val request: String,
    val group: String,
    val artifact: String,
    val version: String,
    val providers: List<String>,
    val alias: String? = null,
) {
    val mavenCoordinate: String get() = "$group:$artifact:$version"

    companion object {
        private val COORD_REGEX = Regex("^([\\w.-]+):([\\w.-]+)(?::([\\w.+-]+))?$")

        /**
         * Resolves [spec] against [PackageCatalog] and [defaultVersion].
         *
         * Accepts:
         * - short alias (`inertia`, `brew`)
         * - `group:artifact` (version = [defaultVersion])
         * - `group:artifact:version`
         */
        @JvmStatic
        @JvmOverloads
        @KoverExclude
        fun parse(
            spec: String,
            defaultVersion: String,
            catalog: Map<String, CatalogEntry> = PackageCatalog.ENTRIES,
        ): ResolvedPackage {
            val trimmed = spec.trim()
            require(trimmed.isNotEmpty()) { "Package name must not be empty" }
            val aliasEntry = catalog[trimmed.lowercase()]
            if (aliasEntry != null) {
                return ResolvedPackage(
                    request = trimmed,
                    group = aliasEntry.group,
                    artifact = aliasEntry.artifact,
                    version = defaultVersion,
                    providers = aliasEntry.providers,
                    alias = trimmed.lowercase(),
                )
            }
            val match =
                COORD_REGEX.matchEntire(trimmed)
                    ?: throw IllegalArgumentException(
                        "Unknown package '$trimmed'. Use a short name " +
                            "(${PackageCatalog.aliases().joinToString(", ")}) " +
                            "or group:artifact[:version]",
                    )
            val group = match.groupValues[1]
            val artifact = match.groupValues[2]
            val version = match.groupValues[3].ifEmpty { defaultVersion }
            val fromCatalog =
                catalog.values.firstOrNull { it.group == group && it.artifact == artifact }
            val alias = catalog.entries.firstOrNull { it.value === fromCatalog }?.key
            return ResolvedPackage(
                request = trimmed,
                group = group,
                artifact = artifact,
                version = version,
                providers = fromCatalog?.providers.orEmpty(),
                alias = alias,
            )
        }
    }
}
