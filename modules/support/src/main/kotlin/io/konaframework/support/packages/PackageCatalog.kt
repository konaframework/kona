package io.konaframework.support.packages

/**
 * First-party short-name map for `kona install` / `grind install`.
 *
 * Generic Maven coordinates (`group:artifact` or `group:artifact:version`) are also accepted
 * without an entry here. Known [providers] are written to the app's
 * `META-INF/kona/providers` so [io.konaframework.core.PackageDiscovery] registers them on boot.
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
            "brew" to CatalogEntry("io.konaframework", "kona-brew", listOf("io.konaframework.brew.BrewServiceProvider")),
            "inertia" to CatalogEntry("io.konaframework", "kona-inertia", listOf("io.konaframework.inertia.InertiaServiceProvider")),
            "crema" to CatalogEntry("io.konaframework", "kona-crema", listOf("io.konaframework.crema.CremaServiceProvider")),
            "filesystem" to CatalogEntry(
                "io.konaframework",
                "kona-filesystem",
                listOf("io.konaframework.filesystem.FilesystemServiceProvider"),
            ),
            "search" to CatalogEntry("io.konaframework", "kona-search", listOf("io.konaframework.search.SearchServiceProvider")),
            "broadcasting" to CatalogEntry(
                "io.konaframework",
                "kona-broadcasting",
                listOf("io.konaframework.broadcasting.BroadcastServiceProvider"),
            ),
            "notifications" to CatalogEntry(
                "io.konaframework",
                "kona-notifications",
                listOf("io.konaframework.notifications.NotificationServiceProvider"),
            ),
            "drip" to CatalogEntry("io.konaframework", "kona-drip", emptyList()),
            "concurrency" to CatalogEntry("io.konaframework", "kona-concurrency", emptyList()),
            "testing" to CatalogEntry("io.konaframework", "kona-testing", emptyList()),
        )

    @JvmStatic
    fun aliases(): List<String> = ENTRIES.keys.toList()

    @JvmStatic
    fun find(alias: String): CatalogEntry? = ENTRIES[alias.lowercase()]
}
