package dev.kona.http

/**
 * HTTP request (Laravel Request shape).
 */
class Request(
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val body: String = "",
    val attributes: MutableMap<String, Any?> = mutableMapOf(),
) {
    fun header(name: String): String? = header(name, null)

    fun header(name: String, default: String?): String? {
        val needle = name.lowercase()
        val match = headers.entries.find { it.key.lowercase() == needle }
        if (match != null) {
            return match.value
        }
        return default
    }

    fun input(key: String, default: String? = null): String? {
        query[key]?.let { return it }
        form()[key]?.let { return it }
        return default
    }

    fun all(): Map<String, String> = query + form()

    fun form(): Map<String, String> {
        if (body.isBlank()) {
            return emptyMap()
        }
        val contentType = header("Content-Type").orEmpty()
        if (contentType.contains("application/json", ignoreCase = true)) {
            return JsonSupport.parseFlat(body)
        }
        return body.split('&')
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

    fun setAttribute(key: String, value: Any?): Request {
        attributes[key] = value
        return this
    }

    fun getAttribute(key: String): Any? = attributes[key]

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8)

    companion object {
        @JvmStatic
        fun get(path: String): Request = Request("GET", path)

        @JvmStatic
        fun post(path: String, body: String = ""): Request = Request("POST", path, body = body)
    }
}
