package dev.kona.brew

/**
 * Update strategy helper. Full fragment replace is the supported morph equivalent:
 * the client swaps the component root `outerHTML` with the new HTML from the server.
 */
object BrewMorph {
    /**
     * Returns [nextHtml] as the replacement for [previousHtml].
     * Keeping this API lets callers (and tests) treat morph as an explicit step.
     */
    @JvmStatic
    fun replace(previousHtml: String, nextHtml: String): String {
        if (nextHtml.isBlank()) {
            return previousHtml
        }
        return nextHtml
    }
}
