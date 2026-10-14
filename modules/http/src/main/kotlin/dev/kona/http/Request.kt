package dev.kona.http

/**
 * HTTP request (Laravel Request shape).
 */
class Request @JvmOverloads constructor(
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

    /** Query + body with natural JSON types (arrays, numbers, booleans, null) for validation. */
    fun data(): Map<String, Any?> {
        if (header("Content-Type").orEmpty().contains("application/json", ignoreCase = true)) {
            return query + JsonSupport.parseObject(body)
        }
        return all()
    }

    /** Laravel `$request->validate(...)`: validated data or [ValidationException]. */
    fun validate(rules: Map<String, List<String>>): Map<String, Any?> = Validator.make(this, rules).validate()

    /**
     * Route parameter bound by the router (Laravel `$request->route('id')`).
     * Returns null when [key] was not in the matched route.
     */
    @JvmOverloads
    fun route(key: String, default: String? = null): String? {
        val value = attributes[key] ?: return default
        return value.toString()
    }

    fun form(): Map<String, String> {
        if (body.isBlank()) {
            return emptyMap()
        }
        val contentType = header("Content-Type").orEmpty()
        if (contentType.contains("application/json", ignoreCase = true)) {
            return JsonSupport.parseFlat(body)
        }
        // Only treat as form when Content-Type is absent (classic POST) or explicitly form-urlencoded.
        // text/plain and other types must not be parsed as key=value pairs.
        if (contentType.isNotBlank() &&
            !contentType.contains("application/x-www-form-urlencoded", ignoreCase = true) &&
            !contentType.contains("multipart/form-data", ignoreCase = true)
        ) {
            return emptyMap()
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

    /** Parsed `Cookie` header name→value map (empty when absent). */
    fun cookies(): Map<String, String> = Cookie.parseHeader(header("Cookie"))

    @JvmOverloads
    fun cookie(name: String, default: String? = null): String? = cookies()[name] ?: default

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8)

    companion object {
        @JvmStatic
        fun get(path: String): Request = Request("GET", path)

        @JvmStatic
        @JvmOverloads
        fun post(path: String, body: String = ""): Request = Request("POST", path, body = body)
    }
}
