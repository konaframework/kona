package org.konaframework.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * URL / route generation (Laravel UrlGenerator shape).
 */
class UrlGenerator @JvmOverloads constructor(
    private val router: Router,
    private var root: String = "",
    private var key: String = "kona-app-key",
    private val clock: Clock = Clock.systemUTC(),
) {
    fun setRoot(root: String): UrlGenerator {
        this.root = root.trimEnd('/')
        return this
    }

    fun setKey(key: String): UrlGenerator {
        this.key = key
        return this
    }

    fun to(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        val normalized = if (path.startsWith("/")) path else "/$path"
        return if (root.isEmpty()) normalized else root + normalized
    }

    @JvmOverloads
    fun route(name: String, params: Map<String, String> = emptyMap()): String =
        to(router.route(name, params))

    @JvmOverloads
    fun signedRoute(
        name: String,
        params: Map<String, String> = emptyMap(),
        expirationSeconds: Long? = null,
    ): String = signed(router.route(name, params), expirationSeconds)

    @JvmOverloads
    fun signed(path: String, expirationSeconds: Long? = null): String {
        val (basePath, existing) = splitPathQuery(path)
        val query = LinkedHashMap(existing)
        if (expirationSeconds != null) {
            query["expires"] = (Instant.now(clock).epochSecond + expirationSeconds).toString()
        }
        val payload = encodePathQuery(basePath, query)
        val signature = sign(payload)
        query["signature"] = signature
        return to(encodePathQuery(basePath, query))
    }

    fun hasValidSignature(request: Request): Boolean {
        val signature = request.query["signature"] ?: return false
        val expires = request.query["expires"]?.toLongOrNull()
        if (expires != null && Instant.now(clock).epochSecond > expires) {
            return false
        }
        val others = request.query.filterKeys { it != "signature" }.toSortedMap()
        val rebuilt = encodePathQuery(request.path, others)
        return sign(rebuilt) == signature
    }

    private fun splitPathQuery(path: String): Pair<String, Map<String, String>> {
        val idx = path.indexOf('?')
        if (idx < 0) {
            return path to emptyMap()
        }
        val base = path.substring(0, idx)
        val query = path.substring(idx + 1)
            .split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) {
                    pair to ""
                } else {
                    pair.substring(0, eq) to pair.substring(eq + 1)
                }
            }
        return base to query
    }

    private fun encodePathQuery(path: String, query: Map<String, String>): String {
        if (query.isEmpty()) {
            return path
        }
        val sorted = query.toSortedMap()
        val encoded = sorted.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
        return "$path?$encoded"
    }

    private fun sign(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
