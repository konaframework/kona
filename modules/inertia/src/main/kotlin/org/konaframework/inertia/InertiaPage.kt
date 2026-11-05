package org.konaframework.inertia

/**
 * Inertia page object returned as JSON for `X-Inertia` requests
 * and embedded as `data-page` on the first HTML visit.
 */
data class InertiaPage(
    val component: String,
    val props: Map<String, Any?>,
    val url: String,
    val version: String?,
    val encryptHistory: Boolean = false,
    val clearHistory: Boolean = false,
    val deferredProps: Map<String, List<String>>? = null,
    val mergeProps: List<String>? = null,
    val prependProps: List<String>? = null,
    val deepMergeProps: List<String>? = null,
    val matchPropsOn: List<String>? = null,
) {
    fun toMap(): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "component" to component,
            "props" to props,
            "url" to url,
            "version" to version,
            "encryptHistory" to encryptHistory,
            "clearHistory" to clearHistory,
        )
        if (!deferredProps.isNullOrEmpty()) {
            map["deferredProps"] = deferredProps
        }
        if (!mergeProps.isNullOrEmpty()) {
            map["mergeProps"] = mergeProps
        }
        if (!prependProps.isNullOrEmpty()) {
            map["prependProps"] = prependProps
        }
        if (!deepMergeProps.isNullOrEmpty()) {
            map["deepMergeProps"] = deepMergeProps
        }
        if (!matchPropsOn.isNullOrEmpty()) {
            map["matchPropsOn"] = matchPropsOn
        }
        return map
    }
}
