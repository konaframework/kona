package org.konaframework.http

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.function.Consumer

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
    /** UTF-8 view of the body (from [body] string or decoded [bodyBytes]). */
    val body: String = if (body.isNotEmpty()) body else String(this.bodyBytes, Charsets.UTF_8)

    /** Mutable input bag for [merge] / [replace] (Laravel ParameterBag). Null means use natural input. */
    private var bag: LinkedHashMap<String, Any?>? = null

    /** Last successful [validate] payload for [safe]. */
    private var validated: Map<String, Any?>? = null

    fun header(name: String): String? = header(name, null)

    fun header(name: String, default: String?): String? {
        val needle = name.lowercase()
        val match = headers.entries.find { it.key.lowercase() == needle }
        if (match != null) {
            return match.value
        }
        return default
    }

    @JvmOverloads
    fun input(key: String, default: String? = null): String? {
        val value = data()[key] ?: return default
        return value.toString()
    }

    /** Query + form as strings, with [merge]/[replace] overlay applied (values stringified). */
    fun all(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for ((key, value) in data()) {
            if (value != null) {
                result[key] = value.toString()
            }
        }
        return result
    }

    /** Query + body with natural JSON types (arrays, numbers, booleans, null) for validation. */
    fun data(): Map<String, Any?> {
        val overlay = bag
        return if (overlay != null) {
            LinkedHashMap(overlay)
        } else {
            LinkedHashMap(naturalData())
        }
    }

    /** Laravel `$request->validate(...)`: validated data or [ValidationException]. */
    fun validate(rules: Map<String, List<String>>): Map<String, Any?> {
        val result = Validator.make(this, rules).validate()
        validated = result
        return result
    }

    /**
     * Validated payload from the last successful [validate] call (Laravel `$request->safe()->all()` shape).
     * Throws when [validate] has not succeeded on this instance.
     */
    fun safe(): Map<String, Any?> =
        validated ?: throw IllegalStateException("Request has not been validated; call validate(...) first.")

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

    fun file(key: String): UploadedFile? {
        bag?.get(key)?.let { value ->
            return value as? UploadedFile
        }
        return allFiles()[key]
    }

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

    /** Subset of [data] for the given keys (Laravel `only`). */
    fun only(vararg keys: String): Map<String, Any?> {
        val source = data()
        val result = linkedMapOf<String, Any?>()
        for (key in keys) {
            if (source.containsKey(key)) {
                result[key] = source[key]
            }
        }
        return result
    }

    fun only(keys: Collection<String>): Map<String, Any?> = only(*keys.toTypedArray())

    /** [data] without the given keys (Laravel `except`). */
    fun except(vararg keys: String): Map<String, Any?> {
        val skip = keys.toSet()
        return data().filterKeys { it !in skip }
    }

    fun except(keys: Collection<String>): Map<String, Any?> = except(*keys.toTypedArray())

    /** Whether the input bag contains [key] (including null). */
    fun has(key: String): Boolean = data().containsKey(key)

    /** Whether [key] is present and not empty (Laravel `filled`). */
    fun filled(key: String): Boolean {
        if (!has(key)) {
            return false
        }
        return !isEmptyInput(data()[key])
    }

    /** Whether [key] is absent from input. */
    fun missing(key: String): Boolean = !has(key)

    /** Merge values into the input bag (Laravel `merge`). */
    fun merge(input: Map<String, Any?>): Request {
        val target = bag ?: LinkedHashMap(naturalData()).also { bag = it }
        target.putAll(input)
        return this
    }

    /** Replace the entire input bag (Laravel `replace`). */
    fun replace(input: Map<String, Any?>): Request {
        bag = LinkedHashMap(input)
        return this
    }

    /**
     * Boolean input (Laravel `boolean`): true for `1`, `true`, `on`, `yes` (case-insensitive);
     * otherwise [default] when missing/blank, else false.
     *
     * Java callers use [booleanValue] (`boolean` is a reserved word).
     */
    @JvmName("booleanValue")
    @JvmOverloads
    fun boolean(key: String, default: Boolean = false): Boolean {
        if (!has(key)) {
            return default
        }
        val value = data()[key] ?: return default
        if (value is Boolean) {
            return value
        }
        val text = value.toString().trim().lowercase()
        if (text.isEmpty()) {
            return default
        }
        return text in TRUE_VALUES
    }

    /**
     * Parse [key] as a date (ISO-8601 local date / date-time / offset date-time).
     * Returns null when missing or unparseable.
     */
    fun date(key: String): LocalDate? {
        val raw = data()[key] ?: return null
        if (raw is LocalDate) {
            return raw
        }
        val text = raw.toString().trim()
        if (text.isEmpty()) {
            return null
        }
        return tryParseDate(text)
    }

    /** Bearer token from `Authorization: Bearer …`, or null. */
    fun bearerToken(): String? {
        val header = header("Authorization") ?: return null
        val prefix = "Bearer "
        if (!header.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            return null
        }
        val token = header.substring(prefix.length).trim()
        return token.ifEmpty { null }
    }

    /**
     * Client IP: `kona.clientIp` (after TrustProxies), else `kona.remoteAddr`, else null.
     */
    fun ip(): String? {
        val client = getAttribute(TrustProxies.CLIENT_IP)?.toString()
        if (!client.isNullOrBlank()) {
            return client
        }
        val remote = getAttribute(TrustProxies.REMOTE_ADDR)?.toString()
        if (!remote.isNullOrBlank()) {
            return remote
        }
        return null
    }

    fun userAgent(): String? = header("User-Agent")

    fun whenHas(key: String, callback: Consumer<Any?>): Request {
        if (has(key)) {
            callback.accept(data()[key])
        }
        return this
    }

    fun whenFilled(key: String, callback: Consumer<Any?>): Request {
        if (filled(key)) {
            callback.accept(data()[key])
        }
        return this
    }

    fun whenMissing(key: String, callback: Runnable): Request {
        if (missing(key)) {
            callback.run()
        }
        return this
    }

    private fun naturalData(): Map<String, Any?> {
        if (header("Content-Type").orEmpty().contains("application/json", ignoreCase = true)) {
            return LinkedHashMap<String, Any?>().apply {
                putAll(query)
                putAll(JsonSupport.parseObject(textBody()))
            }
        }
        if (isMultipart()) {
            val parsed = multipart()
            return LinkedHashMap<String, Any?>().apply {
                putAll(query)
                putAll(parsed.fields)
                putAll(parsed.files)
            }
        }
        return LinkedHashMap<String, Any?>(query + form())
    }

    private fun textBody(): String = body

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

        private val TRUE_VALUES = setOf("1", "true", "on", "yes")

        @JvmStatic
        fun get(path: String): Request = Request("GET", path)

        @JvmStatic
        @JvmOverloads
        fun post(path: String, body: String = ""): Request = Request("POST", path, body = body)

        @JvmStatic
        fun post(path: String, bodyBytes: ByteArray, headers: Map<String, String>): Request =
            Request("POST", path, headers = headers, bodyBytes = bodyBytes)

        internal fun isEmptyInput(value: Any?): Boolean = when (value) {
            null -> true
            is String -> value.isBlank()
            is UploadedFile -> value.isEmpty()
            is Collection<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            is Array<*> -> value.isEmpty()
            else -> false
        }

        internal fun tryParseDate(text: String): LocalDate? {
            try {
                return LocalDate.parse(text)
            } catch (_: DateTimeParseException) {
                // continue
            }
            try {
                return LocalDateTime.parse(text).toLocalDate()
            } catch (_: DateTimeParseException) {
                // continue
            }
            try {
                return OffsetDateTime.parse(text).toLocalDate()
            } catch (_: DateTimeParseException) {
                return null
            }
        }
    }
}
