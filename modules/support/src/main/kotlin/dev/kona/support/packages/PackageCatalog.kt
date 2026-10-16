package dev.kona.support.packages

/**
 * First-party short-name map for `kona install` / `grind install`.
 *
 * Generic Maven coordinates (`group:artifact` or `group:artifact:version`) are also accepted
 * without an entry here. Known [providers] are written to the app's
 * `META-INF/kona/providers` so [dev.kona.core.PackageDiscovery] registers them on boot.
 */
data class CatalogEntry(
    val group: String,
    val artifact: String,
    val providers: List<String> = emptyList(),
) {
    val coordinate: String get() = "$group:$artifact"
}

object PackageCatalog {
    /** Short aliases → Maven coordinates + provider FQCNs. */
    @JvmField
    val ENTRIES: Map<String, CatalogEntry> =
        linkedMapOf(
            "brew" to CatalogEntry("dev.kona", "kona-brew", listOf("dev.kona.brew.BrewServiceProvider")),
            "inertia" to CatalogEntry("dev.kona", "kona-inertia", listOf("dev.kona.inertia.InertiaServiceProvider")),
            "crema" to CatalogEntry("dev.kona", "kona-crema", listOf("dev.kona.crema.CremaServiceProvider")),
            "filesystem" to CatalogEntry(
                "dev.kona",
                "kona-filesystem",
                listOf("dev.kona.filesystem.FilesystemServiceProvider"),
            ),
            "search" to CatalogEntry("dev.kona", "kona-search", listOf("dev.kona.search.SearchServiceProvider")),
            "broadcasting" to CatalogEntry(
                "dev.kona",
                "kona-broadcasting",
                listOf("dev.kona.broadcasting.BroadcastServiceProvider"),
            ),
            "notifications" to CatalogEntry(
                "dev.kona",
                "kona-notifications",
                listOf("dev.kona.notifications.NotificationServiceProvider"),
            ),
            "drip" to CatalogEntry("dev.kona", "kona-drip", emptyList()),
            "concurrency" to CatalogEntry("dev.kona", "kona-concurrency", emptyList()),
            "testing" to CatalogEntry("dev.kona", "kona-testing", emptyList()),
        )

    @JvmStatic
    fun aliases(): List<String> = ENTRIES.keys.toList()

    @JvmStatic
    fun find(alias: String): CatalogEntry? = ENTRIES[alias.lowercase()]
}
