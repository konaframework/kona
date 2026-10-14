package dev.kona.inertia

/**
 * Inertia page object returned as JSON for `X-Inertia` requests
 * and embedded as `data-page` on the first HTML visit.
 */
data class InertiaPage(
    val component: String,
    val props: Map<String, Any?>,
    val url: String,
    val version: String?,
) {
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "component" to component,
            "props" to props,
            "url" to url,
            "version" to version,
        )
}
