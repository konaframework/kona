package org.konaframework.support.packages

/**
 * First-party short-name map for `./grind install` / `kona grind install`.
 *
 * Generic Maven coordinates (`group:artifact` or `group:artifact:version`) are also accepted
 * without an entry here. Known [providers] are written to the app's
 * `META-INF/kona/providers` so [org.konaframework.core.PackageDiscovery] registers them on boot.
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
            "wire" to CatalogEntry("org.konaframework", "kona-wire", listOf("org.konaframework.wire.WireServiceProvider")),
            "brew" to CatalogEntry("org.konaframework", "kona-wire", listOf("org.konaframework.wire.WireServiceProvider")),
            "inertia" to CatalogEntry("org.konaframework", "kona-inertia", listOf("org.konaframework.inertia.InertiaServiceProvider")),
            "crema" to CatalogEntry("org.konaframework", "kona-crema", listOf("org.konaframework.crema.CremaServiceProvider")),
            "filesystem" to CatalogEntry(
                "org.konaframework",
                "kona-filesystem",
                listOf("org.konaframework.filesystem.FilesystemServiceProvider"),
            ),
            "search" to CatalogEntry("org.konaframework", "kona-search", listOf("org.konaframework.search.SearchServiceProvider")),
            "broadcasting" to CatalogEntry(
                "org.konaframework",
                "kona-broadcasting",
                listOf("org.konaframework.broadcasting.BroadcastServiceProvider"),
            ),
            "notifications" to CatalogEntry(
                "org.konaframework",
                "kona-notifications",
                listOf("org.konaframework.notifications.NotificationServiceProvider"),
            ),
            "drip" to CatalogEntry("org.konaframework", "kona-drip", emptyList()),
            "concurrency" to CatalogEntry("org.konaframework", "kona-concurrency", emptyList()),
            "testing" to CatalogEntry("org.konaframework", "kona-testing", emptyList()),
            "firefly" to CatalogEntry(
                "org.konaframework",
                "kona-firefly",
                listOf("org.konaframework.firefly.FireflyServiceProvider"),
            ),
        )

    @JvmStatic
    fun aliases(): List<String> = ENTRIES.keys.toList()

    @JvmStatic
    fun find(alias: String): CatalogEntry? = ENTRIES[alias.lowercase()]
}
