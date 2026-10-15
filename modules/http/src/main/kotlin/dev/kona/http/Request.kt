package dev.kona.http

/**
 * HTTP request (Laravel Request shape).
 */
class Request @JvmOverloads constructor(
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    body: String = "",
    val attributes: MutableMap<String, Any?> = mutableMapOf(),
    bodyBytes: ByteArray? = null,
) {
    val bodyBytes: ByteArray = bodyBytes ?: body.toByteArray(Charsets.UTF_8)
    val body: String = body

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
            return query + JsonSupport.parseObject(textBody())
        }
        if (isMultipart()) {
            val parsed = multipart()
            val merged = linkedMapOf<String, Any?>()
            merged.putAll(query)
            merged.putAll(parsed.fields)
            merged.putAll(parsed.files)
            return merged
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
        val contentType = header("Content-Type").orEmpty()
        if (contentType.contains("application/json", ignoreCase = true)) {
            val text = textBody()
            if (text.isBlank()) {
                return emptyMap()
            }
            return JsonSupport.parseFlat(text)
        }
        if (isMultipart()) {
            return multipart().fields
        }
        val text = textBody()
        if (text.isBlank()) {
            return emptyMap()
        }
        // Only treat as form when Content-Type is absent (classic POST) or explicitly form-urlencoded.
        if (contentType.isNotBlank() &&
            !contentType.contains("application/x-www-form-urlencoded", ignoreCase = true)
        ) {
            return emptyMap()
        }
        return text.split('&')
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

    fun file(key: String): UploadedFile? = allFiles()[key]

    fun hasFile(key: String): Boolean {
        val file = file(key) ?: return false
        return file.isValid()
    }

    fun allFiles(): Map<String, UploadedFile> =
        if (isMultipart()) multipart().files else emptyMap()

    fun setAttribute(key: String, value: Any?): Request {
        attributes[key] = value
        return this
    }

    fun getAttribute(key: String): Any? = attributes[key]

    /** Session attached by `StartSession` (attribute `"session"`), if present. */
    fun session(): RequestSession? = getAttribute(SESSION_ATTRIBUTE) as? RequestSession

    /** Flashed old input for [key], or [default] when absent. */
    @JvmOverloads
    fun old(key: String, default: String? = null): String? = session()?.old(key, default) ?: default

    /**
     * Whether the client expects a JSON error envelope (Laravel `$request->expectsJson()`).
     * Form posts and HTML Accept lists return false so validation can redirect back.
     */
    fun expectsJson(): Boolean {
        val accept = header("Accept").orEmpty()
        if (accept.contains("application/json", ignoreCase = true) &&
            !accept.contains("text/html", ignoreCase = true)
        ) {
            return true
        }
        val contentType = header("Content-Type").orEmpty()
        if (contentType.contains("application/json", ignoreCase = true)) {
            return true
        }
        return false
    }

    /** Parsed `Cookie` header name→value map (empty when absent). */
    fun cookies(): Map<String, String> = Cookie.parseHeader(header("Cookie"))

    @JvmOverloads
    fun cookie(name: String, default: String? = null): String? = cookies()[name] ?: default

    private fun textBody(): String = if (body.isNotEmpty()) body else String(bodyBytes, Charsets.UTF_8)

    private fun isMultipart(): Boolean =
        header("Content-Type").orEmpty().contains("multipart/form-data", ignoreCase = true)

    private fun multipart(): MultipartParser.Result {
        val contentType = header("Content-Type") ?: "multipart/form-data"
        return MultipartParser.parse(bodyBytes, contentType)
    }

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8)

    companion object {
        /** Request attribute key used by `StartSession` (`"session"`). */
        const val SESSION_ATTRIBUTE: String = "session"

        @JvmStatic
        fun get(path: String): Request = Request("GET", path)

        @JvmStatic
        @JvmOverloads
        fun post(path: String, body: String = ""): Request = Request("POST", path, body = body)

        @JvmStatic
        fun post(path: String, bodyBytes: ByteArray, headers: Map<String, String>): Request =
            Request("POST", path, headers = headers, bodyBytes = bodyBytes)
    }
}
