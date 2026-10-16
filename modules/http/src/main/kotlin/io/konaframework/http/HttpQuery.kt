package io.konaframework.http

/**
 * Percent-decoded query-string parsing shared by HTTP adapters.
 */
internal object HttpQuery {
    private val EMPTY: Map<String, String> = emptyMap()

    fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) {
            return EMPTY
        }
        return raw.split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) {
                    decode(pair) to ""
                } else {
                    decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
                }
            }
    }

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8)
}
